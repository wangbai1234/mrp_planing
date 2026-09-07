<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { upload, post } from '../../api/client'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const file = ref<File | null>(null)
const snapshotDate = ref('')
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

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
}

async function handleUpload() {
  if (!file.value || !snapshotDate.value) {
    ElMessage.warning('请先选择快照日期和文件')
    return
  }
  uploadLoading.value = true
  stagingResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', file.value)
    formData.append('snapshotDate', snapshotDate.value)
    const res = await upload<any>('/inventory-imports', formData)
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
  if (!stagingResult.value?.taskId || !snapshotDate.value) return
  
  confirmLoading.value = true
  try {
    const res = await post<any>(`/inventory-imports/${stagingResult.value.taskId}/confirm?snapshotDate=${snapshotDate.value}`)
    if (res.success) {
      ElMessage.success('导入成功')
      file.value = null
      stagingResult.value = null
    }
  } catch (e: any) {
    ElMessage.error('确认失败: ' + e.message)
  } finally {
    confirmLoading.value = false
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => setCrumb('数据管理', '库存快照'))
</script>

<template>
  <div class="inventory-page">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">库存快照</h1>
      <el-button type="primary" @click="handleUpload" :loading="uploadLoading" :disabled="!file || !snapshotDate">
        <el-icon><Upload /></el-icon>导入
      </el-button>
    </div>

    <!-- 配置区域 -->
    <div class="config-card">
      <el-form label-width="100px" style="max-width: 500px">
        <el-form-item label="快照日期" required>
          <el-date-picker v-model="snapshotDate" type="date" value-format="YYYY-MM-DD" placeholder="选择快照日期" style="width: 100%" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 上传区域 -->
    <div class="upload-card">
      <div class="upload-title">上传库存文件</div>
      
      <!-- 没有文件时 -->
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
            <path d="M28 4H12C10.9391 4 9.92172 4.42143 9.17157 5.17157C8.42143 5.92172 8 6.93913 8 8V40C8 41.0609 8.42143 42.0783 9.17157 42.8284C9.92172 43.5786 10.9391 44 12 44H36C37.0609 44 38.0783 43.5786 38.8284 42.8284C39.5786 42.0783 40 41.0609 40 40V16L28 4Z" stroke="#1677FF" stroke-width="2.5"/>
            <path d="M28 4V16H40" stroke="#1677FF" stroke-width="2.5"/>
            <path d="M18 24H30" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
            <path d="M18 32H24" stroke="#1677FF" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <div class="upload-main-text">拖拽文件至此，或<span class="upload-link">选择文件上传</span></div>
        <div class="upload-sub-text">仅支持 .xlsx 格式，文件大小不超过 30MB</div>
        <input ref="fileInput" type="file" accept=".xlsx" style="display: none" @change="handleFileInput">
      </div>

      <!-- 上传中 -->
      <div v-if="uploadLoading" class="upload-area uploading">
        <el-icon class="is-loading" :size="32" color="#1677FF"><Loading /></el-icon>
        <div class="upload-main-text" style="margin-top: 12px">正在解析文件...</div>
      </div>

      <!-- 已选择文件 -->
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

      <!-- 解析结果 -->
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

      <!-- 操作按钮 -->
      <div v-if="stagingResult" class="action-bar">
        <el-button @click="removeFile">重新上传</el-button>
        <el-button 
          type="primary" 
          @click="handleConfirm" 
          :loading="confirmLoading"
          :disabled="stagingResult.errorRows > 0"
        >
          确认导入
        </el-button>
      </div>
    </div>
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

.page-desc {
  margin-top: 4px;
  font-size: 14px;
  color: #86909C;
}

.config-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.upload-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.upload-title {
  font-size: 16px;
  font-weight: 500;
  color: #1F2329;
  margin-bottom: 16px;
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
  margin-top: 16px;
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

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #E5E6EB;
}
</style>
