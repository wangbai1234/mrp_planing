export interface ApiResponse<T> {
  success: boolean
  data: T
  error?: {
    code: string
    message: string
    details?: unknown
  }
  traceId?: string
  timestamp: string
}

export interface PlanVersion {
  id: number
  versionNo: number
  factoryCode: string
  forecastVersionId: number
  inventorySnapshotId?: number
  shipmentBatchId?: number
  capacityVersionId?: number
  ruleVersion: string
  currentWeekStart: string
  inputChecksum?: string
  resultChecksum?: string
  status: string
  autoRecalcForecast: boolean
  autoRecalcInventory: boolean
  splitCategories?: string[] | null
  splitCategoryNames?: string | null
  createdBy?: number
  createdAt: string
  updatedAt: string
}

export interface PlanGridRow {
  planVersionId: number
  materialId: string
  materialName: string
  rootMaterialCode?: string
  rootMaterialName?: string
  factoryCode: string
  weekStartDate: string
  physicalMonth: string
  sourceMonth: string
  slot: number
  isCarry: boolean
  isLocked: boolean
  systemQuantity: number
  manualQuantity: number | ''
  effectiveQuantity: number
  capacityExceeded: boolean
  capacityExcessQty: number
}

export interface CategoryTreeNode {
  id: number
  code: string
  name: string
  level: number
  enabled: boolean
  children?: CategoryTreeNode[]
}

export interface ForecastVersion {
  id: number
  versionNo: number
  fileName?: string
  status: string
  createdAt: string
}

export interface CapacityLine {
  id?: number
  versionId?: number
  factoryCode: string
  lineCode: string
  lineName?: string
  weeklyCapacity: number
  effectiveDate?: string
  isActive: boolean
  remark?: string
}

export interface ImportTask {
  id: number
  taskType: string
  status: string
  resultResourceId?: number
}

export interface CalcTask {
  id: number
  status: string
  resultResourceId?: number
  errorCode?: string
  errorMessage?: string
}

export interface ParseResult<T> {
  rows: T[]
  errors: ParseError[]
  recognizedMonths: string[]
  totalRows: number
  successRows: number
  errorRows: number
}

export interface ParseError {
  rowNumber: number
  column: string
  originalValue: string
  errorCode: string
  message: string
}

export interface InventorySnapshot {
  id: number
  snapshotDate: string
  fileName?: string
  status: string
  createdAt: string
}

export interface InventoryItem {
  id: number
  productLine: string
  inventoryCategory: string
  materialId: string
  projectModel: string
  materialName: string
  supplierName: string
  productMode: string
  odmSupplierQty: number
  xa400Qty: number
  xa378Qty: number
  xa226Qty: number
  shippingAvailableQty: number
  classification: string
  barcode: string
  remark: string
  orderPendingQty: number
  salesStatus: string
  parentRecord: string
}
