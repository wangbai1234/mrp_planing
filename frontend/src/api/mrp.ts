import { del, download, get, post, put, upload, uploadBlob } from './client'

function queryParams(params: Record<string, string | number | null | undefined>): URLSearchParams {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && String(value).trim() !== '') query.set(key, String(value).trim())
  }
  return query
}

export interface MrpCalcBatch {
  id: number
  factoryCode: string
  status: string
  forecastVersionId: number | null
  inventorySnapshotId: number | null
  bomSnapshotId: number | null
  bomSourceChecksum?: string | null
  categoryRoleVersionId: number | null
  materialPolicyVersionId: number | null
  productionCalendarVersionId: number | null
  factoryWarehouseMappingVersionId: number | null
  stockPolicyVersionId: number | null
  businessWeekVersionId: number | null
  inventorySnapshotGroupId: number | null
  decisionChecksum: string | null
  ruleVersion: string
  inputChecksum: string | null
  resultChecksum: string | null
  versionNo: number
  activeRunId: number | null
  publishedAt: string | null
  activeRequestKey: string
  issueCount: number
  unresolvedCount: number
  version: number
  createdBy: number
  createdAt: string
  updatedAt: string
  revisionOfBatchId: number | null
  revisionNo: number
  supersededByBatchId: number | null
}

export interface WorkbenchReviewStatus {
  schedule: 'NOT_GENERATED' | 'PENDING' | 'CONFIRMED' | 'EXPIRED'
  material: 'NOT_ANALYZED' | 'PENDING' | 'CONFIRMED' | 'EXPIRED'
  activeRunId: number | null
  scheduleFingerprint: string | null
  materialFingerprint: string | null
  openFeedback: number
}

export interface MaterialFeedback {
  id: number
  batchId: number
  runId: number
  factoryCode: string
  rootMaterialCode: string
  terminalMaterialCode: string
  sourceMonth: string
  affectedQuantity: string
  suggestedDate: string | null
  reason: string
  status: string
  response: string | null
}

export interface BatchSummary {
  batchId: number
  status: string
  factoryCode: string
  totalIssues: number
  openIssues: number
  dailyPlanCount: number
  unallocatedCount: number
  pendingUnallocated: number
  poolCount: number
  totalAvailable: number
  totalAllocated: number
  resultChecksum: string | null
  publishedAt: string | null
}

export interface BomSnapshot {
  id: number
  sourceVersion: string
  status: string
  checksum: string
  effectiveAt: string
  createdAt: string
}

export interface BomSnapshotDetail {
  id: number
  snapshotId: number
  parentCode: string
  childCode: string
  parentQty: number
  childQty: number
  bomVersion: string
  effectiveDate: string
  organizationCode: string
  bodyScope: string
  pathMetadata: string | null
}

export interface MrpInventoryBalance {
  id: number
  inventorySnapshotId: number
  factoryCode: string
  inventoryOrg: string
  materialCode: string
  availableQuantity: number
  includedFlag: boolean
}

export interface MrpInventoryPool {
  id: number
  batchId: number
  snapshotId: number
  factoryCode: string
  inventoryOrg: string
  materialCode: string
  availableQty: number
  reservedQty: number
  allocatedQty: number
  version: number
}

export interface MrpInventoryAllocation {
  id: number | null
  poolId: number
  demandKey: string
  rootMaterialCode: string
  sourceMonth: string
  prioritySeq: number
  pathHash: string | null
  grossQty: number
  allocatedQty: number
  allocationType: 'AUTO_PRIORITY' | 'MANUAL_OVERRIDE'
  adjustmentReason: string | null
  manualConfirmed: boolean
  revision: number
}

export interface MrpRequirementDetail {
  id: number
  runId: number
  batchId: number
  factoryCode: string
  rootMaterialCode: string
  sourceMonth: string
  parentMaterialCode: string | null
  currentMaterialCode: string
  levelNo: number
  pathText: string | null
  pathHash: string
  categoryRole: string | null
  supplyMode: string | null
  explosionPolicy: string | null
  effectiveExplosionPolicy?: string | null
  policySource?: string | null
  grossQty: number
  inventoryBeforeQty: number
  inventoryUsedQty: number
  inventoryAfterQty: number
  netQty: number
  terminalFlag: boolean
  terminalReason: string | null
}

export interface MrpTerminalDemand {
  id: number
  runId: number
  batchId: number
  factoryCode: string
  rootMaterialCode: string
  terminalMaterialCode: string
  terminalType: string
  supplyMode: string
  sourceMonth: string
  quantity: number
  inventoryPoolCount?: number
  availableQty?: number
  allocatedQty?: number
  warehouseRiskCount?: number
  affectedMachineCount?: number
  pendingIssueCount?: number
  pendingPolicyCount?: number
}

export interface MrpDailyPlanDetail {
  id: number
  runId: number
  batchId: number
  factoryCode: string
  rootMaterialCode: string
  terminalMaterialCode: string
  terminalType: string
  supplyMode: string
  rowKey?: string
  pathGroupKey: string
  sourceMonth: string
  sourceDate: string
  scheduleDate: string
  systemQuantity: string | number
  effectiveQuantity?: string | number
  isProductionDay: boolean
  revision: number
}

export interface MrpDailyPlanOverride {
  id: number
  batchId: number
  factoryCode: string
  rootMaterialCode: string
  terminalMaterialCode: string
  terminalType: string
  supplyMode: string
  pathGroupKey: string
  scheduleDate: string
  manualQuantity: string
  reason: string
  revision: number
}

export type DailyPlanView = 'MACHINE_OUTPUT' | 'TERMINAL'

export interface DailyGridRow {
  sourceMonth?: string | null
  materialUnit?: string | null
  rowKey: string
  factoryCode: string
  warehouseCode?: string
  warehouseName?: string
  rootMaterialCode: string
  terminalMaterialCode: string
  terminalType: string
  supplyMode: string
  pathGroupKey: string
  dayQuantities: Record<string, string>
  weekTotals: Record<string, string>
  rowTotal: string
}

