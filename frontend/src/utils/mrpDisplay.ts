export type TagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

export interface BatchStatusDisplay {
  label: string
  type: TagType
  step: number
  guidance: string
}

const BATCH_STATUS_DISPLAY: Record<string, BatchStatusDisplay> = {
  DRAFT: { label: '待预检', type: 'info', step: 0, guidance: '已锁定输入版本，请先执行预检，系统将检查 BOM、库存、分类角色和物料策略。' },
  CHECKING: { label: '预检中', type: 'warning', step: 1, guidance: '系统正在检查计算输入，请稍后刷新批次状态。' },
  ANALYZING: { label: '分析中', type: 'warning', step: 1, guidance: '系统正在展开 BOM 并识别需要业务确认的问题。' },
  WAITING_DECISION: { label: '待业务处理', type: 'warning', step: 2, guidance: '存在未处理的决策问题或待分配数量，请先在“待办决策”中完成处理。' },
  REANALYZING: { label: '重新分析中', type: 'warning', step: 1, guidance: '业务决定已保存，系统正在重新分析受影响的数据。' },
  READY_TO_CALCULATE: { label: '待计算', type: 'primary', step: 3, guidance: '预检和业务确认已完成，可以开始正式计算。' },
  CALCULATING: { label: '计算中', type: 'warning', step: 3, guidance: 'MRP 任务已提交，系统正在逐层扣减库存并计算末级需求。' },
  WRITING: { label: '结果写入中', type: 'warning', step: 3, guidance: '计算已完成，系统正在固化本次运行结果。' },
  WAITING_DAILY_DISTRIBUTION: { label: '生成日排产中', type: 'warning', step: 3, guidance: '系统正在按生产日历生成日排产并执行数量守恒检查。' },
  SCHEDULE_READY: { label: '整机排产已生成', type: 'primary', step: 3, guidance: '整机日排产已生成，请排产员确认。' },
  MATERIAL_REVIEW: { label: '物料待复核', type: 'warning', step: 3, guidance: '物料分析已生成，请处理缺料与异常并完成物控复核。' },
  MATERIAL_RECALC_REQUIRED: { label: '物料待重算', type: 'warning', step: 3, guidance: '库存分配已改变，原物料分析失效。请进入物料缺料页重新计算。' },
  READY: { label: '待发布', type: 'success', step: 4, guidance: '计算结果已通过门禁，请复核日排产、汇总和执行信息后发布。' },
  PUBLISHED: { label: '已发布', type: 'success', step: 4, guidance: '该版本已发布。如需调整，请生成修订版，不要直接修改已发布结果。' },
  STALE: { label: '输入已失效', type: 'warning', step: 3, guidance: '经营计划已更新，原批次仅供查看。请使用当前经营计划重新生成排产。' },
  SUPERSEDED: { label: '已生成发布版', type: 'info', step: 4, guidance: '该工作批次已经生成独立发布版本，仅供追溯，请进入对应的已发布版本查看或生成修订版。' },
  FAILED: { label: '计算失败', type: 'danger', step: 3, guidance: '本次计算失败，请查看失败原因并修复输入后重新计算。' },
  CANCELLED: { label: '已取消', type: 'info', step: 3, guidance: '该批次已取消，不再继续计算。' }
}

export function batchStatusDisplay(status?: string | null): BatchStatusDisplay {
  if (!status) return { label: '未知状态', type: 'info', step: 0, guidance: '暂未取得批次状态，请刷新后重试。' }
  return BATCH_STATUS_DISPLAY[status] ?? { label: status, type: 'info', step: 0, guidance: '该状态尚未配置操作指引，请联系系统管理员。' }
}

const SIMPLE_LABELS: Record<string, string> = {
  DRAFT: '草稿', PUBLISHED: '已发布', READY: '可用', ACTIVE: '有效',
  IMPORTED: '已导入', RETIRED: '已停用', EMPTY: '无数据',
  OPEN: '待处理', RESOLVED: '已处理', PENDING: '待分配',
  BLOCKING: '阻断', ERROR: '错误', WARNING: '警告', INFO: '提示',
  PLANNED: '待执行', LOCKED: '已锁定', EXECUTED: '已执行',
  GOOD: '良品', DEFECTIVE: '不良品',
  MAKE: '自制', PURCHASE: '外购', OUTSOURCE: '委外', SELF_MADE: '自制', PURCHASED: '外购', OUTSOURCED: '委外', UNMAINTAINED: '未维护',
  STOP: '停止下钻', EXPLODE: '继续下钻', LEAF: 'BOM 叶子'
  ,MACHINE_OUTPUT: '整机产出', FINISHED_GOOD: '整机', HEAD: '机头', HEAD_ASSEMBLY: '机头', PCBA: 'PCBA', RAW_MATERIAL: '原材料', OTHER: '其他'
}

export function businessLabel(value?: string | null): string {
  if (!value) return '-'
  return SIMPLE_LABELS[value] ?? value
}

const ISSUE_TYPE_LABELS: Record<string, string> = {
  MATERIAL_MISSING: '物料管理中缺少物料主数据',
  ROLE_MISSING: '物料分类角色未维护',
  MISSING_CATEGORY_ROLE: '物料分类角色未维护',
  SUPPLY_MODE_MISSING: '供应方式未维护',
  MISSING_MATERIAL_POLICY: '物料策略未维护',
  EXPLOSION_POLICY_MISSING: '下钻策略未维护',
  INVENTORY_MISSING: '缺少库存记录',
  SHARED_INVENTORY: '共享库存待分配',
  EXPLODE_WITHOUT_BOM: '继续下钻但没有下级 BOM',
  MULTIPLE_ACTIVE_BOM: '存在多个有效 BOM 版本',
  BOM_EFFECTIVE_OVERLAP: 'BOM 有效期重叠',
  BOM_ORG_AMBIGUOUS: 'BOM 库存组织不明确',
  INVALID_BOM_QUANTITY: 'BOM 用量无效',
  BOM_CYCLE: 'BOM 存在循环引用',
  TARGET_NON_PRODUCTION_DAY: '排产目标日为非生产日',
  NO_PRODUCTION_DAY: '没有可用生产日',
  EXECUTION_QUANTITY_MISMATCH: '实际完成量与计划量不一致',
  NO_FORECAST_DATA: '经营计划没有明细'
}

export function issueTypeLabel(value?: string | null): string {
  if (!value) return '-'
  return ISSUE_TYPE_LABELS[value] ?? value
}

export function supplyModeLabel(value: string | null | undefined): string {
  return !value || value === 'UNMAINTAINED' ? '未配置' : businessLabel(value)
}
