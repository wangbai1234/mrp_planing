<script setup lang="ts">
import { ref, inject, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { get, upload, download } from '../../api/client'
import type { ForecastVersion } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const versions = ref<ForecastVersion[]>([])
const loading = ref(false)
const importVisible = ref(false)
const file = ref<File | null>(null)
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const currentStep = computed(() => stagingResult.value ? 1 : 0)

async function loadVersions() {
  loading.value = true
  try {
    const res = await get<any>('/forecast-versions')
    versions.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载版本失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function downloadTemplate() {
  try {
    await download('/forecast-imports/template', '经营计划_导入模板.xlsx')
    ElMessage.success('模板下载成功')
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
    const res = await upload<any>('/forecast-imports', formData)
    stagingResult.value = res.data
    if (res.data?.errorRows > 0) {
      ElMessage.warning(`解析完成: ${res.data.successRows} 行成功, ${res.data.errorRows} 行有错误`)
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
    const res = await get<any>(`/import-tasks/${stagingResult.value.taskId}`)
    if (res.success) {
      ElMessage.success('导入成功')
      handleClose()
      await loadVersions()
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
  setCrumb('数据管理', '经营计划')
  loadVersions()
})
</script>

<template>
  <div class="forecast-page">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">经营计划</h1>
      <el-button type="primary" @click="openImportDialog">
        <el-icon><Upload /></el-icon>导入
      </el-button>
    </div>

    <!-- 版本列表 -->
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <span style="font-weight: 600">历史版本</span>
      </template>
      <el-table :data="versions" size="default" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IMPORTED' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 'IMPORTED' ? '已导入' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="导入时间" />
      </el-table>
      <el-empty v-if="!loading && versions.length === 0" description="暂无经营计划版本，请先导入" />
    </el-card>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" :width="760" :before-close="handleClose" class="import-dialog" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-header">
          <div class="dialog-title">经营计划批量导入</div>
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
      </div>

      <div class="rules-section">
        <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
        <div class="rules-list">
          <div class="rule-item">1. 仅支持上传小于 30 MB 且不超过 30,000 条记录的 .xlsx 文件</div>
          <div class="rule-item">2. 所有必填项不能为空，否则将无法完成导入</div>
          <div class="rule-item">3. 不能存在合并单元格</div>
          <div class="rule-item">4. 请确保所有数据信息准确无误，错误的数据将无法被导入</div>
          <div class="rule-item">5. 导入过程可能需要一些时间，具体时长取决于导入数据量，请耐心等待</div>
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
.forecast-page {
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

.page-desc {
  margin-top: 4px;
  font-size: 14px;
  color: #86909C;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

/* 导入弹窗样式 */
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