export interface WeeklyPlanSummary {
  weekKey: string
  weekCode?: string
  businessMonth?: string
  weekStart: string
  weekEnd: string
  planCount: number
  totalQuantity: string | number
  materialUnit?: string | null
  aggregationKey?: string
  view?: DailyPlanView
  capacityBasis?: DailyPlanView
  ruleVersion?: string
}

export interface MonthlyPlanSummary {
  monthKey: string
  planCount: number
  totalQuantity: string | number
  materialUnit?: string | null
  aggregationKey?: string
  view?: DailyPlanView
  capacityBasis?: DailyPlanView
  ruleVersion?: string
}

export interface DailyGridResponse {
  capacityBasis?: DailyPlanView
  ruleVersion?: string
  view?: DailyPlanView
  activeRunId: number
  capacityLoads: DayCapacityLoad[]
  rows: DailyGridRow[]
  totalRows: number
  page: number
  pageSize: number
  calendarDays: string[]
  weekGroups: Array<{ weekKey: string; weekStart: string; weekEnd: string; days: string[] }>
  calendarDayInfo: Array<{ date: string; productionDay: boolean; reason: string }>
}

export interface PlanningCellFact extends MrpDailyPlanDetail {
  rowKey: string
  transferRowKey?: string
  materialUnit?: string | null
  executionStatus?: string | null
}
export interface LinkedTransferInput {
  rowKey: string
  sourceDate: string
  targetDate: string
  quantity: string
  expectedRunId: number
}
export interface LinkedTransferLine {
  rowKey: string
  terminalMaterialCode: string
  terminalType: string
  supplyMode: string
  sourceMonth: string
  pathGroupKey: string
  materialUnit?: string | null
  sourceQuantity: string
  targetQuantity: string
  suggestedQuantity: string
  sourceRevision: number
  targetRevision: number
}
export interface LinkedTransferPreview {
  fingerprint: string
  lines: LinkedTransferLine[]
  sourceLoad: DayCapacityLoad | null
  targetLoad: DayCapacityLoad | null
}

export interface MrpDecisionIssue {
  id: number
  batchId: number
  issueKey: string
  issueType: string
  severity: string
  scopeType: string | null
  scopeKey: string | null
  payloadJson: string | null
  status: string
  revision: number
  createdAt: string
  resolvedAt: string | null
}

export interface CalcTask {
  id: number
  status: string
  phase: string | null
  progressCurrent: number
  progressTotal: number
}

export interface LifecycleProgress {
  taskId: number
  taskType: string
  status: string
  progressCurrent: number
  progressTotal: number
  phase: string | null
  errorMessage: string | null
  resultBatchId: number | null
}

export interface CalculationProgress {
  taskId: number
  status: string
  progressCurrent: number
  progressTotal: number
  phase: string | null
  errorMessage: string | null
}

export interface MrpUnallocatedQuantity {
  id: number
  runId: number
  batchId: number
  demandKey: string
  sourceDate: string
  attemptedScheduleDate: string
  quantity: number
  reasonCode: string
  status: string
  resolvedScheduleDate: string | null
  revision: number
}

export interface CategoryRoleVersion {
  id: number
  versionNo: number
  status: string
  checksum: string | null
  createdBy: number
  createdAt: string
  publishedAt: string | null
}

export interface ProductionCalendarVersion {
  id: number
  versionNo: number
  factoryCode: string
  status: string
  checksum: string | null
  createdBy: number
  createdAt: string
  publishedAt: string | null
}

export interface MaterialPolicyVersion {
  id: number
  versionNo: number
  status: string
  checksum: string | null
  createdBy: number
  createdAt: string
  publishedAt: string | null
}

export type SupplyMode = 'SELF_MADE' | 'PURCHASED' | 'OUTSOURCED'
export type ExplosionPolicy = 'EXPLODE' | 'STOP'
export interface PolicyPage<T> { items: T[]; total: number; page: number; pageSize: number; totalPages: number }
export interface MaterialPolicyWrite { id?: number; materialCode: string; supplyMode: SupplyMode; explosionPolicy: ExplosionPolicy }
export interface MaterialPolicyNotice {
  materialCode: string; materialName?: string; factoryCode: string; missingType: 'BOTH' | 'SUPPLY' | 'EXPLOSION';
  effectiveExplosionPolicy: ExplosionPolicy; sourcesJson: string; defaultExplosionConfirmedAt: string | null
}

export interface MaterialPolicyDetail {
  id?: number
  versionId?: number
  materialCode: string
  supplyMode: 'SELF_MADE' | 'PURCHASED' | 'OUTSOURCED' | 'UNMAINTAINED'
  explosionPolicy: 'EXPLODE' | 'STOP' | 'UNMAINTAINED'
  revision?: number
  createdBy?: number | null
  createdByName?: string | null
  createdAt?: string
  updatedBy?: number | null
  updatedByName?: string | null
  updatedAt?: string
}

export interface ProductionCalendarDay {
  id?: number
  versionId?: number
  factoryCode?: string
  calendarDate: string
  isProductionDay: boolean
  allocationWeight: number
  dailyCapacity: number | null
  reason: string
  revision?: number
  createdBy?: number | null
  createdByName?: string | null
  createdAt?: string
  updatedBy?: number | null
  updatedByName?: string | null
  updatedAt?: string
}

