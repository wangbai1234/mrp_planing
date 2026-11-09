import { describe, expect, it } from 'vitest'
import type { AuditLogRecord } from '../../api/audit'
import { auditAction, auditChanges, auditResource, auditSummary } from '../auditPresentation'

function log(before: unknown, after: unknown, action = 'UPDATE', resource_type = 'stock_policy'): AuditLogRecord {
  return { id: 1, action, resource_type, resource_id: 'WH|M001', operator_id: 1,
    operator_name: '张三', created_at: '2026-11-09 10:00:00', before_value: before, after_value: after }
}

describe('业务审计记录', () => {
  it('只展示变化字段，并翻译业务字段与状态', () => {
    const changes = auditChanges(log(
      JSON.stringify({ materialCode: 'M001', safetyStockQty: 100, supplyMode: 'PURCHASED', updatedAt: 'old' }),
      JSON.stringify({ materialCode: 'M001', safetyStockQty: 120, supplyMode: 'SELF_MADE', updatedAt: 'new' })
    ))
    expect(changes.map(change => [change.label, change.before, change.after])).toEqual([
      ['安全库存', '100', '120'], ['供应方式', '外购', '自制']
    ])
  })

  it('按周编码比较业务周，不受数组顺序影响', () => {
    const changes = auditChanges(log(
      [{ weekCode: 'W1', startDate: '2026-11-01' }, { weekCode: 'W2', startDate: '2026-11-08' }],
      [{ weekCode: 'W2', startDate: '2026-11-09' }, { weekCode: 'W1', startDate: '2026-11-01' }],
      'UPDATE', 'business_week'
    ))
    expect(changes.map(change => [change.label, change.before, change.after])).toEqual([
      ['W2 · 开始日期', '2026-11-08', '2026-11-09']
    ])
  })

  it('导入记录显示文件和汇总，并比较嵌套的单条数据', () => {
    const item = log({ safetyStockQty: 100 }, { fileName: '库存.xlsx', data: { safetyStockQty: 120 } }, 'IMPORT')
    expect(auditSummary(item)).toContain('导入了库存控制策略 · 库存.xlsx')
    expect(auditChanges(item).map(change => [change.label, change.before, change.after])).toEqual([
      ['安全库存', '100', '120']
    ])
    expect(auditSummary(log(null, { fileName: '库存.xlsx', inserted: 2, updated: 3 }, 'IMPORT')))
      .toContain('新增 2 条 · 更新 3 条')
  })

  it('过滤审计元数据和敏感字段，保留原始值记录', () => {
    expect(auditChanges(log({ passwordHash: 'old', enabled: false }, { passwordHash: 'new', enabled: true })))
      .toEqual([{ key: 'enabled', label: '启用', before: '停用', after: '启用', changed: true }])
    expect(auditChanges(log('800', '1000'))).toEqual([
      { key: 'value', label: '内容', before: '800', after: '1000', changed: true }
    ])
  })

  it('经营计划编辑记录按真实 line 结构比较月份，并将操作显示为中文', () => {
    const item = log(
      { materialId: 'M001', monthsJson: '{"2027-01-01":"100"}', status: 'DRAFT' },
      { line: { materialId: 'M001', monthsJson: '{"2027-01-01":"120"}', status: 'DRAFT' }, reason: '月计划调整' },
      'EDIT', 'forecast_line'
    )
    expect(auditSummary(item)).toBe('编辑了经营计划明细')
    expect(auditChanges(item).map(change => [change.label, change.before, change.after])).toEqual([
      ['2027年1月计划量', '100', '120'], ['调整原因', '—', '月计划调整']
    ])
    expect(auditAction('TRANSFER')).toBe('调整排产')
    expect(auditAction('UNRECOGNIZED')).toBe('其他操作')
    expect(auditResource('UNRECOGNIZED')).toBe('业务数据')
  })

  it('排产日志中的业务字段使用明确的中文名称', () => {
    const fields = {
      batchId: 1, pathGroupKey: 'A', scheduleDate: '2026-11-09', terminalType: 'LEAF',
      manualQuantity: 10, rootMaterialCode: 'M001', terminalMaterialCode: 'P001', businessLine: 'A线'
    }
    const changes = auditChanges(log(null, fields, 'TRANSFER', 'mrp_daily_plan_override'))
    expect(changes.map(change => change.label)).toEqual([
      '排产批次', '路径分组', '排产日期', '终点类型', '人工排产量',
      '来源整机料号', '终点物料料号', '业务线'
    ])
  })

  it('翻译产能口径和终点类型，隐藏并发校验字段', () => {
    const changes = auditChanges(log(null, {
      capacityBasis: 'MACHINE_OUTPUT', terminalType: 'RAW_MATERIAL',
      status: 'OVERLOAD', expectedRunId: 9, rowKey: 'opaque-key',
      sourceDate: '2026-11-08', targetDate: '2026-11-09'
    }, 'TRANSFER', 'mrp_daily_plan_override'))
    expect(changes.map(change => [change.label, change.after])).toEqual([
      ['产能核算口径', '整机产出'], ['终点类型', '原材料'],
      ['状态', '超产能'],
      ['原排产日期', '2026-11-08'], ['目标排产日期', '2026-11-09']
    ])
  })
})
