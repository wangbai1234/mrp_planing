<script setup lang="ts">
import { ref, inject, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post, upload, download } from '../../api/client'
import type { InventorySnapshot, InventoryItem } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const snapshots = ref<InventorySnapshot[]>([])
const loading = ref(false)
const importVisible = ref(false)
const file = ref<File | null>(null)
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

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

const filteredSnapshots = computed(() => {
  const keyword = listSearch.value.trim().toLowerCase()
  if (!keyword) return snapshots.value
  return snapshots.value.filter(s =>
    String(s.id).includes(keyword) ||
    (s.snapshotDate || '').toLowerCase().includes(keyword) ||
    (s.status || '').toLowerCase().includes(keyword)
  )
})

const listTotal = computed(() => filteredSnapshots.value.length)

const pagedSnapshots = computed(() => {
  const start = (listCurrentPage.value - 1) * listPageSize.value
  return filteredSnapshots.value.slice(start, start + listPageSize.value)
})

const filteredItems = computed(() => {
  const keyword = detailsSearch.value.trim().toLowerCase()
  if (!keyword) return snapshotItems.value
  return snapshotItems.value.filter(item =>
    (item.materialId || '').toLowerCase().includes(keyword) ||
    (item.materialName || '').toLowerCase().includes(keyword) ||
    (item.supplierName || '').toLowerCase().includes(keyword) ||
    (item.projectModel || '').toLowerCase().includes(keyword) ||
    (item.inventoryCategory || '').toLowerCase().includes(keyword)
  )
})

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
    const res = await get<any>('/inventory-snapshots')
    snapshots.value = res || []
  } catch (e: any) {
    ElMessage.error('加载列表失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadSnapshotDetails(snapshotId: number, page = 1) {
  detailsLoading.value = true
  selectedSnapshotId.value = snapshotId
  currentPage.value = page
  detailsSearch.value = ''
  try {
    const res = await get<any>(`/inventory-snapshots/${snapshotId}?page=${page}&pageSize=${pageSize.value}`)
    snapshotItems.value = res?.items || []
    totalItems.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error('加载详情失败: ' + e.message)
  } finally {
    detailsLoading.value = false
  }
}

function handleSnapshotClick(row: InventorySnapshot) {
  loadSnapshotDetails(row.id)
}

function handleListPageChange(page: number) {
  listCurrentPage.value = page
}

function handleListSizeChange(size: number) {
  listPageSize.value = size
  listCurrentPage.value = 1
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
}

function handleDetailsSearch() {
  currentPage.value = 1
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
    stagingResult.value = res.data
    if (res?.errorRows > 0) {
      ElMessage.warning(`解析完成: ${res.successRows} 行成功, ${res.errorRows} 行有错误`)
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
  if (!stagingResult.value?.taskId) return

  confirmLoading.value = true
  try {
    const res = await post<any>(`/inventory-imports/items/${stagingResult.value.taskId}/confirm`)
    if (res.success) {
      ElMessage.success('导入成功')
      handleClose()
      await loadSnapshots()
    }
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

function openImportDialog() {
  stagingResult.value = null
  file.value = null
  importVisible.value = true
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  setCrumb('数据管理', '库存管理')
  loadSnapshots()
})
</script>

<template>
  <div class="inventory-page">
    <div class="page-header">
      <h1 class="page-title">库存管理</h1>
      <el-button type="primary" @click="openImportDialog">
        <el-icon><Upload /></el-icon>导入
      </el-button>
    </div>

    <div class="table-card" v-loading="loading">
      <div class="table-title-bar">
        <span class="table-title-text">历史记录</span>
        <el-input
          v-model="listSearch"
          placeholder="搜索ID / 日期 / 状态"
          clearable
          style="width: 220px"
          @input="handleListSearch"
        />
      </div>
      <el-table :data="pagedSnapshots" size="default" border @row-click="handleSnapshotClick" highlight-current-row>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="snapshotDate" label="快照日期" width="140" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IMPORTED' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 'IMPORTED' ? '已导入' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="导入时间">
          <template #default="{ row }">
            {{ formatBeijingTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && pagedSnapshots.length === 0" description="暂无库存数据，请先导入" />
      <div v-if="listTotal > 0" class="pagination-bar">
        <span class="total-text">共 {{ listTotal }} 条</span>
        <el-pagination
          v-model:current-page="listCurrentPage"
          v-model:page-size="listPageSize"
          :total="listTotal"
          :page-sizes="[3, 10, 20]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleListSizeChange"
          @current-change="handleListPageChange"
        />
      </div>
    </div>

    <div v-if="selectedSnapshotId" class="table-card details-card" v-loading="detailsLoading">
      <div class="table-title-bar">
        <span class="table-title-text">快照详情</span>
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-input
            v-model="detailsSearch"
            placeholder="搜索料号 / 物料名称 / 供应商"
            clearable
            style="width: 220px"
            @input="handleDetailsSearch"
          />
          <el-button size="small" @click="selectedSnapshotId = null">关闭</el-button>
        </div>
      </div>
      <el-table :data="filteredItems" size="default" border max-height="600" style="width: 100%;">
        <el-table-column prop="productLine" label="产品线" width="100" fixed />
        <el-table-column prop="inventoryCategory" label="库存分类" width="100" fixed />
        <el-table-column prop="materialId" label="料号" width="130" fixed />
        <el-table-column prop="projectModel" label="项目型号" width="120" />
        <el-table-column prop="materialName" label="物料名称" width="150" />
        <el-table-column prop="supplierName" label="供应商名称" width="130" />
        <el-table-column prop="productMode" label="产品模式" width="100" />
        <el-table-column prop="odmSupplierQty" label="ODM供应商仓" width="110" align="right" />
        <el-table-column prop="xa400Qty" label="XA400咪哈成品仓" width="130" align="right" />
        <el-table-column prop="xa378Qty" label="XA378永惠成品仓" width="130" align="right" />
        <el-table-column prop="xa226Qty" label="XA226惠州仓" width="110" align="right" />
        <el-table-column prop="shippingAvailableQty" label="出货可用库存" width="110" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #1677FF;">{{ row.shippingAvailableQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="classification" label="分类" width="80" />
        <el-table-column prop="barcode" label="产品条形码" width="130" />
        <el-table-column prop="remark" label="备注" width="120" />
        <el-table-column prop="orderPendingQty" label="订单未交数量" width="110" align="right" />
        <el-table-column prop="salesStatus" label="销售状态" width="90" />
        <el-table-column prop="parentRecord" label="父记录" width="100" />
      </el-table>
      <el-empty v-if="!detailsLoading && filteredItems.length === 0" description="暂无数据" />
      <div v-if="totalItems > 0" class="pagination-bar">
        <span class="total-text">共 {{ totalItems }} 条</span>
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
            <el-table-column prop="inventoryCategory" label="库存分类" width="90" />
            <el-table-column prop="materialId" label="料号" width="120" />
            <el-table-column prop="projectModel" label="项目型号" width="100" />
            <el-table-column prop="materialName" label="物料名称" width="120" />
            <el-table-column prop="supplierName" label="供应商" width="100" />
            <el-table-column prop="odmSupplierQty" label="ODM仓" width="70" align="right" />
            <el-table-column prop="xa400Qty" label="XA400" width="60" align="right" />
            <el-table-column prop="xa378Qty" label="XA378" width="60" align="right" />
            <el-table-column prop="xa226Qty" label="XA226" width="60" align="right" />
            <el-table-column prop="shippingAvailableQty" label="可用库存" width="70" align="right" />
            <el-table-column prop="message" label="错误信息" min-width="150" />
          </el-table>
        </div>
      </div>

      <div class="rules-section">
        <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
        <div class="rules-list">
          <div class="rule-item">1. 仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件</div>
          <div class="rule-item">2. 必填项（库存分类、料号、供应商名称）不能为空</div>
          <div class="rule-item">3. 料号必须在物料管理表中存在</div>
          <div class="rule-item">4. 不能存在合并单元格</div>
          <div class="rule-item">5. 项目型号、物料名称根据料号自动查询，出货可用库存自动计算</div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose" class="btn-cancel">取消</el-button>
          <el-button
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