export interface CalendarEntry {
  id?: number; factoryCode: string; calendarDate: string; productionDay: boolean | null;
  dailyCapacity: string | number | null; capacityUnit: string | null; reason: string; revision: number;
  createdByName?: string | null; createdAt?: string; updatedByName?: string | null; updatedAt?: string;
}
export interface CalendarBatchRequest {
  factoryCode: string; startDate: string; endDate: string; weekdays: number[]; productionDay: boolean;
  dailyCapacity: string; capacityUnit: string; reason: string; overwrite: boolean;
  copyFromFactory?: string; expectedFingerprint?: string;
}
export interface CalendarBatchPreview { inserted: number; updated: number; preserved: number; fingerprint: string; rows: CalendarEntry[] }
export interface PlanningCalendarCheck { total: number; missingDays: { factoryCode: string; date: string }[]; configurationIssues: string[] }
export interface DayCapacityLoad {
  capacityBasis?: DailyPlanView
  factoryCode: string; date: string; configured: boolean; productionDay: boolean;
  dailyCapacity: string | number | null; capacityUnit: string | null; load: string | number;
  excess: string | number | null; status: 'NORMAL' | 'OVERLOAD' | 'REST_LOAD' | 'UNASSESSED';
  message: string; partiallyUnassessed: boolean;
}
export interface DailyTransferRequest {
  rowKey: string; sourceDate: string; targetDate: string; quantity: string; reason: string;
  expectedRunId: number; expectedSourceQuantity: string; expectedTargetQuantity: string;
  expectedSourceRevision: number; expectedTargetRevision: number;
}

export interface CapacityFactoryLine {
  id?: number
  factoryCode: string
  lineCode: string
  lineName?: string
  weeklyCapacity: number
  isActive: boolean
}

export interface ConfigLog {
  action: string
  resource_id: string
  before_value: string | null
  after_value: string | null
  operator_name: string | null
  created_at: string
}

export interface ConfigVersionBase {
  id: number
  versionNo: number
  status: string
  checksum?: string | null
  createdBy?: number
  createdAt?: string
  publishedAt?: string | null
  effectiveDate?: string | null
}

export interface FactoryWarehouseMappingVersion extends ConfigVersionBase {}

export interface FactoryWarehouseMappingDetail {
  id?: number
  versionId?: number
  factoryCode: string
  factoryName?: string
  warehouseCode: string
  warehouseName: string
  enabled: boolean
  warehouseManager?: string | null
  supplierName?: string | null
  goodWarehouse?: boolean | null
  warehouseAttribute?: string | null
  priorityNo?: number | null
  qualityConfigured?: boolean
  createdBy?: number | null
  createdByName?: string | null
  createdAt?: string
  updatedBy?: number | null
  updatedByName?: string | null
  updatedAt?: string
}

