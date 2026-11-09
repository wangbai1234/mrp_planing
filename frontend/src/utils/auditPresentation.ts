import type { AuditLogRecord } from '../api/audit'

export interface AuditChange {
  key: string
  label: string
  before: string
  after: string
  changed: boolean
}

const labels: Record<string, string> = {
  factoryCode: '工厂编码', factoryName: '工厂名称', warehouseCode: '仓库编码', warehouseName: '仓库名称',
  warehouseManager: '仓库管理员', supplierName: '对应供应商', warehouseAttribute: '仓库属性',
  goodWarehouse: '良品仓', qualityConfigured: '品质已确认', priorityNo: '优先级', enabled: '启用',
  materialCode: '物料料号', materialId: '物料料号', materialName: '物料名称',
  safetyStockQty: '安全库存', warningStockQty: '预警库存', supplyMode: '供应方式',
  explosionPolicy: '下钻策略', businessMonth: '业务月份', weekCode: '业务周',
  startDate: '开始日期', endDate: '结束日期', calendarDate: '日期', productionDay: '生产日',
  dailyCapacity: '整机日产能', capacityUnit: '单位', reason: '调整原因',
  planMonth: '计划月份', quantity: '数量', qty: '数量', total: '合计',
  prioritySeq: '计划优先级', formType: '形态', mold: '模具', status: '状态',
  username: '用户名', displayName: '姓名', department: '部门', email: '邮箱', phone: '电话',
  isActive: '启用', roleName: '角色名称', code: '编码', name: '名称',
  fileName: '导入文件', inserted: '新增条数', updated: '更新条数', versionNo: '版本号',
  data: '数据', months: '月份数量', value: '内容', project: '项目型号', platform: '平台',
  maker: '编制人', baseVersionId: '基础版本', allocationWeight: '分配权重',
  allocatedQty: '分配数量', adjustmentReason: '调整原因', manualOverride: '人工覆盖',
  confirmedCount: '确认条数', resolvedCount: '已处理条数', scope: '范围',
  source: '原计划', target: '目标计划', from: '原计划', to: '目标计划',
  request: '调整请求', loads: '产能负载', rule: '规则',
  batchId: '排产批次', pathGroupKey: '路径分组', scheduleDate: '排产日期',
  terminalType: '终点类型', manualQuantity: '人工排产量',
  rootMaterialCode: '来源整机料号', terminalMaterialCode: '终点物料料号',
  businessLine: '业务线',
  capacityBasis: '产能核算口径', configured: '产能已配置', date: '日期',
  description: '说明', excess: '超出量', load: '已排负荷', message: '提示',
  partiallyUnassessed: '部分需求未评估', sortNo: '排序',
  sourceDate: '原排产日期', targetDate: '目标排产日期', userCount: '关联用户数',
}

const resources: Record<string, string> = {
  mrp_factory: '工厂', factory_warehouse_mapping: '仓库', stock_policy: '库存控制策略',
  material_policy: '物料策略', business_week: '业务周期间', mrp_production_calendar: '生产日历',
  forecast_line: '经营计划明细', forecast_draft: '经营计划草稿', forecast_version: '经营计划版本',
  mrp_daily_plan_override: '日排产', mrp_calc_batch: '排产批次',
  mrp_material_policy_notice: '物料策略确认', mrp_stock_risk: '库存风险',
  mrp_inventory_allocation: '库存分配', mrp_workbench_review: '工作台复核',
  USER: '用户', ROLE: '角色', PERMISSION: '权限',
  FORECAST: '经营计划', INVENTORY: '库存', PLAN: '排产',
}

const actions: Record<string, string> = {
  CREATE: '新增', UPDATE: '编辑', EDIT: '编辑', DELETE: '删除', IMPORT: '导入', ENABLE: '启用',
  DISABLE: '停用', ASSIGN_ROLE: '分配角色', UPDATE_PERMISSIONS: '更新权限',
  RESET_PASSWORD: '重置密码', COPY_ROLE: '复制角色', LOGIN: '登录',
  TRANSFER: '调整排产', CAPACITY_ACKNOWLEDGE: '确认产能',
  CONFIRM_DEFAULT_EXPLOSION: '确认下钻策略',
  APPLY_ZERO_STOCK_THRESHOLDS: '应用库存阈值',
  CREATE_DRAFT: '创建草稿', DISCARD: '放弃草稿', PUBLISH: '发布',
  CONFIRM_SCHEDULE: '确认排产', OVERRIDE_INVENTORY_ALLOCATION: '调整库存分配',
  RESET_INVENTORY_ALLOCATION: '恢复自动分配',
}

const actionSummaries: Record<string, string> = {
  ASSIGN_ROLE: '为用户分配了角色', UPDATE_PERMISSIONS: '更新了角色权限',
  RESET_PASSWORD: '重置了用户密码', COPY_ROLE: '复制了角色', LOGIN: '登录了系统',
  TRANSFER: '调整了日排产', CAPACITY_ACKNOWLEDGE: '确认了产能',
  CONFIRM_DEFAULT_EXPLOSION: '确认了下钻策略',
  APPLY_ZERO_STOCK_THRESHOLDS: '应用了库存阈值',
  CREATE_DRAFT: '创建了经营计划草稿', DISCARD: '放弃了经营计划草稿',
  PUBLISH: '发布了经营计划版本', CONFIRM_SCHEDULE: '确认了排产',
  OVERRIDE_INVENTORY_ALLOCATION: '调整了库存分配',
  RESET_INVENTORY_ALLOCATION: '恢复了自动分配',
}

