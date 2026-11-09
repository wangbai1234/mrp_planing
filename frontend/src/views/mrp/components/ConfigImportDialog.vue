<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import type { ConfigImportPreview } from '../../../api/mrp'

const props = defineProps<{
  title: string
  rules: string[]
  columns: string[]
  preview: (file: File) => Promise<ConfigImportPreview>
  commit: (file: File) => Promise<{ inserted: number; updated: number }>
  template: () => Promise<void>
  errors: (file: File) => Promise<void>
}>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ imported: [] }>()

const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragOver = ref(false)
const parsing = ref(false)
const importing = ref(false)
const result = ref<ConfigImportPreview | null>(null)

const step = computed(() => (result.value ? 1 : 0))

function reset() {
  file.value = null
  result.value = null
  parsing.value = false
  importing.value = false
  if (fileInput.value) fileInput.value.value = ''
}
function close() { visible.value = false }
function formatSize(size: number) { return size < 1024 * 1024 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB` }

async function downloadTemplate() {
  try { await props.template(); ElMessage.success('模板下载成功') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '模板下载失败') }
}
async function downloadErrors() {
  if (!file.value) return
  try { await props.errors(file.value); ElMessage.success('错误报告下载成功') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '下载失败') }
}

function accept(candidate: File) {
  if (!candidate.name.toLowerCase().endsWith('.xlsx')) { ElMessage.error('文件格式错误，请上传 .xlsx 格式的文件'); return }
  if (candidate.size > 30 * 1024 * 1024) { ElMessage.error('文件大小超过 30MB 限制'); return }
  file.value = candidate
  parse(candidate)
}
function onDrop(event: DragEvent) {
  event.preventDefault()
  isDragOver.value = false
  const dropped = event.dataTransfer?.files[0]
  if (dropped) accept(dropped)
}
function onPick(event: Event) {
  const picked = (event.target as HTMLInputElement).files?.[0]
  if (picked) accept(picked)
}
async function parse(candidate: File) {
  parsing.value = true
  result.value = null
  try { result.value = await props.preview(candidate) }
  catch (error) { file.value = null; ElMessage.error(error instanceof Error ? error.message : '解析失败') }
  finally { parsing.value = false }
}
async function confirm() {
  if (!file.value || !result.value || result.value.errorRows > 0) return
  importing.value = true
  try {
    const saved = await props.commit(file.value)
    ElMessage.success(`导入完成：新增 ${saved.inserted} 行，更新 ${saved.updated} 行`)
    visible.value = false
    emit('imported')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导入失败') }
  finally { importing.value = false }
}
</script>

<template>
  <el-dialog v-model="visible" :width="760" class="import-dialog" :close-on-click-modal="false" @closed="reset">
    <template #header><div class="dialog-title">{{ title }}</div></template>
    <div class="step-hint">
      <span class="step-label">步骤 {{ step + 1 }}/2：</span>
      <span v-if="step === 0">可下载模板获取数据，根据指引编辑后再上传</span>
      <span v-else>请确认上传的文件信息</span>
      <span v-if="step === 0" class="download-link" @click="downloadTemplate">下载模板</span>
    </div>
    <div class="upload-section">
      <div v-if="!file && !parsing" class="upload-area" :class="{ 'is-dragover': isDragOver }"
           @dragover.prevent="isDragOver = true" @dragleave="isDragOver = false" @drop="onDrop" @click="fileInput?.click()">
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
        <input ref="fileInput" type="file" accept=".xlsx" style="display: none" @change="onPick">
      </div>
      <div v-if="parsing" class="upload-area uploading">
        <el-icon class="is-loading" :size="32" color="#1677FF"><Loading /></el-icon>
        <div class="upload-main-text">正在解析文件...</div>
      </div>
      <div v-if="file && !parsing" class="file-info-card">
        <div class="file-details"><div class="file-name">{{ file.name }}</div><div class="file-size">{{ formatSize(file.size) }}</div></div>
        <div class="file-remove" @click="reset">移除</div>
      </div>
      <div v-if="result" class="parse-summary">
        <div class="summary-item"><span class="summary-label">总行数</span><span class="summary-value">{{ result.totalRows }}</span></div>
        <div class="summary-item"><span class="summary-label">有效行</span><span class="summary-value success">{{ result.successRows }}</span></div>
        <div class="summary-item"><span class="summary-label">错误行</span><span class="summary-value" :class="{ error: result.errorRows > 0 }">{{ result.errorRows }}</span></div>
      </div>
      <div v-if="result && result.errorRows > 0" class="error-alert">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>发现 {{ result.errorRows }} 条错误数据，无法导入</template>
          <template #default><div class="error-actions"><span>请下载错误报告，修正后重新导入</span><el-button type="primary" size="small" @click="downloadErrors">下载错误数据</el-button></div></template>
        </el-alert>
      </div>
      <div v-if="result && result.previewRows.length" class="preview-section">
        <div class="preview-title">数据预览（前10行）</div>
        <el-table :data="result.previewRows" size="small" border max-height="300">
          <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.type === 'error' ? 'danger' : 'success'" size="small" effect="plain">{{ row.type === 'error' ? '错误' : '成功' }}</el-tag></template></el-table-column>
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column v-for="(column, index) in columns" :key="column" :label="column" min-width="120"><template #default="{ row }">{{ row.cells[index] }}</template></el-table-column>
          <el-table-column prop="message" label="错误信息" min-width="180" />
        </el-table>
      </div>
    </div>
    <div class="rules-section">
      <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
      <div v-for="(rule, index) in rules" :key="rule" class="rule-item">{{ index + 1 }}. {{ rule }}</div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!result || result.errorRows > 0" @click="confirm">开始导入</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.import-dialog) { border-radius: 12px; }
.dialog-title { font-size: 20px; font-weight: 600; color: #1F2329; }
.step-hint { font-size: 14px; color: #4E5969; margin-bottom: 16px; }
.step-label { color: #1F2329; font-weight: 500; }
.download-link { color: #1677FF; margin-left: 8px; cursor: pointer; font-weight: 500; }
.upload-section { margin-bottom: 20px; }
.upload-area { border: 1px dashed #C9DDFF; border-radius: 10px; padding: 40px; text-align: center; cursor: pointer; background: #FAFBFC; min-height: 180px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.upload-area:hover, .upload-area.is-dragover { border-color: #1677FF; background: #F2F7FF; }
.upload-main-text { font-size: 14px; color: #4E5969; margin: 12px 0 8px; }
.upload-link { color: #1677FF; font-weight: 500; }
.upload-sub-text, .file-size { font-size: 12px; color: #86909C; }
.file-info-card { display: flex; align-items: center; gap: 12px; padding: 16px; background: #F7F8FA; border-radius: 8px; border: 1px solid #E5E6EB; }
.file-details { flex: 1; }
.file-name { font-size: 14px; font-weight: 500; color: #1F2329; }
.file-remove { cursor: pointer; color: #86909C; }
.parse-summary { display: flex; gap: 32px; margin-top: 12px; padding: 12px 16px; background: #F7F8FA; border-radius: 8px; }
.summary-item { display: flex; flex-direction: column; gap: 4px; }
.summary-label { font-size: 12px; color: #86909C; }
.summary-value { font-size: 16px; font-weight: 600; color: #1F2329; }
.summary-value.success { color: #00B42A; }
.summary-value.error { color: #F53F3F; }
.preview-section { margin-top: 16px; border: 1px solid #E5E6EB; border-radius: 8px; padding: 12px; }
.preview-title { font-size: 14px; font-weight: 500; color: #1F2329; margin-bottom: 12px; }
.error-alert { margin-top: 16px; }
.error-actions { display: flex; align-items: center; gap: 16px; margin-top: 8px; }
.rules-title { font-size: 14px; font-weight: 500; color: #1F2329; margin-bottom: 12px; }
.rule-item { font-size: 13px; color: #4E5969; line-height: 28px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
</style>
