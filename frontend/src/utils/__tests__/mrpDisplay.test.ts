import { describe, expect, it } from 'vitest'
import { batchStatusDisplay, businessLabel, issueTypeLabel } from '../mrpDisplay'

describe('MRP 业务文案', () => {
  it('所有批次状态都显示为中文业务含义', () => {
    const statuses = [
      'DRAFT', 'CHECKING', 'ANALYZING', 'WAITING_DECISION', 'REANALYZING',
      'READY_TO_CALCULATE', 'CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION',
      'READY', 'PUBLISHED', 'STALE', 'FAILED', 'CANCELLED'
    ]

    statuses.forEach(status => {
      expect(batchStatusDisplay(status).label).not.toBe(status)
      expect(batchStatusDisplay(status).guidance.length).toBeGreaterThan(0)
    })
  })

  it('决策问题和通用状态提供中文标签', () => {
    expect(issueTypeLabel('INVENTORY_MISSING')).toBe('缺少库存记录')
    expect(issueTypeLabel('MATERIAL_MISSING')).toBe('物料管理中缺少物料主数据')
    expect(issueTypeLabel('BOM_ORG_AMBIGUOUS')).toBe('BOM 库存组织不明确')
    expect(businessLabel('OPEN')).toBe('待处理')
    expect(businessLabel('EXECUTED')).toBe('已执行')
  })
})