const enumValues: Record<string, string> = {
  SELF_MADE: '自制', PURCHASED: '外购', OUTSOURCED: '委外',
  EXPLODE: '继续下钻', STOP: '停止下钻', ACTIVE: '启用', INACTIVE: '停用',
  DRAFT: '草稿', PUBLISHED: '已发布', UNMAINTAINED: '待维护',
  MACHINE_OUTPUT: '整机产出', TERMINAL: '末级需求',
  RAW_MATERIAL: '原材料', ASSEMBLY: '组件', NORMAL: '正常', OVERLOAD: '超产能',
}

const ignored = new Set([
  'id', 'createdAt', 'updatedAt', 'createdBy', 'updatedBy', 'createdByName', 'updatedByName',
  'revision', 'passwordHash', 'password', 'token', 'traceId', 'ipAddress',
  'draftId', 'isDeleted', 'operatorId', 'runId', 'taskId', 'fingerprint',
  'rowKey', 'expectedRunId', 'expectedSourceQuantity', 'expectedTargetQuantity',
  'expectedSourceRevision', 'expectedTargetRevision',
])

function parse(value: unknown): unknown {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) as unknown } catch { return value }
}

function record(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : null
}

function flatten(value: unknown, prefix = '', result: Map<string, unknown> = new Map()): Map<string, unknown> {
  if (prefix.endsWith('monthsJson') && typeof value === 'string') {
    const months = record(parse(value))
    if (months) return flatten(months, prefix, result)
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      const row = record(item)
      const key = row && typeof row.weekCode === 'string' ? row.weekCode : `第${index + 1}项`
      flatten(item, prefix ? `${prefix}.${key}` : key, result)
    })
  } else {
    const object = record(value)
    if (object) {
      for (const [key, item] of Object.entries(object)) {
        if (ignored.has(key) || (key === 'weekCode' && prefix.startsWith('W'))) continue
        flatten(item, prefix ? `${prefix}.${key}` : key, result)
      }
  } else result.set(prefix || 'value', value)
  }
  return result
}

function fieldLabel(path: string): string {
  return path.split('.').filter(part => !['data', 'line', 'months', 'monthsJson'].includes(part))
    .map(part => {
      const month = /^(\d{4})-(\d{2})-\d{2}$/.exec(part)
      if (month) return `${month[1]}年${Number(month[2])}月计划量`
      if (/^W\d+$/i.test(part)) return part.toUpperCase()
      return labels[part] || (/^[\x00-\x7F]+$/.test(part) ? '其他字段' : part)
    }).join(' · ')
}

function display(value: unknown, key: string): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') {
    if (key.endsWith('productionDay')) return value ? '生产' : '停产'
    if (key.endsWith('enabled') || key.endsWith('isActive')) return value ? '启用' : '停用'
    return value ? '是' : '否'
  }
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string') {
    if (key.endsWith('productionDay')) return value === 'true' ? '生产' : value === 'false' ? '停产' : value
    return enumValues[value] || value
  }
  return '—'
}

export function auditChanges(log: AuditLogRecord): AuditChange[] {
  const before = flatten(parse(log.before_value))
  const afterRaw = parse(log.after_value)
  const afterObject = record(afterRaw)
  const afterValue = log.action === 'IMPORT' && afterObject && 'data' in afterObject ? afterObject.data
    : log.action === 'EDIT' && afterObject && record(afterObject.line)
      ? { ...record(afterObject.line), reason: afterObject.reason }
      : afterRaw
  const after = flatten(afterValue)
  const keys = [...new Set([...before.keys(), ...after.keys()])]
  return keys.filter(key => !['fileName', 'inserted', 'updated'].includes(key)).map(key => {
    const oldValue = display(before.get(key), key)
    const newValue = display(after.get(key), key)
    return { key, label: fieldLabel(key), before: oldValue, after: newValue, changed: oldValue !== newValue }
  }).filter(change => change.changed)
}

export function auditSummary(log: AuditLogRecord): string {
  const after = record(parse(log.after_value))
  if (log.action === 'IMPORT' && after) {
    const parts = [after.fileName, typeof after.inserted === 'number' ? `新增 ${after.inserted} 条` : null,
      typeof after.updated === 'number' ? `更新 ${after.updated} 条` : null].filter(Boolean)
    if (parts.length) return `导入了${resources[log.resource_type] || '记录'} · ${parts.join(' · ')}`
  }
  if (actionSummaries[log.action]) return actionSummaries[log.action]
  const action = actions[log.action]
  return action ? `${action}了${resources[log.resource_type] || '业务数据'}` : '进行了业务操作'
}

export function auditAction(action: string): string { return actions[action] || '其他操作' }
export function auditResource(type: string): string { return resources[type] || '业务数据' }
export function auditTime(value: string): string { return value ? value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19) : '—' }
