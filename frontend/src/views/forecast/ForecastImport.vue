<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put, upload, download } from '../../api/client'
import { usePermission } from '../../composables/usePermission'
import type { ForecastVersion } from '../../api/types'
import PolicyMaterialPicker from '../mrp/components/PolicyMaterialPicker.vue'
import { mrpApi } from '../../api/mrp'
import AuditLogViewer from '../../components/AuditLogViewer.vue'
import { getAuditLogs, type AuditLogRecord } from '../../api/audit'

// 版本列表相关
const versions = ref<ForecastVersion[]>([])
const loading = ref(false)
const factoryOptions = ref<string[]>([])
const page = ref(1)
const pageSize = ref(3)
const total = ref(0)
const keyword = ref('')
const versionHistoryVisible = ref(false)
const versionInfoVisible = ref(false)
const selectedVersion = ref<ForecastVersion | null>(null)
const { hasPermission } = usePermission()
interface DraftLine {
  id: number; factoryCode: string; materialId: string; materialName: string
  formType: string; mold: string; status: string; months: Record<string, string>
  prioritySeq: number; isDeleted: number; total: string; updatedBy?: number; updatedAt?: string
}
interface Draft { id: number; baseVersionId?: number; revision: number; createdBy: number; createdAt: string; updatedBy?: number; updatedAt?: string }
interface Page<T> { items: T[]; total: number }
interface DraftSummary { draftId: number; revision: number; activeLines: number; changedLines: number; changes: { changeType: string; rowCount: number; quantityDelta: number }[] }
const draft = ref<Draft | null>(null)
const draftLines = ref<DraftLine[]>([])
const draftTotal = ref(0)
const draftLoading = ref(false)
const draftSummary = ref<DraftSummary | null>(null)
const isCurrentVersion = computed(() => !selectedVersionId.value || selectedVersionId.value === versionMeta.value.id)
const showingDraft = computed(() => isCurrentVersion.value && !!draft.value)
const mainRows = computed(() => showingDraft.value ? draftLines.value : versionDetails.value)
const mainMonths = computed(() => showingDraft.value ? [...new Set([...recognizedMonths.value, ...draftLines.value.flatMap(row => Object.keys(row.months || {}))])].sort() : recognizedMonths.value)
const mainTotal = computed(() => showingDraft.value ? draftTotal.value : detailsTotal.value)
const hasChanges = computed(() => !!draftSummary.value?.changes?.length)
const pendingChangedLines = computed(() => draftSummary.value?.changedLines ?? 0)
const editVisible = ref(false)
const editLineId = ref<number | null>(null)
const editExistingKey = ref<{ factoryCode: string; materialId: string } | null>(null)
const editModel = ref({ factoryCode: '', materialId: '', formType: '', mold: '', status: '', months: {} as Record<string, string>, reason: '' })
const compareOld = ref<number | null>(null)
const compareNew = ref<number | null>(null)
const compareRows = ref<Record<string, unknown>[]>([])
const restoreKey = ref('')
const removedRows = computed(() => [...new Map(compareRows.value.filter(row => row.changeType === 'REMOVED').map(row => [String(row.factoryCode) + '|' + String(row.materialId), { factoryCode: String(row.factoryCode), materialId: String(row.materialId) }])).entries()])
const compareTotal = ref(0)
const comparePage = ref(1)
const compareKeyword = ref('')
const compareFactory = ref('')
const compareLoading = ref(false)
const compareVisible = ref(false)
const logVisible = ref(false)
const publishVisible = ref(false)
const publishReason = ref('')
const mutationLoading = ref(false)
const compareTarget = ref<'versions' | 'draft'>('versions')
const compareOptions = ref<ForecastVersion[]>([])
const compareAll = ref(false)
const versionMeta = ref<Record<string, unknown>>({})
const selectedVersionMeta = ref<Record<string, unknown>>({})
const logRows = ref<AuditLogRecord[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logLoading = ref(false)
const logError = ref('')
const materialFilter = ref<{ factoryCode: string; materialId: string } | null>(null)

async function loadDraft() {
  try {
    const value = await get<Draft>('/forecast-workspace/draft')
    draft.value = value?.id ? value : null
    if (draft.value) { await loadDraftLines(); await loadDraftSummary() }
    else { draftLines.value = []; draftTotal.value = 0; draftSummary.value = null }
  } catch (e: unknown) { ElMessage.error('加载草稿失败: ' + message(e)) }
}
async function loadDraftSummary() {
  if (!draft.value) return
  try { draftSummary.value = await get<DraftSummary>('/forecast-workspace/draft/' + draft.value.id + '/summary') }
  catch (e: unknown) { ElMessage.error('加载草稿差异摘要失败: ' + message(e)) }
}
async function loadDraftLines() {
  if (!draft.value) return
  draftLoading.value = true
  try {
    const q = new URLSearchParams({ page: String(detailsPage.value), pageSize: String(detailsPageSize.value), keyword: detailsKeyword.value })
    const result = await get<Page<DraftLine>>(`/forecast-workspace/draft/${draft.value!.id}/lines?${q}`)
    draftLines.value = result.items || []; draftTotal.value = result.total || 0
  } catch (e: unknown) { ElMessage.error('加载草稿明细失败: ' + message(e)) }
  finally { draftLoading.value = false }
}
function message(e: unknown) { return e instanceof Error ? e.message : '未知错误' }
function isDialogDismissal(e: unknown) { return e === 'cancel' || e === 'close' }
function changeLabel(value: string) { return ({ ADDED: '新增', REMOVED: '删除', QUANTITY: '数量变化', PRIORITY: '优先级变化', ATTRIBUTE: '属性变化', UNCHANGED: '未变化' } as Record<string,string>)[value] || value }
function startDraft() { openLine() }
async function ensureDraft(): Promise<Draft | null> {
  if (draft.value) return draft.value
  const created = await post<Draft>('/forecast-workspace/draft', {})
  draft.value = created
  await loadDraft()
  return draft.value
}
async function findDraftLine(row: { factoryCode: string; materialId: string }): Promise<DraftLine> {
  const q = new URLSearchParams({ factoryCode: row.factoryCode, materialId: row.materialId })
  return get<DraftLine>('/forecast-workspace/draft/' + draft.value!.id + '/line?' + q)
}
async function restoreDeletedRow(row: { factoryCode: string; materialId: string }) {
  if (!draft.value) return
  mutationLoading.value = true
  try {
    const q = new URLSearchParams({ factoryCode: row.factoryCode, materialId: row.materialId })
    const deleted = await get<DraftLine>('/forecast-workspace/draft/' + draft.value.id + '/deleted-line?' + q)
    const reason = await ElMessageBox.prompt('请输入恢复原因', '恢复 ' + row.materialId, { confirmButtonText: '确认', cancelButtonText: '取消', inputValidator: value => !!value.trim() || '原因必填' })
    await post('/forecast-workspace/draft/' + draft.value.id + '/lines', { factoryCode: deleted.factoryCode, materialId: deleted.materialId, formType: deleted.formType, mold: deleted.mold, status: deleted.status, months: Object.fromEntries(Object.entries(deleted.months).map(([month, qty]) => [month.slice(0, 7), qty])), expectedRevision: draft.value.revision, reason: reason.value })
    await loadDraft(); await loadComparison(); restoreKey.value = ''; ElMessage.success('已恢复料号')
  } catch (e: unknown) { if (!isDialogDismissal(e)) ElMessage.error('恢复失败: ' + message(e)) }
  finally { mutationLoading.value = false }
}
function restoreSelectedRow() {
  const row = removedRows.value.find(([key]) => key === restoreKey.value)?.[1]
  if (row) restoreDeletedRow(row)
}
async function editCurrentRow(row: { factoryCode: string; materialId: string }) {
  if (!isCurrentVersion.value) return
  mutationLoading.value = true
  try {
    if (draft.value) openLine(await findDraftLine(row))
    else {
      openLine(row as DraftLine)
      editLineId.value = null
      editExistingKey.value = { factoryCode: row.factoryCode, materialId: row.materialId }
    }
  }
  catch (e: unknown) { if (!isDialogDismissal(e)) ElMessage.error('打开编辑失败: ' + message(e)) }
  finally { mutationLoading.value = false }
}
async function deleteCurrentRow(row: { factoryCode: string; materialId: string }) {
  if (!isCurrentVersion.value) return
  mutationLoading.value = true
  try {
    const reason = await ElMessageBox.prompt('请输入删除原因', '删除 ' + row.materialId, { confirmButtonText: '确认', cancelButtonText: '取消', inputValidator: value => !!value.trim() || '原因必填' })
    await ensureDraft()
    const line = await findDraftLine(row)
    await post('/forecast-workspace/draft/' + draft.value!.id + '/lines/' + line.id + '/delete', { expectedRevision: draft.value!.revision, reason: reason.value })
    await loadDraft(); ElMessage.success('已标记删除，请核验差异后生成新版本')
  }
  catch (e: unknown) { if (!isDialogDismissal(e)) ElMessage.error('删除失败: ' + message(e)) }
  finally { mutationLoading.value = false }
}
async function openComparison(row?: { factoryCode: string; materialId: string }) {
  compareKeyword.value = row?.materialId || ''
  compareFactory.value = row?.factoryCode || ''
  compareTarget.value = draft.value && isCurrentVersion.value ? 'draft' : 'versions'
  comparePage.value = 1; compareVisible.value = true
  await searchCompareVersions()
  if (compareTarget.value === 'versions') {
    compareNew.value = selectedVersionId.value
    const previousNo = (selectedVersion.value?.versionNo ?? 0) - 1
    if (previousNo > 0) await searchCompareVersions(String(previousNo))
    const previousVersion = compareOptions.value.find(v => v.versionNo === previousNo)
    compareOld.value = previousVersion?.id ?? null
    await searchCompareVersions()
    if (previousVersion && !compareOptions.value.some(v => v.id === previousVersion.id)) compareOptions.value.push(previousVersion)
    if (selectedVersion.value && !compareOptions.value.some(v => v.id === selectedVersion.value!.id)) compareOptions.value.push(selectedVersion.value)
  }
  await loadComparison()
}
async function openLogs(row?: { factoryCode: string; materialId: string }) {
  materialFilter.value = row ? { factoryCode: row.factoryCode, materialId: row.materialId } : null
  logPage.value = 1; logVisible.value = true; await loadLogs()
}
function openLine(row?: DraftLine) {
  editLineId.value = row?.id ?? null
  editExistingKey.value = null
  editModel.value = { factoryCode: row?.factoryCode ?? '', materialId: row?.materialId ?? '', formType: row?.formType ?? '', mold: row?.mold ?? '', status: row?.status ?? '', months: row ? { ...row.months } : Object.fromEntries(recognizedMonths.value.map(month => [month, '0'])), reason: '' }
  editVisible.value = true
}
async function saveLine() {
  if (!editModel.value.reason.trim() || !Object.keys(editModel.value.months).length) { ElMessage.error('请填写原因及至少一个月份'); return }
  try {
    await ensureDraft()
    if (editExistingKey.value) editLineId.value = (await findDraftLine(editExistingKey.value)).id
    const months = Object.fromEntries(Object.entries(editModel.value.months).map(([k, v]) => [k.slice(0, 7), v]))
    const body = { ...editModel.value, months, expectedRevision: draft.value!.revision }
    if (editLineId.value) await put(`/forecast-workspace/draft/${draft.value!.id}/lines/${editLineId.value}`, body)
    else await post(`/forecast-workspace/draft/${draft.value!.id}/lines`, body)
    editVisible.value = false; await loadDraft(); ElMessage.success('调整已保存，请核验差异后生成新版本')
  } catch (e: unknown) { if (!isDialogDismissal(e)) { ElMessage.error('保存失败: ' + message(e)); await loadDraft() } }
}
async function publishDraft() {
  if (!draft.value || !publishReason.value.trim() || !hasChanges.value) return
  mutationLoading.value = true
  try {
    await post('/forecast-workspace/draft/' + draft.value.id + '/publish', { expectedRevision: draft.value.revision, reason: publishReason.value.trim() })
    publishVisible.value = false
    await loadLatestVersion(); await loadDraft(); ElMessage.success('新版本已生成；相关未发布排产需重新生成')
  } catch (e: unknown) { ElMessage.error('生成版本失败: ' + message(e)); await loadDraft() }
  finally { mutationLoading.value = false }
}
async function previewPublish() {
  if (!draft.value) return
  await loadDraftSummary()
  if (!draftSummary.value?.activeLines) { ElMessage.error('计划没有有效料号，不能生成版本'); return }
  if (!hasChanges.value) { ElMessage.warning('没有数据变化，无需生成新版本'); return }
  compareTarget.value = 'draft'; compareKeyword.value = ''; compareFactory.value = ''; compareAll.value = false; comparePage.value = 1
  await loadComparison()
  if (!compareTotal.value) { ElMessage.error('未能加载待发布差异，请稍后重试'); return }
  publishReason.value = ''; publishVisible.value = true
}
async function discardDraft() {
  if (!draft.value) return
  try {
    const result = await ElMessageBox.prompt('请输入放弃原因', '放弃草稿', { confirmButtonText: '确认', cancelButtonText: '取消', inputValidator: value => !!value.trim() || '原因必填' })
    await post(`/forecast-workspace/draft/${draft.value.id}/discard`, { expectedRevision: draft.value.revision, reason: result.value })
    await loadDraft()
  } catch (e: unknown) { if (!isDialogDismissal(e)) ElMessage.error('放弃失败: ' + message(e)) }
}
async function loadComparison() {
  if (compareTarget.value === 'draft' && !draft.value) { compareRows.value = []; compareTotal.value = 0; return }
  if (compareTarget.value === 'versions' && (!compareOld.value || !compareNew.value)) { compareRows.value = []; compareTotal.value = 0; return }
  compareLoading.value = true
  try {
    const q = new URLSearchParams({ keyword: compareKeyword.value, page: String(comparePage.value), pageSize: '20', changesOnly: String(!compareAll.value) })
    if (compareFactory.value) q.set('factoryCode', compareFactory.value)
    let endpoint: string
    if (compareTarget.value === 'draft') endpoint = '/forecast-workspace/draft/' + draft.value!.id + '/compare?' + q
    else { q.set('oldId', String(compareOld.value)); q.set('newId', String(compareNew.value)); endpoint = '/forecast-workspace/compare?' + q }
    const result = await get<Page<Record<string, unknown>>>(endpoint)
    compareRows.value = result.items || []; compareTotal.value = result.total || 0
  } catch (e: unknown) { compareRows.value = []; compareTotal.value = 0; ElMessage.error('加载差异失败: ' + message(e)) }
  finally { compareLoading.value = false }
}
async function searchCompareVersions(query = '') {
  try {
    const q = new URLSearchParams({ keyword: query, page: '1', pageSize: '100' })
    const result = await get<Page<ForecastVersion>>('/forecast-versions?' + q)
    compareOptions.value = result.items || []
  } catch (e: unknown) { ElMessage.error('加载版本候选失败: ' + message(e)) }
}
async function loadLogs() {
  logLoading.value = true
  logError.value = ''
  try {
    const q = new URLSearchParams({ resourceType: materialFilter.value ? 'forecast_line' : 'forecast', page: String(logPage.value), pageSize: '20' })
    if (materialFilter.value) q.set('resourceId', materialFilter.value.factoryCode + '|' + materialFilter.value.materialId)
    const result = await getAuditLogs(Object.fromEntries(q.entries()))
    logRows.value = result.items || []; logTotal.value = result.total || 0
  } catch (e: unknown) { logRows.value = []; logTotal.value = 0; logError.value = message(e); ElMessage.error('加载日志失败: ' + logError.value) }
  finally { logLoading.value = false }
}
// 导入相关
const importVisible = ref(false)
const file = ref<File | null>(null)
const stagingResult = ref<any>(null)
const uploadLoading = ref(false)
const confirmLoading = ref(false)
const isDragOver = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

// 版本详情相关
const selectedVersionId = ref<number | null>(null)
const versionDetails = ref<any[]>([])
const detailsLoading = ref(false)
const recognizedMonths = ref<string[]>([])
const detailsPage = ref(1)
const detailsPageSize = ref(20)
const detailsTotal = ref(0)
const detailsKeyword = ref('')

const currentStep = computed(() => stagingResult.value ? 1 : 0)

async function loadVersions() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: page.value.toString(),
      pageSize: pageSize.value.toString()
    })
    if (keyword.value) {
      params.append('keyword', keyword.value)
    }
    const res = await get<any>(`/forecast-versions?${params.toString()}`)
    versions.value = res?.items || []
    total.value = res?.total || 0
    if (versions.value.length) {
      compareNew.value = versions.value[0].id
      compareOld.value = versions.value[1]?.id ?? null
      compareOptions.value = [...versions.value]
    }
  } catch (e: any) {
    ElMessage.error('加载版本失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadLatestVersion() {
  page.value = 1
  keyword.value = ''
  let current: { id?: number }
  try { current = await get<{ id?: number }>('/forecast-workspace/current') }
  catch (e: unknown) { ElMessage.error('加载当前计划失败: ' + message(e)); return }
  versionMeta.value = current
  selectedVersionMeta.value = current
  await loadVersions()
  const latest = current.id
    ? versions.value.find(row => row.id === current.id) ?? { id: current.id, versionNo: Number(versionMeta.value.versionNo || 0), status: 'IMPORTED', createdAt: String(versionMeta.value.createdAt || '') }
    : null
  if (latest) {
    selectedVersion.value = latest
    detailsPage.value = 1
    await loadVersionDetails(latest.id, true)
  } else {
    selectedVersion.value = null
    selectedVersionId.value = null
    versionDetails.value = []
  }
  if (draft.value) await loadDraftLines()
}

async function loadVersionDetails(versionId: number, resetKeyword: boolean = false) {
  detailsLoading.value = true
  selectedVersionId.value = versionId
  if (resetKeyword) {
    detailsKeyword.value = ''
  }
  try {
    const params = new URLSearchParams({
      page: detailsPage.value.toString(),
      pageSize: detailsPageSize.value.toString()
    })
    if (detailsKeyword.value) {
      params.append('keyword', detailsKeyword.value)
    }
    const res = await get<any>(`/forecast-versions/${versionId}?${params.toString()}`)
    const items = res?.items || []
    detailsTotal.value = res?.total || 0
    
    // 提取月份列（从第一个物料的months对象中获取）
    recognizedMonths.value = res?.recognizedMonths || [...new Set(items.flatMap((item: { months?: Record<string, unknown> }) => Object.keys(item.months || {})))].sort()
    
    // 后端已返回聚合数据，直接使用
    versionDetails.value = items.map((item: any) => ({
      factoryCode: item.factoryCode,
      formType: item.formType,
      materialId: item.materialId,
      materialName: item.materialName,
      project: item.project,
      platform: item.platform,
      mold: item.mold,
      status: item.status,
      months: item.months || {},
      total: item.total || 0
    }))
  } catch (e: any) {
    ElMessage.error('加载版本详情失败: ' + e.message)
  } finally {
    detailsLoading.value = false
  }
}

function handleVersionSizeChange(val: number) {
  pageSize.value = val
  page.value = 1
  loadVersions()
}

function handleVersionPageChange(val: number) {
  page.value = val
  loadVersions()
}

function handleVersionSearch() {
  page.value = 1
  loadVersions()
}

function handleDetailsSizeChange(val: number) {
  detailsPageSize.value = val
  detailsPage.value = 1
  if (showingDraft.value) loadDraftLines()
  else if (selectedVersionId.value) loadVersionDetails(selectedVersionId.value)
}

function handleDetailsPageChange(val: number) {
  detailsPage.value = val
  if (showingDraft.value) loadDraftLines()
  else if (selectedVersionId.value) loadVersionDetails(selectedVersionId.value)
}

function handleDetailsSearch() {
  detailsPage.value = 1
  if (showingDraft.value) loadDraftLines()
  else if (selectedVersionId.value) loadVersionDetails(selectedVersionId.value)
}

async function handleVersionClick(row: any) {
  selectedVersion.value = row
  versionInfoVisible.value = false
  selectedVersionMeta.value = {}
  detailsPage.value = 1
  await loadVersionDetails(row.id, true)
  try { selectedVersionMeta.value = await get<Record<string, unknown>>(`/forecast-workspace/versions/${row.id}/metadata`) }
  catch (e: unknown) { ElMessage.error('加载版本信息失败: ' + message(e)) }
  if (draft.value && row.id === versionMeta.value.id) loadDraftLines()
  versionHistoryVisible.value = false
}

async function downloadTemplate() {
  try {
    await download('/forecast-imports/template', '经营计划_导入模板.xlsx')
    ElMessage.success('模板下载成功')
  } catch (e: any) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

async function downloadErrorReport() {
  if (!stagingResult.value?.taskId) return
  try {
    await download(`/forecast-imports/${stagingResult.value.taskId}/errors`, '经营计划_错误数据.xlsx')
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
    const res = await upload<any>('/forecast-imports', formData)
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
  if (!hasPermission('forecast:import_confirm') || !stagingResult.value?.taskId) return
  
  confirmLoading.value = true
  try {
    await post<any>(`/forecast-imports/${stagingResult.value.taskId}/confirm`)
    ElMessage.success('导入成功')
    handleClose()
    await loadLatestVersion()
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

function formatBeijingTime(utcTime: string): string {
  if (!utcTime) return ''
  // MySQL DATETIME is already stored in the application's local time zone; Instant carries Z.
  if (!/[Zz]|[+-]\d{2}:?\d{2}$/.test(utcTime)) return utcTime.replace('T', ' ').slice(0, 19)
  const date = new Date(utcTime)
  if (Number.isNaN(date.getTime())) return ''
  // 转换为北京时间 (UTC+8)
  const beijingTime = new Date(date.getTime() + 8 * 60 * 60 * 1000)
  const year = beijingTime.getUTCFullYear()
  const month = String(beijingTime.getUTCMonth() + 1).padStart(2, '0')
  const day = String(beijingTime.getUTCDate()).padStart(2, '0')
  const hours = String(beijingTime.getUTCHours()).padStart(2, '0')
  const minutes = String(beijingTime.getUTCMinutes()).padStart(2, '0')
  const seconds = String(beijingTime.getUTCSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

onMounted(() => {
  mrpApi.getFactories().then(items => { factoryOptions.value = items || [] }).catch(e => ElMessage.error('加载工厂失败: ' + message(e)))
  loadLatestVersion()
  loadDraft()
})
</script>

<template>
  <div class="forecast-page">
    <div class="page-header">
      <h1 class="page-title">经营计划</h1>
      <div class="page-actions">
        <el-button v-if="!isCurrentVersion" @click="loadLatestVersion">返回最新版本</el-button>
        <el-button @click="versionHistoryVisible = true">查看版本</el-button>
        <el-button @click="openComparison()">版本差异</el-button>
        <el-button v-if="hasPermission('forecast:import')" type="primary" :disabled="!!draft" @click="openImportDialog"><el-icon><Upload /></el-icon>导入</el-button>
        <el-button v-if="isCurrentVersion && hasPermission('forecast:create')" type="primary" @click="draft ? openLine() : startDraft()">新增</el-button>
        <el-button v-if="isCurrentVersion && draft && hasPermission('forecast:edit')" @click="discardDraft">放弃变更</el-button>
      </div>
    </div>
    <el-card shadow="never" v-loading="detailsLoading || draftLoading || mutationLoading">
      <template #header><div class="forecast-table-header">
        <div class="forecast-version-heading">
          <span class="table-title-text">经营计划</span>
          <el-tag v-if="selectedVersion" size="small" :type="isCurrentVersion ? 'success' : 'info'">{{ isCurrentVersion ? '生效版本' : '历史版本' }} V{{ selectedVersion.versionNo }}</el-tag>
          <el-button v-if="selectedVersion" link type="primary" @click="versionInfoVisible = true">版本详情</el-button>
          <el-tooltip v-if="showingDraft" :content="'当前预览尚未生效，MRP 仍使用 V' + selectedVersion?.versionNo" placement="bottom">
            <el-tag size="small" type="warning">当前显示：待发布预览</el-tag>
          </el-tooltip>
          <el-button v-if="showingDraft && hasChanges && hasPermission('forecast:publish')" link type="primary" @click="previewPublish">查看变更（{{ pendingChangedLines }} 条料号）并生成新版本</el-button>
          <el-button v-else-if="showingDraft && hasChanges" link type="primary" @click="openComparison()">查看待发布变更（{{ pendingChangedLines }} 条料号）</el-button>
          <el-button v-else-if="showingDraft && hasPermission('forecast:edit')" link type="primary" @click="discardDraft">无实际变更，放弃预览</el-button>
        </div>
        <div style="display:flex;gap:8px;align-items:center;margin-left:auto"><el-input v-model="detailsKeyword" placeholder="搜索料号/名称/工厂" style="width:220px" clearable @clear="handleDetailsSearch" @keyup.enter="handleDetailsSearch" /></div>
      </div></template>
      <el-table :data="mainRows" size="default" border max-height="600" style="width:100%">
        <el-table-column prop="factoryCode" label="业务线/工厂" width="100" fixed /><el-table-column prop="formType" label="形态" width="80" fixed /><el-table-column prop="materialId" label="料号" width="130" fixed /><el-table-column prop="materialName" label="名称" width="150" /><el-table-column prop="project" label="项目型号" width="120" /><el-table-column prop="platform" label="规格型号" width="120" /><el-table-column prop="mold" label="模具" width="100" /><el-table-column prop="status" width="90"><template #header><el-tooltip content="导入表中的业务状态，仅供查看与版本差异核验，不参与排产计算" placement="top"><span>业务状态</span></el-tooltip></template></el-table-column>
        <el-table-column v-for="month in mainMonths" :key="month" :label="month.slice(0,7)" width="100" align="right"><template #default="{row}">{{ row.months[month] ?? '' }}</template></el-table-column>
        <el-table-column prop="total" label="fcst-total" width="110" align="right" fixed="right" />
        <el-table-column label="操作" width="255" fixed="right"><template #default="{row}">
          <el-button v-if="isCurrentVersion && hasPermission('forecast:edit')" link type="primary" @click="editCurrentRow(row)">编辑</el-button>
          <el-button v-if="isCurrentVersion && hasPermission('forecast:delete')" link type="danger" @click="deleteCurrentRow(row)">删除</el-button>
          <el-button v-if="hasPermission('forecast:log') || hasPermission('audit:view')" link type="primary" @click="openLogs(row)">查看日志</el-button>
        </template></el-table-column>
      </el-table>
      <el-empty v-if="!detailsLoading && !draftLoading && !mainRows.length" description="暂无经营计划数据" />
      <div v-if="mainTotal > 0" style="margin-top:16px;display:flex;justify-content:flex-end"><el-pagination v-model:current-page="detailsPage" v-model:page-size="detailsPageSize" :page-sizes="[20,50,100]" :total="mainTotal" layout="total, sizes, prev, pager, next" @size-change="handleDetailsSizeChange" @current-change="handleDetailsPageChange" /></div>
    </el-card>
    <el-dialog v-model="versionInfoVisible" :title="'版本详情 · V' + (selectedVersion?.versionNo ?? '')" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="版本状态">{{ isCurrentVersion ? '生效版本' : '历史版本' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ selectedVersionMeta.sourceType === 'MANUAL' ? '人工调整' : selectedVersionMeta.sourceType === 'IMPORT' ? '导入' : '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ selectedVersionMeta.createdByName || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatBeijingTime(String(selectedVersionMeta.createdAt || '')) || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="生效人">{{ selectedVersionMeta.publishedByName || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="生效时间">{{ formatBeijingTime(String(selectedVersionMeta.publishedAt || '')) || '未记录' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog v-model="compareVisible" title="版本差异 · 工厂＋料号＋月份" width="1100px">
      <div class="filter-bar" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <el-radio-group v-model="compareTarget" @change="comparePage=1;loadComparison()"><el-radio-button label="正式版本" value="versions" /><el-radio-button label="当前版与待发布调整" value="draft" :disabled="!draft" /></el-radio-group>
        <template v-if="compareTarget === 'versions'"><el-select v-model="compareOld" filterable remote :remote-method="searchCompareVersions" placeholder="旧版本" style="width:140px"><el-option v-for="v in compareOptions" :key="v.id" :value="v.id" :label="'V'+v.versionNo" /></el-select><span>对比</span><el-select v-model="compareNew" filterable remote :remote-method="searchCompareVersions" placeholder="新版本" style="width:140px"><el-option v-for="v in compareOptions" :key="v.id" :value="v.id" :label="'V'+v.versionNo" /></el-select></template>
        <el-input v-model="compareKeyword" placeholder="搜索料号/工厂" clearable style="width:220px" @keyup.enter="comparePage=1;loadComparison()" @clear="comparePage=1;loadComparison()" /><el-checkbox v-model="compareAll" @change="comparePage=1;loadComparison()">显示未变化</el-checkbox><el-button type="primary" @click="comparePage=1;loadComparison()">对比</el-button>
      </div>
      <el-table :data="compareRows" border max-height="440" v-loading="compareLoading"><el-table-column prop="factoryCode" label="工厂" width="95" /><el-table-column prop="materialId" label="料号" width="140" /><el-table-column prop="materialName" label="名称" width="170" /><el-table-column prop="planMonth" label="月份" width="100" /><el-table-column label="变化类型" width="110"><template #default="{row}">{{ changeLabel(row.changeType) }}</template></el-table-column><el-table-column prop="oldQty" label="旧数量" /><el-table-column prop="newQty" label="新数量" /><el-table-column prop="delta" label="差额" /><el-table-column prop="oldPriority" label="旧优先级" width="95" /><el-table-column prop="newPriority" label="新优先级" width="95" /></el-table>
      <div v-if="compareTarget === 'draft' && removedRows.length && hasPermission('forecast:create')" style="display:flex;gap:8px;margin-top:8px"><el-select v-model="restoreKey" placeholder="选择待恢复料号" filterable style="width:260px"><el-option v-for="[key,row] in removedRows" :key="key" :value="key" :label="row.factoryCode + ' / ' + row.materialId" /></el-select><el-button type="primary" :disabled="!restoreKey" :loading="mutationLoading" @click="restoreSelectedRow">恢复</el-button></div>
      <el-empty v-if="!compareLoading && !compareRows.length" description="暂无版本差异" /><el-pagination v-if="compareTotal" v-model:current-page="comparePage" :total="compareTotal" :page-size="20" layout="total, prev, pager, next" @current-change="loadComparison" />
    </el-dialog>
    <el-dialog v-model="logVisible" :title="'操作日志' + (materialFilter ? ' · ' + materialFilter.materialId : '')" width="min(760px, 96vw)">
      <AuditLogViewer :logs="logRows" :loading="logLoading" :error="logError" :total="logTotal" :page="logPage" @page-change="logPage=$event;loadLogs()" @retry="loadLogs" />
    </el-dialog>
    <el-dialog v-model="publishVisible" title="生成新版本 · 差异核验" width="1050px">
      <p>变更摘要：{{ draftSummary?.changes.map(c => changeLabel(c.changeType) + ' ' + c.rowCount + '项').join('、') }}</p>
      <el-alert title="生成新版本后，相关未发布排产批次将失效，需要重新生成排产；已发布排产不会自动重算。" type="warning" :closable="false" style="margin-bottom:12px" />
      <el-table :data="compareRows" border max-height="330" v-loading="compareLoading"><el-table-column prop="factoryCode" label="工厂" /><el-table-column prop="materialId" label="料号" /><el-table-column prop="planMonth" label="月份" /><el-table-column label="变化"><template #default="{row}">{{ changeLabel(row.changeType) }}</template></el-table-column><el-table-column prop="oldQty" label="旧数量" /><el-table-column prop="newQty" label="新数量" /><el-table-column prop="delta" label="差额" /></el-table>
      <el-pagination v-if="compareTotal" v-model:current-page="comparePage" :total="compareTotal" :page-size="20" layout="total, prev, pager, next" @current-change="loadComparison" />
      <el-input v-model="publishReason" type="textarea" :rows="2" placeholder="请输入生成原因" style="margin-top:12px" />
      <template #footer><el-button @click="publishVisible=false">取消</el-button><el-button type="primary" :disabled="!publishReason.trim() || !hasChanges || compareLoading" :loading="mutationLoading" @click="publishDraft">确认生成</el-button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" :title="editLineId || editExistingKey ? '编辑经营计划' : '新增经营计划'" width="680px">
      <el-form label-width="90px"><el-form-item label="工厂"><el-select v-model="editModel.factoryCode" :disabled="!!editLineId || !!editExistingKey" style="width:100%"><el-option v-for="code in factoryOptions" :key="code" :label="code" :value="code" /></el-select></el-form-item>
        <el-form-item label="料号"><PolicyMaterialPicker :key="editLineId || editExistingKey?.materialId || 'new'" v-model="editModel.materialId" kind="forecast" :disabled="!!editLineId || !!editExistingKey" /></el-form-item>
        <el-form-item label="形态"><el-input v-model="editModel.formType" /></el-form-item><el-form-item label="模具"><el-input v-model="editModel.mold" /></el-form-item><el-form-item label="业务状态"><el-input v-model="editModel.status" placeholder="选填，不影响排产计算" /></el-form-item>
        <el-form-item label="月份数量"><div style="width:100%"><div v-for="(_,month) in editModel.months" :key="month" style="display:flex;gap:8px;margin-bottom:6px;align-items:center"><span style="width:100px">{{ month.slice(0,7) }}</span><el-input v-model="editModel.months[month]" style="width:170px" /></div><el-empty v-if="!Object.keys(editModel.months).length" description="当前版本无月份，请先导入经营计划" /></div></el-form-item>
        <el-form-item label="调整原因"><el-input v-model="editModel.reason" type="textarea" :rows="2" /></el-form-item></el-form>
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" @click="saveLine">确认</el-button></template>
    </el-dialog>

    <el-dialog v-model="versionHistoryVisible" title="版本记录" width="720px">
      <div class="filter-bar"><el-input v-model="keyword" placeholder="搜索版本" clearable style="width:220px" @clear="handleVersionSearch" @keyup.enter="handleVersionSearch" /></div>
      <el-table :data="versions" border v-loading="loading" @row-click="handleVersionClick" highlight-current-row>
        <el-table-column prop="versionNo" label="版本号" width="100"><template #default="{row}">V{{ row.versionNo }}</template></el-table-column>
        <el-table-column prop="status" label="版本状态" width="120"><template #default="{row}">{{ row.status === 'IMPORTED' ? '已导入' : row.status }}</template></el-table-column>
        <el-table-column label="导入时间"><template #default="{row}">{{ formatBeijingTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click.stop="handleVersionClick(row)">查看</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && versions.length === 0" description="暂无历史版本" />
      <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[3,10,20]" :total="total" layout="total, sizes, prev, pager, next" @size-change="handleVersionSizeChange" @current-change="handleVersionPageChange" />
    </el-dialog>

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

        <!-- 错误提示 -->
        <div v-if="stagingResult && stagingResult.errorRows > 0" class="error-alert">
          <el-alert
            type="error"
            :closable="false"
            show-icon
          >
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

        <!-- 预览数据表格 -->
        <div v-if="stagingResult && stagingResult.previewRows && stagingResult.previewRows.length > 0" class="preview-section">
          <div class="preview-title">数据预览（前10行）</div>
          <el-table :data="stagingResult.previewRows" size="small" border max-height="300">
            <el-table-column prop="type" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.type === 'error' ? 'danger' : 'success'" size="small" effect="plain">
                  {{ row.type === 'error' ? '错误' : '成功' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="row" label="行号" width="60" />
            <el-table-column prop="factoryCode" label="工厂" width="80" />
            <el-table-column prop="formType" label="形态" width="80" />
            <el-table-column prop="materialId" label="料号" width="120" />
            <el-table-column prop="materialName" label="名称" width="120" />
            <el-table-column prop="project" label="项目" width="100" />
            <el-table-column prop="platform" label="平台" width="100" />
            <el-table-column prop="mold" label="模具" width="80" />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column prop="planMonth" label="月份" width="100" />
            <el-table-column prop="forecastQty" label="数量" width="80" />
            <el-table-column prop="message" label="错误信息" min-width="150" />
          </el-table>
        </div>
      </div>

      <div class="rules-section">
        <div class="rules-title">上传的 Excel 文件需符合以下规范：</div>
        <div class="rules-list">
          <div class="rule-item">1. 仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件</div>
          <div class="rule-item">2. 工厂须为已维护且启用的工厂；其他必填项不能为空</div>
          <div class="rule-item">3. 不能存在合并单元格</div>
          <div class="rule-item">4. 请确保所有数据信息准确无误，错误的数据将无法被导入</div>
          <div class="rule-item">5. 导入过程可能需要一些时间，具体时长取决于导入数据量，请耐心等待</div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose" class="btn-cancel">取消</el-button>
          <el-button 
            v-if="hasPermission('forecast:import_confirm')"
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
.forecast-table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.forecast-version-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
