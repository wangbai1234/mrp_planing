<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post, upload, download } from '../../api/client'
import type { InventorySnapshot, InventoryItem } from '../../api/types'
import { usePermission } from '../../composables/usePermission'

const { hasPermission } = usePermission()

const snapshots = ref<InventorySnapshot[]>([])
const loading = ref(false)
const importVisible = ref(false)
const file = ref<File | null>(null)
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const historyVisible = ref(false)
const selectedSnapshot = ref<InventorySnapshot | null>(null)

const selectedSnapshotId = ref<number | null>(null)
const snapshotItems = ref<InventoryItem[]>([])
const detailsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)

const listCurrentPage = ref(1)
const listPageSize = ref(3)
const listSearch = ref('')
const detailsSearch = ref('')

const currentStep = computed(() => stagingResult.value ? 1 : 0)

const listTotal = ref(0)

function inventoryQualityLabel(quality?: string) {
  if (quality === 'GOOD') return '良品'
  if (quality === 'DEFECTIVE') return '不良品'
  return '未维护'
}

function warehouseLabel(code?: string, name?: string) {
  if (name) return name
  return code || '-'
}

function inventoryQuantity(item: InventoryItem) {
  return item.inventoryQuantity ?? item.inventoryQty ?? item.shippingAvailableQty ?? 0
}