export interface FactoryMaster {
  id: number
  factoryCode: string
  factoryName: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface StockPolicyVersion extends ConfigVersionBase {}

export interface StockPolicyWarehouse {
  code: string
  name: string
}

export interface StockPolicyMaterialOption {
  specModel?: string
  materialCode: string
  materialName: string
}

export interface StockPolicyDetail {
  id?: number
  versionId?: number
  warehouseCode: string
  materialCode: string
  safetyStockQty: number
  warningStockQty: number
  createdBy?: number | null
  createdByName?: string | null
  createdAt?: string
  updatedBy?: number | null
  updatedByName?: string | null
  updatedAt?: string
}

export interface StockPolicyRisk {
  id: number
  batchId: number
  runId: number
  riskType: 'SAFETY_STOCK_BREACH' | 'WARNING_STOCK_BREACH' | 'STOCK_POLICY_MISSING'
  warehouseCode: string | null
  materialCode: string
  availableQty: number | null
  usedQty: number | null
  remainingQty: number | null
  safetyStockQty: number | null
  warningStockQty: number | null
  rootMaterialCodes: string | null
  resolvedAt?: string | null
  resolvedBy?: number | null
  resolutionCode?: string | null
}

export interface ConfigImportPreview {
  rows: unknown[]
  errors: string[]
  totalRows: number
  successRows: number
  errorRows: number
  previewRows: Array<{ row: number; cells: string[]; type: 'success' | 'error'; message: string }>
}

export interface StockPolicyImportPreview extends ConfigImportPreview {
  rows: StockPolicyDetail[]
}

export interface BusinessWeekVersion extends ConfigVersionBase {
  businessMonth: string
}

export interface BusinessWeekDetail {
  id?: number
  businessMonth?: string
  weekCode: string
  startDate: string
  endDate: string
  sortNo?: number
  revision?: number
  createdByName?: string | null
  createdAt?: string
  updatedByName?: string | null
  updatedAt?: string
}

export interface BatchCreationVersions {
  categoryRoleVersions: CategoryRoleVersion[]
  materialPolicyVersions: MaterialPolicyVersion[]
  productionCalendarVersions: ProductionCalendarVersion[]
}

export interface ForecastVersion {
  id: number
  versionNo: number
  fileName?: string
  status: string
  createdAt: string
}

export interface InventorySnapshot {
  id: number
  snapshotDate: string | null
  warehouseCode: string | null
  fileName?: string | null
  status: string
  createdAt?: string
}

export interface ForecastMaterialSummary {
  materialId: string
  materialName: string
  factoryCode: string
  months: Record<string, number>
  total: number
}

function form(file: File): FormData {
  const data = new FormData()
  data.append('file', file)
  return data
}

export const mrpApi = {
  // BOM Snapshots
  createBomSnapshot: (effectiveDate?: string) =>
    post<BomSnapshot>(`/mrp/bom-snapshots${effectiveDate ? `?effectiveDate=${effectiveDate}` : ''}`),

  getBomSnapshot: (id: number) =>
    get<BomSnapshot>(`/mrp/bom-snapshots/${id}`),

  getBomSnapshotDetails: (id: number) =>
    get<BomSnapshotDetail[]>(`/mrp/bom-snapshots/${id}/details`),

  getBomSnapshots: () =>
    get<BomSnapshot[]>('/mrp/bom-snapshots'),

  getCurrentBomSnapshot: () =>
    get<BomSnapshot>('/mrp/bom-snapshots/current'),

  getCurrentBomStatus: () =>
    get<{ localCount: number }>('/mrp/batches/current-bom-status'),

  // Inventory
  normalizeInventory: (snapshotId: number, factoryCode: string, inventoryOrg: string) =>
    post<MrpInventoryBalance[]>(`/mrp/inventory/normalize/${snapshotId}?factoryCode=${factoryCode}&inventoryOrg=${inventoryOrg}`),

  getInventoryBalances: (snapshotId: number) =>
    get<MrpInventoryBalance[]>(`/mrp/inventory/balances/${snapshotId}`),

  getInventorySnapshots: (warehouseCode?: string) =>
    get<InventorySnapshot[]>(`/mrp/inventory/snapshots${warehouseCode ? `?warehouseCode=${encodeURIComponent(warehouseCode)}` : ''}`),

  getPools: (batchId: number) =>
    get<MrpInventoryPool[]>(`/mrp/inventory/pools/${batchId}`),

  updateAllocation: (allocationId: number, allocatedQty: number, expectedRevision: number, reason: string) =>
    put<void>(`/mrp/inventory/allocations/${allocationId}?${queryParams({ allocatedQty, expectedRevision, reason })}`, {}),
  resetAllocation: (allocationId: number) =>
    post<void>(`/mrp/inventory/allocations/${allocationId}/reset`),
  getAllocationsByPool: (poolId: number) =>
    get<MrpInventoryAllocation[]>(`/mrp/inventory/allocations/pool/${poolId}`),

  // Batches
  createBatch: (factoryCode: string, forecastVersionId: number, inventorySnapshotId: number, bomSnapshotId?: number, categoryRoleVersionId?: number) =>
    post<MrpCalcBatch>(`/mrp/batches?${queryParams({ factoryCode, forecastVersionId, inventorySnapshotId, bomSnapshotId, categoryRoleVersionId })}`),

  getBatch: (id: number) =>
    get<MrpCalcBatch>(`/mrp/batches/${id}`),

  getCurrentBatch: () =>
    get<MrpCalcBatch | null>('/mrp/batches/current'),

  getBatchFactories: (id: number) => get<string[]>('/mrp/batches/' + id + '/factories'),

  cancelBatch: (batchId: number) =>
    post<MrpCalcBatch>(`/mrp/batches/${batchId}/cancel`),

  getBatchSummary: (batchId: number) =>
    get<BatchSummary>(`/mrp/batches/${batchId}/summary`),

  getBatches: (status?: string) =>
    get<MrpCalcBatch[]>(`/mrp/batches${status ? `?status=${status}` : ''}`),

  getBatchesPage: (page: number = 1, pageSize: number = 20, status?: string, keyword?: string) => {
    const params = new URLSearchParams()
    params.append('page', page.toString())
    params.append('pageSize', pageSize.toString())
    if (status) params.append('status', status)
    if (keyword) params.append('keyword', keyword)
    return get<{items: MrpCalcBatch[], total: number, page: number, pageSize: number, totalPages: number}>(`/mrp/batches/page?${params.toString()}`)
  },

  precheck: (batchId: number) =>
    post<MrpCalcBatch>(`/mrp/batches/${batchId}/precheck`),

  lockAndCalculate: (batchId: number) =>
    post<MrpCalcBatch>(`/mrp/batches/${batchId}/lock-and-calculate`),

  calculate: (batchId: number) =>
    post<CalcTask>(`/mrp/batches/${batchId}/calculate`),
  getCalculationProgress: (batchId: number) =>
    get<CalculationProgress | null>(`/mrp/batches/${batchId}/calculation-task`),

  distribute: (batchId: number, runId: number, calendarVersionId: number) =>
    post<{details: MrpDailyPlanDetail[], unallocated: MrpUnallocatedQuantity[]}>(`/mrp/batches/${batchId}/distribute?runId=${runId}&calendarVersionId=${calendarVersionId}`),

  publish: (batchId: number, capacityFingerprint?: string) =>
    post<CalculationProgress>(`/mrp/batches/${batchId}/publish`, capacityFingerprint ? { capacityFingerprint } : undefined),
  getLifecycleTask: (batchId: number, kind: 'publish' | 'revise') =>
    get<LifecycleProgress | null>(`/mrp/batches/${batchId}/lifecycle-task?kind=${kind}`),
  getPlanningCalendarCheck: (batchId: number, latest = false) => get<PlanningCalendarCheck>(`/mrp/batches/${batchId}/calendar-check?latest=${latest}`),
  getPublishCapacity: (batchId: number) => get<{ loads: DayCapacityLoad[]; fingerprint: string }>(`/mrp/batches/${batchId}/capacity-review`),

  // Requirements
  getRequirements: (batchId: number) =>
    get<MrpRequirementDetail[]>(`/mrp/batches/${batchId}/requirements`),
  getRequirementsPage: (batchId: number, page: number, pageSize: number) =>
    get<PolicyPage<MrpRequirementDetail>>(`/mrp/batches/${batchId}/requirements/page?${queryParams({ page, pageSize })}`),

  getTerminalDemands: (batchId: number) =>
    get<MrpTerminalDemand[]>(`/mrp/batches/${batchId}/terminal-demands`),
  getTerminalDemandsPage: (batchId: number, page: number, pageSize: number, keyword?: string) =>
    get<PolicyPage<MrpTerminalDemand>>(`/mrp/batches/${batchId}/terminal-demands/page?${queryParams({ page, pageSize, keyword })}`),
  getShortagePage: (batchId: number, params: { page: number; pageSize: number; factoryCode?: string; rootMaterialCode?: string; terminalMaterialCode?: string; sourceMonth?: string; keyword?: string; detail?: boolean }) =>
    get<PolicyPage<MrpTerminalDemand>>(`/mrp/batches/${batchId}/shortage/page?${queryParams({ ...params, detail: params.detail ? 'true' : undefined })}`),
  exportShortage: (batchId: number, params: { factoryCode?: string; rootMaterialCode?: string; sourceMonth?: string; keyword?: string }) =>
    download(`/mrp/batches/${batchId}/shortage/export?${queryParams(params)}`, `物料汇总_${batchId}.xlsx`),
  getShortagePools: (batchId: number, params: { page: number; pageSize: number; factoryCode: string; terminalMaterialCode: string }) =>
    get<PolicyPage<MrpInventoryPool>>(`/mrp/batches/${batchId}/shortage/pools?${queryParams(params)}`),
  getWorkbenchReviews: (batchId: number) => get<WorkbenchReviewStatus>(`/mrp/batches/${batchId}/reviews`),
  confirmSchedule: (batchId: number, fingerprint: string) => post<WorkbenchReviewStatus>(`/mrp/batches/${batchId}/schedule-confirmation`, { fingerprint }),
  confirmMaterial: (batchId: number, fingerprint: string) => post<WorkbenchReviewStatus>(`/mrp/batches/${batchId}/material-confirmation`, { fingerprint }),
  startMaterialAnalysis: (batchId: number) => post<CalcTask>(`/mrp/batches/${batchId}/material-analysis`),
  getMaterialFeedback: (batchId: number, page: number, pageSize: number) => get<PolicyPage<MaterialFeedback>>(`/mrp/batches/${batchId}/material-feedback/page?${queryParams({ page, pageSize })}`),
  createMaterialFeedback: (batchId: number, input: Pick<MaterialFeedback, 'factoryCode' | 'rootMaterialCode' | 'terminalMaterialCode' | 'sourceMonth' | 'affectedQuantity' | 'reason' | 'suggestedDate'>) => post<MaterialFeedback>(`/mrp/batches/${batchId}/material-feedback`, input),
  respondMaterialFeedback: (batchId: number, id: number, response: string) => post<MaterialFeedback>(`/mrp/batches/${batchId}/material-feedback/${id}/respond`, { response }),
  getInventoryPoolsPage: (batchId: number, page: number, pageSize: number) =>
    get<PolicyPage<MrpInventoryPool>>(`/mrp/inventory/pools/${batchId}/page?${queryParams({ page, pageSize })}`),
  getPoolAllocationCount: (poolId: number) =>
    get<number>(`/mrp/inventory/allocations/pool/${poolId}/count`),

  // Daily Plans
  getDailyPlans: (batchId: number, startDate?: string, endDate?: string) =>
    get<MrpDailyPlanDetail[]>(`/mrp/batches/${batchId}/daily-plans${startDate && endDate ? `?startDate=${startDate}&endDate=${endDate}` : ''}`),
  exportDailyPlans: (batchId: number, view: DailyPlanView = 'MACHINE_OUTPUT') => download(`/mrp/plans/${batchId}/export?${queryParams({ view })}`, `${view === 'MACHINE_OUTPUT' ? '排产计划' : '物料需求'}_${batchId}.xlsx`),
  getDailyGrid: (batchId: number, params: { page: number; pageSize: number; startDate?: string; endDate?: string; terminalMaterialCode?: string; factoryCode?: string; keyword?: string; view?: DailyPlanView }) => {
    const query = queryParams({ page: params.page, pageSize: params.pageSize, startDate: params.startDate, endDate: params.endDate, factoryCode: params.factoryCode, keyword: params.keyword })
    if (params.terminalMaterialCode) query.set('terminalMaterialCode', params.terminalMaterialCode)
    if (params.view) query.set('view', params.view)
    return get<DailyGridResponse>(`/mrp/plans/${batchId}/daily-grid?${query.toString()}`)
  },
  getPlanningCellDetails: (batchId: number, params: { page: number; pageSize: number; factoryCode: string; startDate: string; endDate: string; rootMaterialCode?: string; terminalMaterialCode?: string; sourceMonth?: string; view?: DailyPlanView }) =>
    get<PolicyPage<PlanningCellFact>>(`/mrp/batches/${batchId}/rolling-details?${queryParams({ ...params, terminalType: params.view === 'MACHINE_OUTPUT' ? 'MACHINE_OUTPUT' : undefined })}`),
  getRollingGrid: (batchId: number, params: { page: number; pageSize: number; factoryCode?: string; keyword?: string; rootMaterialCode?: string; view?: DailyPlanView }) =>
    get<DailyGridResponse>(`/mrp/batches/${batchId}/rolling-grid?${queryParams(params)}`),
  previewLinkedTransfer: (batchId: number, request: LinkedTransferInput) =>
    post<LinkedTransferPreview>(`/mrp/batches/${batchId}/linked-transfers/preview`, request),
  commitLinkedTransfer: (batchId: number, request: LinkedTransferInput & { fingerprint: string; reason: string; lines: Array<{ rowKey: string; quantity: string }> }) =>
    post<void>(`/mrp/batches/${batchId}/linked-transfers`, request),
  getWeeklySummary: (batchId: number, view?: DailyPlanView) =>
    get<WeeklyPlanSummary[]>(`/mrp/batches/${batchId}/plans/weekly${view ? `?view=${view}` : ''}`),
  getMonthlySummary: (batchId: number, view?: DailyPlanView) =>
    get<MonthlyPlanSummary[]>(`/mrp/batches/${batchId}/plans/monthly${view ? `?view=${view}` : ''}`),

  // Issues
  getIssues: (batchId: number, status?: string) =>
    get<MrpDecisionIssue[]>(`/mrp/batches/${batchId}/issues${status ? `?status=${status}` : ''}`),
  getIssuesPage: (batchId: number, page: number, pageSize: number, status?: string, keyword?: string) =>
    get<PolicyPage<MrpDecisionIssue>>(`/mrp/batches/${batchId}/issues/page?${queryParams({ page, pageSize, status, keyword })}`),
  resolveIssue: (issueId: number, decisionCode: string, reason?: string, expectedRevision?: number) =>
    post<unknown>(`/mrp/batches/issues/${issueId}/resolve?decisionCode=${encodeURIComponent(decisionCode)}${reason ? `&reason=${encodeURIComponent(reason)}` : ''}${expectedRevision !== undefined ? `&expectedRevision=${expectedRevision}` : ''}`),
  resolveMissingInventory: (batchId: number, issueIds: number[], reason: string) =>
    post<void>(`/mrp/batches/${batchId}/issues/inventory-missing/resolve`, { issueIds, reason }),
  reviseBatch: (batchId: number) => post<CalculationProgress>(`/mrp/batches/${batchId}/revise`),
  saveDailyOverride: (batchId: number, override: Record<string, unknown>) => post<void>(`/mrp/batches/${batchId}/overrides`, override),
  transferDaily: (batchId: number, request: DailyTransferRequest) => post<void>(`/mrp/batches/${batchId}/daily-transfers`, request),
  getCapacityLoads: (batchId: number, startDate: string, endDate: string) => get<DayCapacityLoad[]>(`/mrp/batches/${batchId}/capacity-loads?${queryParams({ startDate, endDate })}`),
  getDailyOverrides: (batchId: number) => get<MrpDailyPlanOverride[]>(`/mrp/batches/${batchId}/overrides`),
  restoreDailyOverride: (overrideId: number) => post<void>(`/mrp/overrides/${overrideId}/restore-auto`),

  // Unallocated
  getPendingUnallocatedPage: (batchId: number, page: number, pageSize: number, keyword?: string) =>
    get<PolicyPage<MrpUnallocatedQuantity>>(`/mrp/batches/${batchId}/unallocated/page?${queryParams({ page, pageSize, keyword })}`),
  getUnallocated: (batchId: number) =>
    get<MrpUnallocatedQuantity[]>(`/mrp/batches/${batchId}/unallocated`),

  // Config
  getCategoryRoleVersions: () =>
    get<CategoryRoleVersion[]>('/mrp/category-role-versions'),

  createCategoryRoleVersion: () =>
    post<CategoryRoleVersion>('/mrp/category-role-versions'),

  getProductionCalendarVersions: () =>
    get<ProductionCalendarVersion[]>('/mrp/production-calendar-versions'),

  createProductionCalendarVersion: (factoryCode: string) =>
    post<ProductionCalendarVersion>(`/mrp/production-calendar-versions?factoryCode=${factoryCode}`),

  getProductionCalendarDays: (versionId: number) =>
    get<ProductionCalendarDay[]>(`/mrp/production-calendar-versions/${versionId}/days`),

  saveProductionCalendarDays: (versionId: number, days: ProductionCalendarDay[]) =>
    post<void>(`/mrp/production-calendar-versions/${versionId}/days`, days),

  publishProductionCalendarVersion: (versionId: number) =>
    post<void>(`/mrp/production-calendar-versions/${versionId}/publish`),

  deleteProductionCalendarVersion: (versionId: number) =>
    del<void>(`/mrp/production-calendar-versions/${versionId}`),

  getMaterialPolicyVersions: () =>
    get<MaterialPolicyVersion[]>('/mrp/material-policy-versions'),

  createMaterialPolicyVersion: () =>
    post<MaterialPolicyVersion>('/mrp/material-policy-versions'),

  getMaterialPolicyDetails: (versionId: number) =>
    get<MaterialPolicyDetail[]>(`/mrp/material-policy-versions/${versionId}/details`),

  saveMaterialPolicyDetails: (versionId: number, details: MaterialPolicyDetail[]) =>
    post<void>(`/mrp/material-policy-versions/${versionId}/details`, details),

  publishMaterialPolicyVersion: (versionId: number) =>
    post<void>(`/mrp/material-policy-versions/${versionId}/publish`),

  deleteMaterialPolicyVersion: (versionId: number) =>
    del<void>(`/mrp/material-policy-versions/${versionId}`),

  getFactoryWarehouseMappingVersions: () =>
    get<FactoryWarehouseMappingVersion[]>('/mrp/factory-warehouse-mapping-versions'),

  createFactoryWarehouseMappingVersion: (effectiveDate?: string) =>
    post<FactoryWarehouseMappingVersion>('/mrp/factory-warehouse-mapping-versions', effectiveDate ? { effectiveDate } : undefined),

  getFactoryWarehouseMappingDetails: (versionId: number) =>
    get<FactoryWarehouseMappingDetail[]>(`/mrp/factory-warehouse-mapping-versions/${versionId}/details`),

  getFactoryWarehouseMappings: () =>
    get<FactoryWarehouseMappingDetail[]>('/mrp/factory-warehouse-mappings'),
  getFactoryWarehouseMappingPage: (keyword: string, quality: string, page: number, pageSize: number) =>
    get<{ items: FactoryWarehouseMappingDetail[]; total: number; page: number; pageSize: number }>(`/mrp/factory-warehouse-mappings/page?${queryParams({ keyword, quality, page, pageSize })}`),
  createFactoryWarehouseMapping: (row: FactoryWarehouseMappingDetail) =>
    post<FactoryWarehouseMappingDetail>('/mrp/factory-warehouse-mappings', row),
  updateFactoryWarehouseMapping: (id: number, row: FactoryWarehouseMappingDetail) =>
    put<FactoryWarehouseMappingDetail>(`/mrp/factory-warehouse-mappings/${id}`, row),
  deleteFactoryWarehouseMapping: (id: number) =>
    del<void>(`/mrp/factory-warehouse-mappings/${id}`),

  searchPolicyMaterials: (kind: 'material' | 'stock', keyword: string, page: number) =>
    get<PolicyPage<StockPolicyMaterialOption>>(`/mrp/${kind}-policies/materials/page?${new URLSearchParams({ keyword, page: String(page), pageSize: '10' })}`),
  searchForecastMaterials: (keyword: string, page: number) =>
    get<PolicyPage<StockPolicyMaterialOption>>('/forecast-workspace/materials/page?' + new URLSearchParams({ keyword, page: String(page), pageSize: '10' })),
  getMaterialPolicyPage: (keyword: string, supplyMode: string | null | undefined, explosionPolicy: string | null | undefined, page: number, pageSize: number) =>
    get<PolicyPage<MaterialPolicyDetail>>(`/mrp/material-policies/page?${queryParams({ keyword, supplyMode, explosionPolicy, page, pageSize })}`),
  getStockPolicyPage: (keyword: string, warehouseCode: string | null | undefined, page: number, pageSize: number) =>
    get<PolicyPage<StockPolicyDetail>>(`/mrp/stock-policies/page?${queryParams({ keyword, warehouseCode, page, pageSize })}`),
  getMaterialPolicyNotices: (batchId: number, keyword = '', missingType = '', page = 1, pageSize = 20) =>
    get<PolicyPage<MaterialPolicyNotice>>(`/mrp/batches/${batchId}/material-policy-notices?${queryParams({ keyword, missingType, page, pageSize })}`),
  getStockPolicies: () => get<StockPolicyDetail[]>('/mrp/stock-policies'),
  getPendingDefaultExplosionCount: (batchId: number) =>
    get<number>(`/mrp/batches/${batchId}/material-policy-notices/pending-default-count`),
  confirmDefaultExplosion: (batchId: number) =>
    post<number>(`/mrp/batches/${batchId}/material-policy-notices/confirm-default-explosion`),
  getStockPolicyWarehouses: () => get<StockPolicyWarehouse[]>('/mrp/stock-policies/warehouses'),
  searchStockPolicyMaterials: (keyword: string) =>
    get<StockPolicyMaterialOption[]>(`/mrp/stock-policies/materials?keyword=${encodeURIComponent(keyword)}`),
  createStockPolicy: (row: StockPolicyDetail) => post<StockPolicyDetail>('/mrp/stock-policies', row),
  updateStockPolicy: (id: number, row: StockPolicyDetail) => put<StockPolicyDetail>(`/mrp/stock-policies/${id}`, row),
  deleteStockPolicy: (id: number) => del<void>(`/mrp/stock-policies/${id}`),

  getMaterialPolicies: () => get<MaterialPolicyDetail[]>('/mrp/material-policies'),
  createMaterialPolicy: (row: MaterialPolicyWrite) => post<MaterialPolicyDetail>('/mrp/material-policies', row),
  updateMaterialPolicy: (id: number, row: MaterialPolicyWrite) => put<MaterialPolicyDetail>(`/mrp/material-policies/${id}`, row),
  deleteMaterialPolicy: (id: number) => del<void>(`/mrp/material-policies/${id}`),

  getFactories: () => get<string[]>('/mrp/factories'),
  getFactoryMaster: () => get<FactoryMaster[]>('/mrp/factory-master'),
  getFactoryMasterPage: (keyword: string, status: string, page: number, pageSize: number) =>
    get<{ items: FactoryMaster[]; total: number; page: number; pageSize: number }>(`/mrp/factory-master/page?${queryParams({ keyword, status, page, pageSize })}`),
  createFactory: (row: Pick<FactoryMaster, 'factoryCode' | 'factoryName' | 'enabled'>) => post<FactoryMaster>('/mrp/factory-master', row),
  updateFactory: (code: string, row: Pick<FactoryMaster, 'factoryCode' | 'factoryName' | 'enabled'>) => put<FactoryMaster>(`/mrp/factory-master/${encodeURIComponent(code)}`, row),
  deleteFactory: (code: string) => del<void>(`/mrp/factory-master/${encodeURIComponent(code)}`),
  getCalendarMonth: (factoryCode: string, month: string) => get<CalendarEntry[]>(`/mrp/production-calendars/month?${queryParams({factoryCode, month})}`),
  getCalendarPage: (params: {factoryCode: string; month: string; page: number; pageSize: number; keyword?: string; status?: string}) => get<{items: CalendarEntry[]; total: number; page: number; pageSize: number}>(`/mrp/production-calendars/page?${queryParams(params)}`),
  previewCalendarBatch: (request: CalendarBatchRequest) => post<CalendarBatchPreview>('/mrp/production-calendars/batch/preview', request),
  saveCalendarBatch: (request: CalendarBatchRequest) => post<CalendarBatchPreview>('/mrp/production-calendars/batch', request),
  downloadCalendarMonth: (factoryCode: string, month: string) => download(`/mrp/production-calendars/export?${queryParams({ factoryCode, month })}`, `生产日历_${factoryCode}_${month}.xlsx`),
  getProductionCalendar: (factoryCode?: string) =>
    get<CalendarEntry[]>(`/mrp/production-calendars${factoryCode ? `?factoryCode=${factoryCode}` : ''}`),
  createProductionCalendar: (row: CalendarEntry) => post<CalendarEntry>('/mrp/production-calendars', row),
  updateProductionCalendar: (id: number, row: CalendarEntry) =>
    put<CalendarEntry>(`/mrp/production-calendars/${id}`, row),
  deleteProductionCalendar: (id: number) => del<void>(`/mrp/production-calendars/${id}`),

  getBusinessWeeks: (businessMonth?: string) =>
    get<BusinessWeekDetail[]>(`/mrp/business-weeks${businessMonth ? `?businessMonth=${businessMonth}-01` : ''}`)
      .then(rows => rows.map(row => ({ ...row, businessMonth: row.businessMonth?.substring(0, 7) }))),
  saveBusinessWeeks: (businessMonth: string, weeks: BusinessWeekDetail[]) =>
    put<BusinessWeekDetail[]>('/mrp/business-weeks', { businessMonth: `${businessMonth}-01`, weeks }),
  deleteBusinessMonth: (businessMonth: string) =>
    del<void>(`/mrp/business-weeks?businessMonth=${businessMonth}-01`),

  getConfigLogs: (resourceType: string, resourceId: string) =>
    get<{ items: ConfigLog[] }>(`/audit-logs?${queryParams({ resourceType, resourceId, pageSize: 100 })}`)
      .then(result => result.items),

  importStockPolicies: (file: File) => upload<{ inserted: number; updated: number }>('/mrp/stock-policies/import', form(file)),
  previewStockPolicyImport: (file: File) => upload<ConfigImportPreview>('/mrp/stock-policies/import/preview', form(file)),
  downloadStockPolicyErrors: (file: File) => uploadBlob('/mrp/stock-policies/import/errors', form(file), '库存控制策略_错误数据.xlsx'),
  downloadStockPolicyTemplate: () => download('/mrp/stock-policies/template', '库存控制策略_导入模板.xlsx'),

  importFactoryWarehouseMappings: (file: File) =>
    upload<{ inserted: number; updated: number }>('/mrp/factory-warehouse-mappings/import', form(file)),
  previewFactoryWarehouseImport: (file: File) =>
    upload<ConfigImportPreview>('/mrp/factory-warehouse-mappings/import/preview', form(file)),
  downloadFactoryWarehouseErrors: (file: File) => uploadBlob('/mrp/factory-warehouse-mappings/import/errors', form(file), '工厂仓库映射_错误数据.xlsx'),
  downloadFactoryWarehouseTemplate: () => download('/mrp/factory-warehouse-mappings/template', '工厂仓库映射_导入模板.xlsx'),

  importMaterialPolicies: (file: File) => upload<{ inserted: number; updated: number }>('/mrp/material-policies/import', form(file)),
  previewMaterialPolicyImport: (file: File) =>
    upload<ConfigImportPreview>('/mrp/material-policies/import/preview', form(file)),
  downloadMaterialPolicyErrors: (file: File) => uploadBlob('/mrp/material-policies/import/errors', form(file), '物料策略_错误数据.xlsx'),
  downloadMaterialPolicyTemplate: () => download('/mrp/material-policies/template', '物料策略_导入模板.xlsx'),

  importProductionCalendar: (file: File) =>
    upload<{ inserted: number; updated: number }>('/mrp/production-calendars/import', form(file)),
  previewProductionCalendarImport: (file: File) =>
    upload<ConfigImportPreview>('/mrp/production-calendars/import/preview', form(file)),
  downloadProductionCalendarErrors: (file: File) => uploadBlob('/mrp/production-calendars/import/errors', form(file), '生产日历_错误数据.xlsx'),
  downloadProductionCalendarTemplate: () => download('/mrp/production-calendars/template', '生产日历_导入模板.xlsx'),

  importBusinessWeeks: (file: File) => upload<{ inserted: number; updated: number }>('/mrp/business-weeks/import', form(file)),
  previewBusinessWeekImport: (file: File) =>
    upload<ConfigImportPreview>('/mrp/business-weeks/import/preview', form(file)),
  downloadBusinessWeekErrors: (file: File) => uploadBlob('/mrp/business-weeks/import/errors', form(file), '业务周期间_错误数据.xlsx'),
  downloadBusinessWeekTemplate: () => download('/mrp/business-weeks/template', '业务周期间_导入模板.xlsx'),

  saveFactoryWarehouseMappingDetails: (versionId: number, details: FactoryWarehouseMappingDetail[]) =>
    post<void>(`/mrp/factory-warehouse-mapping-versions/${versionId}/details`, details),

  publishFactoryWarehouseMappingVersion: (versionId: number) =>
    post<void>(`/mrp/factory-warehouse-mapping-versions/${versionId}/publish`),

  deleteFactoryWarehouseMappingVersion: (versionId: number) =>
    del<void>(`/mrp/factory-warehouse-mapping-versions/${versionId}`),

  getStockPolicyVersions: () =>
    get<StockPolicyVersion[]>('/mrp/stock-policy-versions'),

  createStockPolicyVersion: (effectiveDate?: string) =>
    post<StockPolicyVersion>('/mrp/stock-policy-versions', effectiveDate ? { effectiveDate } : undefined),

  getStockPolicyDetails: (versionId: number) =>
    get<StockPolicyDetail[]>(`/mrp/stock-policy-versions/${versionId}/details`),

  saveStockPolicyDetails: (versionId: number, details: StockPolicyDetail[]) =>
    post<void>(`/mrp/stock-policy-versions/${versionId}/details`, details),

  publishStockPolicyVersion: (versionId: number) =>
    post<void>(`/mrp/stock-policy-versions/${versionId}/publish`),

  deleteStockPolicyVersion: (versionId: number) =>
    del<void>(`/mrp/stock-policy-versions/${versionId}`),

  createIncrementalStockPolicyVersion: (versionId: number, effectiveDate: string, overrides: StockPolicyDetail[]) =>
    post<StockPolicyVersion>(`/mrp/stock-policy-versions/${versionId}/incremental`, { effectiveDate, overrides }),

  getStockRisks: (batchId: number) =>
    get<StockPolicyRisk[]>(`/mrp/inventory/stock-risks/${batchId}`),
  getStockRisksPage: (batchId: number, page: number, pageSize: number, runId?: number) =>
    get<PolicyPage<StockPolicyRisk>>(`/mrp/inventory/stock-risks/${batchId}/page?${queryParams({ page, pageSize, runId })}`),
  applyZeroStockThresholds: (batchId: number, runId: number) =>
    post<number>(`/mrp/inventory/stock-risks/${batchId}/apply-zero-thresholds?runId=${runId}`),

  getBusinessWeekVersions: () =>
    get<BusinessWeekVersion[]>('/mrp/business-week-versions'),

  createBusinessWeekVersion: (businessMonth: string) =>
    post<BusinessWeekVersion>('/mrp/business-week-versions', { businessMonth: `${businessMonth}-01` }),

  getBusinessWeekDetails: (versionId: number) =>
    get<BusinessWeekDetail[]>(`/mrp/business-week-versions/${versionId}/details`),

  saveBusinessWeekDetails: (versionId: number, details: BusinessWeekDetail[]) =>
    post<void>(`/mrp/business-week-versions/${versionId}/details`, details),

  publishBusinessWeekVersion: (versionId: number) =>
    post<void>(`/mrp/business-week-versions/${versionId}/publish`),

  deleteBusinessWeekVersion: (versionId: number) =>
    del<void>(`/mrp/business-week-versions/${versionId}`),

  getCapacityLines: () =>
    get<CapacityFactoryLine[]>('/capacity-lines'),

  getBatchCreationVersions: () =>
    get<BatchCreationVersions>('/mrp/batch-creation-versions'),

  getForecastVersions: () =>
    get<{items: ForecastVersion[], total: number, page: number, pageSize: number}>('/forecast-versions?page=1&pageSize=100')
      .then(result => result.items || []),

  getCurrentForecastVersion: () =>
    get<{id?: number}>('/forecast-versions/current'),

  getForecastMaterials: (versionId: number) =>
    get<{items: ForecastMaterialSummary[], total: number}>(`/forecast-versions/${versionId}?page=1&pageSize=10000`)
      .then(result => result.items || []),
}