function formatBeijingTime(utcStr: string): string {
  if (!utcStr) return ''
  const d = new Date(utcStr)
  if (isNaN(d.getTime())) return utcStr
  const offset = 8 * 60
  const local = new Date(d.getTime() + offset * 60 * 1000)
  const y = local.getUTCFullYear()
  const m = String(local.getUTCMonth() + 1).padStart(2, '0')
  const day = String(local.getUTCDate()).padStart(2, '0')
  const h = String(local.getUTCHours()).padStart(2, '0')
  const min = String(local.getUTCMinutes()).padStart(2, '0')
  const s = String(local.getUTCSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}:${s}`
}

async function loadSnapshots() {
  loading.value = true
  try {
    const params = new URLSearchParams({ page: String(listCurrentPage.value), pageSize: String(listPageSize.value) })
    if (listSearch.value.trim()) params.set('keyword', listSearch.value.trim())
    const res = await get<any>(`/inventory-snapshots/page?${params}`)
    snapshots.value = res?.items || []
    listTotal.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error('加载列表失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

let detailsRequest = 0
async function loadSnapshotDetails(snapshotId: number, page = 1) {
  const request = ++detailsRequest
  detailsLoading.value = true
  selectedSnapshotId.value = snapshotId
  currentPage.value = page
  try {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize.value) })
    if (detailsSearch.value.trim()) params.set('keyword', detailsSearch.value.trim())
    const res = await get<any>(`/inventory-snapshots/${snapshotId}?${params}`)
    if (request !== detailsRequest) return
    snapshotItems.value = res?.items || []
    totalItems.value = res?.total || 0
  } catch (e: any) {
    if (request === detailsRequest) ElMessage.error('加载详情失败: ' + e.message)
  } finally {
    if (request === detailsRequest) detailsLoading.value = false
  }
}

function handleSnapshotClick(row: InventorySnapshot) {
  selectedSnapshot.value = row
  detailsSearch.value = ''
  loadSnapshotDetails(row.id)
  historyVisible.value = false
}

function handleListPageChange(page: number) {
  listCurrentPage.value = page
  loadSnapshots()
}

function handleListSizeChange(size: number) {
  listPageSize.value = size
  listCurrentPage.value = 1
  loadSnapshots()
}

function handlePageChange(page: number) {
  if (selectedSnapshotId.value) {
    loadSnapshotDetails(selectedSnapshotId.value, page)
  }
}

function handleSizeChange(size: number) {
  pageSize.value = size
  if (selectedSnapshotId.value) {
    loadSnapshotDetails(selectedSnapshotId.value, 1)
  }
}

function handleListSearch() {
  listCurrentPage.value = 1
  loadSnapshots()
}

function handleDetailsSearch() {
  currentPage.value = 1
  if (selectedSnapshotId.value) void loadSnapshotDetails(selectedSnapshotId.value, 1)
}

async function downloadTemplate() {
  try {
    await download('/inventory-imports/template', '库存_导入模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch (e: any) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

async function downloadErrorReport() {
  if (!stagingResult.value?.taskId) return
  try {
    await download(`/inventory-imports/${stagingResult.value.taskId}/errors`, '库存_错误数据.xlsx')
    ElMessage.success('错误报告下载成功')
  } catch (e: any) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = true
}

function handleDragLeave() {
  isDragOver.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragOver.value = false
  const droppedFile = e.dataTransfer?.files[0]
  if (droppedFile) {
    validateAndUpload(droppedFile)
  }
}

function handleFileInput(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    validateAndUpload(target.files[0])
  }
}

function validateAndUpload(f: File) {
  if (!f.name.endsWith('.xlsx')) {
    ElMessage.error('文件格式错误，请上传 .xlsx 格式的文件')
    return
  }
  if (f.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小超过 30MB 限制')
    return
  }
  file.value = f
  handleUpload(f)
}

async function handleUpload(f: File) {
  uploadLoading.value = true
  stagingResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', f)
    const res = await upload<any>('/inventory-imports/items', formData)
    stagingResult.value = res
    if (res?.errorRows > 0) {
      ElMessage.error(`解析完成: ${res.successRows} 行成功, ${res.errorRows} 行有错误，无法导入`)
    }
  } catch (e: any) {
    ElMessage.error('上传失败: ' + e.message)
    file.value = null
  } finally {
    uploadLoading.value = false
  }
}

function removeFile() {
  file.value = null
  stagingResult.value = null
}

async function handleConfirm() {
  if (!hasPermission('inventory:import_confirm') || !stagingResult.value?.taskId) return

  confirmLoading.value = true
  try {
    await post<any>(`/inventory-imports/items/${stagingResult.value.taskId}/confirm`)
    ElMessage.success('导入成功')
    handleClose()
    listCurrentPage.value = 1
    listSearch.value = ''
    await loadSnapshots()
    if (snapshots.value[0]) handleSnapshotClick(snapshots.value[0])
  } catch (e: any) {
    ElMessage.error('确认失败: ' + e.message)
  } finally {
    confirmLoading.value = false
  }
}

function handleClose() {
  importVisible.value = false
  stagingResult.value = null
  file.value = null
  uploadLoading.value = false
}

async function openImportDialog() {
  stagingResult.value = null
  file.value = null
  importVisible.value = true
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(async () => {
  await loadSnapshots()
  if (snapshots.value[0]) handleSnapshotClick(snapshots.value[0])
})
</script>

<template>
  <div class="inventory-page">
    <div class="page-header">
      <h1 class="page-title">库存管理</h1>
      <div class="page-actions"><el-button @click="historyVisible = true">历史版本</el-button><el-button v-if="hasPermission('inventory:import')" type="primary" @click="openImportDialog"><el-icon><Upload /></el-icon>导入</el-button></div>
    </div>

    <div class="table-card" v-loading="detailsLoading || loading">
      <div class="table-title-bar">
        <span class="table-title-text">{{ selectedSnapshot ? `库存快照 #${selectedSnapshot.id} · ${selectedSnapshot.snapshotDate}` : '库存数据' }}</span>
        <el-input v-model="detailsSearch" placeholder="搜索仓库 / 料号 / 名称 / 供应商" clearable style="width:220px" @keyup.enter="handleDetailsSearch" @clear="handleDetailsSearch" />
      </div>
      <el-table :data="snapshotItems" size="default" border max-height="600" style="width: 100%;">
        <el-table-column label="仓库" width="120" fixed>
          <template #default="{ row }">{{ warehouseLabel(row.warehouseCode, row.warehouseName) }}</template>
        </el-table-column>
        <el-table-column prop="inventoryCategory" label="物料类别" width="120" fixed />
        <el-table-column prop="materialId" label="物料料号" width="150" fixed />
        <el-table-column prop="inventoryQuality" label="品质" width="90">
          <template #default="{ row }">
            <el-tag :type="row.inventoryQuality === 'GOOD' ? 'success' : 'danger'" size="small">
              {{ inventoryQualityLabel(row.inventoryQuality) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" width="150" />
        <el-table-column prop="supplierName" label="供应商名称" width="130" />
        <el-table-column label="库存数量" min-width="120" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #1677FF;">{{ inventoryQuantity(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" />
      </el-table>
      <div v-if="totalItems > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalItems"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog v-model="historyVisible" title="历史版本" width="720px">
      <div class="filter-bar"><el-input v-model="listSearch" placeholder="搜索ID / 日期 / 状态" clearable style="width:220px" @clear="handleListSearch" @keyup.enter="handleListSearch" /></div>
      <el-table :data="snapshots" border v-loading="loading" @row-click="handleSnapshotClick" highlight-current-row>
        <el-table-column prop="id" label="快照" width="90"><template #default="{row}">#{{ row.id }}</template></el-table-column><el-table-column prop="snapshotDate" label="快照日期" width="140" /><el-table-column prop="status" label="状态" width="110"><template #default="{row}">{{ row.status === 'IMPORTED' ? '已导入' : row.status }}</template></el-table-column><el-table-column label="导入时间"><template #default="{row}">{{ formatBeijingTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click.stop="handleSnapshotClick(row)">查看</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && snapshots.length === 0" description="暂无历史版本" />
      <el-pagination v-if="listTotal" v-model:current-page="listCurrentPage" v-model:page-size="listPageSize" :total="listTotal" :page-sizes="[3,10,20]" layout="total, sizes, prev, pager, next" @size-change="handleListSizeChange" @current-change="handleListPageChange" />
    </el-dialog>

    <el-dialog v-model="importVisible" :width="760" :before-close="handleClose" class="import-dialog" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-header">
          <div class="dialog-title">库存批量导入</div>
        </div>
      </template>

      <div class="step-hint">
        <span class="step-label">步骤 {{ currentStep + 1 }}/2：</span>
        <span v-if="currentStep === 0">可下载模板获取数据，根据指引编辑后再上传</span>
        <span v-else>请确认上传的文件信息</span>
        <span v-if="currentStep === 0" class="download-link" @click="downloadTemplate">下载模板</span>
      </div>

      <div class="upload-section">
        <div
          v-if="!file && !uploadLoading"
          class="upload-area"
          :class="{ 'is-dragover': isDragOver }"
          @dragover="handleDragOver"
          @dragleave="handleDragLeave"
          @drop="handleDrop"
          @click="fileInput?.click()"
        >
          <div class="upload-icon">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
              <path d="M28 4H12C10.9391 4 9.92172 4.42143 9.17157 5.17157C8.42143 5.92172 8 6.93913 8 8V40C8 41.0609 8.42143 42.0783 9.17157 42.8284C9.92172 43.5786 10.9391 44 12 44H36C37.0609 44 38.0783 43.5786 38.8284 42.8284C39.5786 42.0783 40 41.0609 40 40V16L28 4Z" stroke="#1677FF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M28 4V16H40" stroke="#1677FF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M18 24H30" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
              <path d="M18 32H24" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="upload-main-text">拖拽文件至此，或<span class="upload-link">选择文件上传</span></div>
          <div class="upload-sub-text">仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件</div>
          <input ref="fileInput" type="file" accept=".xlsx" style="display: none" @change="handleFileInput">
        </div>

        <div v-if="uploadLoading" class="upload-area uploading">
          <el-icon class="is-loading" :size="32" color="#1677FF"><Loading /></el-icon>
          <div class="upload-main-text" style="margin-top: 12px">正在解析文件...</div>
        </div>

        <div v-if="file && !uploadLoading" class="file-info-card">
          <div class="file-icon">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
              <rect width="40" height="40" rx="8" fill="#E8F3FF"/>
              <path d="M22 10H14C12.8954 10 12 10.8954 12 12V28C12 29.1046 12.8954 30 14 30H26C27.1046 30 28 29.1046 28 28V16L22 10Z" stroke="#1677FF" stroke-width="1.5"/>
              <path d="M22 10V16H28" stroke="#1677FF" stroke-width="1.5"/>
            </svg>
          </div>
          <div class="file-details">
            <div class="file-name">{{ file.name }}</div>
            <div class="file-size">{{ formatFileSize(file.size) }}</div>
          </div>
          <div class="file-remove" @click="removeFile">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M12 4L4 12M4 4L12 12" stroke="#86909C" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </div>
        </div>

        <div v-if="stagingResult" class="parse-summary">
          <div class="summary-item">
            <span class="summary-label">总行数</span>
            <span class="summary-value">{{ stagingResult.totalRows }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">有效行</span>
            <span class="summary-value success">{{ stagingResult.successRows }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">错误行</span>
            <span class="summary-value" :class="{ error: stagingResult.errorRows > 0 }">{{ stagingResult.errorRows }}</span>
          </div>
        </div>

        <div v-if="stagingResult && stagingResult.errorRows > 0" class="error-alert">
          <el-alert type="error" :closable="false" show-icon>
            <template #title>
              <span>发现 {{ stagingResult.errorRows }} 条错误数据，无法导入</span>
            </template>
            <template #default>
              <div class="error-actions">
                <span>请下载错误报告，修正后重新导入</span>
                <el-button type="primary" size="small" @click="downloadErrorReport">
                  下载错误数据
                </el-button>
              </div>
            </template>
          </el-alert>
        </div>

        <div v-if="stagingResult && stagingResult.previewRows && stagingResult.previewRows.length > 0" class="preview-section">
          <div class="preview-title">数据预览</div>
          <el-table :data="stagingResult.previewRows" size="small" border max-height="300">
            <el-table-column prop="type" label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.type === 'error' ? 'danger' : 'success'" size="small" effect="plain">
                  {{ row.type === 'error' ? '错误' : '成功' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="row" label="行号" width="60" />
            <el-table-column prop="warehouseCode" label="仓库编码" width="130" />
            <el-table-column prop="inventoryCategory" label="物料类别" width="100" />
            <el-table-column prop="materialId" label="物料料号" width="130" />
            <el-table-column prop="inventoryQuality" label="品质" width="80">
              <template #default="{ row }">{{ inventoryQualityLabel(row.inventoryQuality) }}</template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" width="120" />
            <el-table-column label="库存数量" width="100" align="right"><template #default="{row}">{{ row.inventoryQuantity ?? row.inventoryQty ?? row.shippingAvailableQty ?? 0 }}</template></el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" />
            <el-table-column prop="message" label="错误信息" min-width="150" />
          </el-table>
        </div>
      </div>

      <div class="rules-section">
        <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
        <div class="rules-list">
          <div class="rule-item">1. 仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件</div>
          <div class="rule-item">2. 仓库编码、物料料号、库存数量必填；品质依据已维护仓库属性自动识别，无需在 Excel 中填写品质列</div>
          <div class="rule-item">3. 料号必须在物料管理表中存在</div>
          <div class="rule-item">4. 不能存在合并单元格</div>
          <div class="rule-item">5. 同一文件可包含多个仓库；仓库须已启用并确认良品属性，按仓库编码分别汇总</div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose" class="btn-cancel">取消</el-button>
          <el-button
            v-if="hasPermission('inventory:import_confirm')"
            type="primary"
            @click="handleConfirm"
            :loading="confirmLoading"
            :disabled="!stagingResult || stagingResult.errorRows > 0"
            class="btn-import"
          >
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.inventory-page {
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

.details-card {
  margin-top: 16px;
}

.table-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-title-text {
  font-weight: 600;
  font-size: 15px;
  color: #1F2329;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e6eb;
}

.total-text {
  font-size: 14px;
  color: #86909C;
}

:deep(.import-dialog) {
  border-radius: 12px;
}

:deep(.import-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin: 0;
}

:deep(.import-dialog .el-dialog__body) {
  padding: 16px 24px;
}

:deep(.import-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid #E5E6EB;
}

.dialog-title {
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.step-hint {
  font-size: 14px;
  color: #4E5969;
  margin-bottom: 16px;
}

.step-label {
  color: #1F2329;
  font-weight: 500;
}

.download-link {
  color: #1677FF;
  margin-left: 8px;
  cursor: pointer;
  font-weight: 500;
}

.download-link:hover {
  text-decoration: underline;
}

.upload-section {
  margin-bottom: 20px;
}

.upload-area {
  border: 1px dashed #C9DDFF;
  border-radius: 10px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #FAFBFC;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.upload-area:hover,
.upload-area.is-dragover {
  border-color: #1677FF;
  background: #F2F7FF;
}

.upload-area.uploading {
  cursor: default;
}

.upload-icon {
  margin-bottom: 16px;
}

.upload-main-text {
  font-size: 14px;
  color: #4E5969;
  margin-bottom: 8px;
}

.upload-link {
  color: #1677FF;
  font-weight: 500;
}

.upload-sub-text {
  font-size: 12px;
  color: #86909C;
}

.file-info-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #F7F8FA;
  border-radius: 8px;
  border: 1px solid #E5E6EB;
}

.file-details {
  flex: 1;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
}

.file-size {
  font-size: 12px;
  color: #86909C;
  margin-top: 2px;
}

.file-remove {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.file-remove:hover {
  background: #E5E6EB;
}

.parse-summary {
  display: flex;
  gap: 32px;
  margin-top: 12px;
  padding: 12px 16px;
  background: #F7F8FA;
  border-radius: 8px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 12px;
  color: #86909C;
}

.summary-value {
  font-size: 16px;
  font-weight: 600;
  color: #1F2329;
}

.summary-value.success {
  color: #00B42A;
}

.summary-value.error {
  color: #F53F3F;
}

.rules-section {
  margin-top: 4px;
}

.rules-title {
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
  margin-bottom: 12px;
}

.rule-item {
  font-size: 13px;
  color: #4E5969;
  line-height: 28px;
}

.preview-section {
  margin-top: 16px;
  border: 1px solid #E5E6EB;
  border-radius: 8px;
  padding: 12px;
}

.preview-title {
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
  margin-bottom: 12px;
}

.error-alert {
  margin-top: 16px;
}

.error-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-cancel {
  border-radius: 8px;
}

.btn-import {
  border-radius: 8px;
  min-width: 100px;
}
</style>
