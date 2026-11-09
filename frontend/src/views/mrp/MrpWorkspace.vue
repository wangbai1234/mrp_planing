<template>
  <div class="page-header">
    <div>
      <div class="title-line">
        <h1 class="page-title">排产计划</h1>
        <el-tag v-if="batch" :type="statusInfo.type">{{ statusInfo.label }}</el-tag>
        <el-tag v-if="isHistorical" type="info">历史版本</el-tag>
      </div>
      <div v-if="batch" class="version-line">排产版本 #{{ batch.id }} · {{ batch.factoryCode }} · R{{ batch.revisionNo }} · 更新于 {{ formatTime(batch.updatedAt) }}</div>
    </div>
    <div class="page-actions">
      <el-button v-if="isHistorical" @click="showCurrent">返回当前版本</el-button>
      <el-button v-if="batch" @click="auxiliaryVisible = true">版本信息</el-button><el-button @click="openHistory">历史版本</el-button>
      <el-button v-if="hasPlanResult && permissionStore.hasPermission('mrp_plan:export')" type="primary" @click="handleExport">导出</el-button>
      <el-button v-if="permissionStore.hasPermission('mrp_batch:create')" type="primary" @click="openCreate">生成排产</el-button>
    </div>
  </div>

  <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="loadWorkspace()">重新加载</el-button></template></el-alert>
  <el-alert v-if="calculationFailure" :title="calculationFailure" type="error" show-icon :closable="false" class="page-alert" />

  <div v-loading="loading" class="workspace-content">
    <template v-if="batch">
      <div class="status-bar" :class="{ historical: isHistorical }">
        <div class="status-copy">
          <div class="status-message"><strong>{{ isHistorical ? '正在查看历史排产' : '当前排产' }}</strong><span>{{ lifecycleNotice || statusGuidance }}</span></div>
          <CalculationProgressBar :batch-status="batch.status" :progress="calcProgress" :retrying="progressRetrying" :lifecycle="lifecycleTask" :lifecycle-retrying="lifecycleStatusUnknown && lifecycleBusy" />
        </div>
        <div v-if="!isHistorical" class="status-actions">
          <el-button v-if="canCancel && !lifecycleBusy" type="danger" link @click="handleCancelBatch">放弃当前排产</el-button>
          <el-button v-if="primaryAction" type="primary" :loading="actionLoading || lifecycleBusy" :disabled="lifecycleBusy || (primaryAction.key === 'publish' && Boolean(publishBlockedReason))" @click="handlePrimaryAction">{{ primaryAction.label }}</el-button>
        </div>
      </div>

      <el-alert v-if="isLegacyIncompleteBatch" title="该旧批次缺少当前排产所需的锁定配置，只能作为历史记录查看。" type="warning" show-icon :closable="false" class="page-alert" />
      <div v-if="review?.openFeedback" class="status-bar"><span>待处理物料反馈：{{ review.openFeedback }}</span><el-button type="primary" @click="openFeedback">查看并处理</el-button></div>

      <el-alert v-if="noticeError" :title="noticeError" type="error" :closable="false" class="page-alert"><el-button type="primary" link @click="loadWorkspace()">重新加载</el-button></el-alert>
      <el-alert v-if="calendarCheck.total" :title="`生产日历或业务周配置缺失 ${calendarCheck.total} 项，当前排产不可继续。请维护配置后重新生成。`" type="error" show-icon :closable="false" class="page-alert"><el-button type="primary" link @click="router.push('/mrp/production-calendars')">维护生产日历</el-button></el-alert>
      <el-tabs v-model="activeTab" class="workspace-tabs">
        <el-tab-pane label="日排产" name="daily" :disabled="!hasPlanResult" />
        <el-tab-pane label="周汇总" name="weekly" :disabled="!hasPlanResult" />
        <el-tab-pane label="月汇总" name="monthly" :disabled="!hasPlanResult" />
      </el-tabs>
      <RollingPlanningGrid v-if="hasPlanResult" :key="`${batchId}:${batch.activeRunId}`" :batch-id="batchId" view="MACHINE_OUTPUT" :root-material-code="typeof route.query.root === 'string' ? route.query.root : undefined" :factory-code="typeof route.query.factory === 'string' ? route.query.factory : undefined" :source-month="typeof route.query.sourceMonth === 'string' ? route.query.sourceMonth : undefined" :focus-date="typeof route.query.date === 'string' ? route.query.date : undefined" :mode="activeTab === 'weekly' ? 'week' : activeTab === 'monthly' ? 'month' : 'day'" :read-only="isReadOnly || !['v4-rolling-business-weeks', 'v5-shifted-business-weeks'].includes(batch.ruleVersion || '')" @changed="loadWorkspace(true)" />
    </template>
    <el-empty v-else-if="!loading && !loadError" description="尚未创建本期排产"><el-button v-if="permissionStore.hasPermission('mrp_batch:create')" type="primary" @click="createVisible = true">生成排产</el-button></el-empty>
  </div>

  <el-dialog v-model="historyVisible" title="历史版本" width="820px">
    <div class="history-filter"><el-select v-model="historyStatus" placeholder="状态" clearable style="width:140px" @change="reloadHistory"><el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select><el-input v-model="historyKeyword" placeholder="搜索版本号或工厂" clearable style="width:220px" @keyup.enter="reloadHistory" @clear="reloadHistory" /></div>
    <el-table :data="historyRows" v-loading="historyLoading" max-height="460" @row-dblclick="selectHistory">
      <el-table-column prop="id" label="版本号" width="100"><template #default="{ row }">#{{ row.id }}</template></el-table-column><el-table-column prop="factoryCode" label="工厂" width="120" /><el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="batchStatusDisplay(row.status).type">{{ batchStatusDisplay(row.status).label }}</el-tag></template></el-table-column><el-table-column prop="revisionNo" label="修订" width="80"><template #default="{ row }">R{{ row.revisionNo }}</template></el-table-column><el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="selectHistory(row)">查看</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!historyLoading && !historyRows.length" description="暂无历史排产版本" />
    <div class="pagination-bar"><el-pagination v-model:current-page="historyPage" v-model:page-size="historyPageSize" :page-sizes="[20,50,100]" :total="historyTotal" layout="total, sizes, prev, pager, next" @size-change="reloadHistory" @current-change="loadHistory" /></div>
  </el-dialog>
  <el-dialog v-model="publishVisible" title="发布确认" width="880px">
    <el-alert :title="['v3-whole-machine-capacity','v4-rolling-business-weeks','v5-shifted-business-weeks'].includes(batch?.ruleVersion || '') ? '发布后通过修订版调整。请确认以下整机日产能（台/日）预警。' : '历史计划按原自制末级口径评估，请确认以下预警。'" type="warning" :closable="false" class="page-alert" />
    <el-table :data="publishWarnings" border max-height="400"><el-table-column prop="factoryCode" label="工厂" width="100" /><el-table-column prop="date" label="实际排产日" width="120" /><el-table-column label="已排负荷" min-width="145"><template #default="{row}">{{ quantityText(row.load) }} {{ row.capacityBasis === 'MACHINE_OUTPUT' ? '台' : row.capacityUnit || '' }}</template></el-table-column><el-table-column label="冻结日产能" min-width="145"><template #default="{row}">{{ quantityText(row.dailyCapacity) }} {{ row.capacityUnit || '' }}</template></el-table-column><el-table-column label="超出量" width="100"><template #default="{row}">{{ quantityText(row.excess) }}</template></el-table-column><el-table-column prop="message" label="预警" min-width="190" /></el-table>
    <el-empty v-if="!publishWarnings.length" description="日产能检查正常" />
    <el-checkbox v-if="publishWarnings.length" v-model="capacityAcknowledged" style="margin-top:16px">已了解当前预警，确认按当前计划发布</el-checkbox>
    <template #footer><el-button @click="publishVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading" :disabled="publishWarnings.length > 0 && !capacityAcknowledged" @click="confirmPublish">确认</el-button></template>
  </el-dialog>
  <el-drawer v-model="auxiliaryVisible" title="版本信息" size="720px" destroy-on-close>
    <div v-if="batch">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="经营计划">{{ versionText(batch.forecastVersionId) }}</el-descriptions-item><el-descriptions-item label="库存快照">{{ versionText(batch.inventorySnapshotId) }}</el-descriptions-item><el-descriptions-item :label="batch.bomSnapshotId ? '历史BOM快照' : 'BOM数据'">{{ batch.bomSnapshotId ? versionText(batch.bomSnapshotId) : '创建时最新同步 · ' + (batch.bomSourceChecksum?.slice(0, 12) || '-') }}</el-descriptions-item><el-descriptions-item label="生产日历">{{ versionText(batch.productionCalendarVersionId) }}</el-descriptions-item>
            <el-descriptions-item label="分类角色">{{ versionText(batch.categoryRoleVersionId) }}</el-descriptions-item><el-descriptions-item label="物料策略">{{ versionText(batch.materialPolicyVersionId) }}</el-descriptions-item><el-descriptions-item label="仓库映射">{{ versionText(batch.factoryWarehouseMappingVersionId) }}</el-descriptions-item><el-descriptions-item label="库存策略">{{ versionText(batch.stockPolicyVersionId) }}</el-descriptions-item>
            <el-descriptions-item label="业务周">{{ versionText(batch.businessWeekVersionId) }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatTime(batch.createdAt) }}</el-descriptions-item><el-descriptions-item label="发布时间">{{ formatTime(batch.publishedAt) }}</el-descriptions-item>
            <el-descriptions-item label="输入校验码" :span="2"><span class="checksum-value">{{ batch.inputChecksum || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="结果校验码" :span="2"><span class="checksum-value">{{ batch.resultChecksum || '-' }}</span></el-descriptions-item>
          </el-descriptions>
    </div>
  </el-drawer>
  <BatchCreateDialog v-model="createVisible" @created="handleCreated" />
  <el-dialog v-model="feedbackVisible" title="物料反馈处理" width="900px">
    <el-alert title="填写处理结论只关闭反馈，不会修改排产数量或物料分析。若需调整，请先修改整机日排产，再重新完成确认与物料分析。" type="info" :closable="false" class="page-alert" />
    <el-table :data="feedbackRows" v-loading="feedbackLoading" border>
      <el-table-column prop="factoryCode" label="工厂" width="100" />
      <el-table-column prop="rootMaterialCode" label="受影响整机" width="145" />
      <el-table-column prop="terminalMaterialCode" label="物料" width="145" />
      <el-table-column prop="affectedQuantity" label="影响数量" width="110" />
      <el-table-column prop="reason" label="反馈原因" min-width="180" />
      <el-table-column prop="response" label="处理结果" min-width="160" />
      <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="row.status === 'OPEN' && permissionStore.hasPermission('mrp_schedule:confirm')" type="primary" link @click="respondFeedback(row.id)">处理</el-button></template></el-table-column>
      <template #empty><el-empty description="暂无物料反馈" /></template>
    </el-table>
    <el-pagination v-model:current-page="feedbackPage" :page-size="20" :total="feedbackTotal" layout="total, prev, pager, next" @current-change="loadFeedback" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mrpApi, type BatchSummary, type CalculationProgress, type LifecycleProgress, type MrpCalcBatch, type DayCapacityLoad, type PlanningCalendarCheck, type WorkbenchReviewStatus, type MaterialFeedback } from '../../api/mrp'
import { quantityText } from '../../utils/dailyDecimal'
import { batchStatusDisplay } from '../../utils/mrpDisplay'
import { usePermissionStore } from '../../stores/permission'
import BatchCreateDialog from './components/BatchCreateDialog.vue'
import RollingPlanningGrid from './components/RollingPlanningGrid.vue'
import CalculationProgressBar from './components/CalculationProgress.vue'

const publishVisible = ref(false); const publishWarnings = ref<DayCapacityLoad[]>([]); const publishFingerprint = ref(''); const capacityAcknowledged = ref(false)
const publishBatchId = ref<number | null>(null)
const route = useRoute(); const router = useRouter(); const permissionStore = usePermissionStore()
const calendarCheck = ref<PlanningCalendarCheck>({ total: 0, missingDays: [], configurationIssues: [] }); const releaseChecking = ref(true)
const pendingDefaultCount = ref(0); const stockRiskCount = ref(0); const noticeError = ref('')
const currentBatch = ref<MrpCalcBatch | null>(null); const batch = ref<MrpCalcBatch | null>(null); const summary = ref<BatchSummary | null>(null)
const review = ref<WorkbenchReviewStatus | null>(null); const reviewSaving = ref(false)
const feedbackVisible = ref(false); const feedbackLoading = ref(false); const feedbackRows = ref<MaterialFeedback[]>([]); const feedbackPage = ref(1); const feedbackTotal = ref(0)
async function openFeedback(){feedbackVisible.value=true;feedbackPage.value=1;await loadFeedback()}
async function loadFeedback(){if(!batch.value)return;feedbackLoading.value=true;try{const result=await mrpApi.getMaterialFeedback(batch.value.id,feedbackPage.value,20);feedbackRows.value=result.items||[];feedbackTotal.value=result.total||0}catch(error:unknown){ElMessage.error('加载物料反馈失败：'+errorMessage(error))}finally{feedbackLoading.value=false}}
async function respondFeedback(id:number){if(!batch.value)return;try{const value=await ElMessageBox.prompt('请记录排产处理决定。此操作只关闭反馈，不修改排产或物料数量。','处理物料反馈',{inputType:'textarea',inputValidator:value=>Boolean(value?.trim())||'请填写处理结果'});await mrpApi.respondMaterialFeedback(batch.value.id,id,value.value);ElMessage.success('处理结论已记录，排产和物料数量未变化');await loadFeedback();await loadWorkspace(true)}catch(error:unknown){if(error!=='cancel')ElMessage.error('处理失败：'+errorMessage(error))}}
async function confirmSchedule() { if (!batch.value || !review.value?.scheduleFingerprint) return; reviewSaving.value = true; try { review.value = await mrpApi.confirmSchedule(batch.value.id, review.value.scheduleFingerprint); ElMessage.success('整机排产已确认'); await loadWorkspace(true) } catch (error: unknown) { ElMessage.error('确认失败：' + errorMessage(error)); await loadWorkspace(true) } finally { reviewSaving.value = false } }
const auxiliaryVisible = ref(false)
const exceptionCount = computed(() => (summary.value?.openIssues || 0) + (summary.value?.pendingUnallocated || 0)
  + pendingDefaultCount.value + stockRiskCount.value + calendarCheck.value.total)
const autoGenerationBatch = ref<number | null>(null)
const calcProgress = ref<CalculationProgress | null>(null)
const lifecycleTask = ref<LifecycleProgress | null>(null)
const lifecycleStatusUnknown = ref(false)
const lifecycleBusy = computed(() => lifecycleStatusUnknown.value || ['PENDING', 'RUNNING'].includes(lifecycleTask.value?.status || ''))
const lifecycleNotice = computed(() => {
  if (lifecycleStatusUnknown.value) return '读取发布或修订任务状态失败，正在重试，请勿重复提交'
  const task = lifecycleTask.value
  if (!task) return ''
  const name = task.taskType === 'MRP_PUBLISH' ? '发布' : '修订'
  if (lifecycleBusy.value) return name + '任务已提交，离开页面后仍会继续执行'
  return task.status === 'FAILED' ? name + '失败：' + (task.errorMessage || '请重新提交') : ''
})
const progressRetrying = ref(false)
const calculationFailure = computed(() => !isHistorical.value && batch.value?.status === 'READY_TO_CALCULATE' && calcProgress.value?.status === 'FAILED'
  ? 'MRP 计算失败（任务 #' + calcProgress.value.taskId + '）：' + (calcProgress.value.errorMessage || '请查看计算任务日志') : '')
const activeTab = ref('daily'); const loading = ref(false); const actionLoading = ref(false); const loadError = ref('')
const historyVisible = ref(false); const historyLoading = ref(false); const historyRows = ref<MrpCalcBatch[]>([]); const historyPage = ref(1); const historyPageSize = ref(20); const historyTotal = ref(0); const historyStatus = ref(''); const historyKeyword = ref(''); const createVisible = ref(false)
const batchId = computed(() => batch.value?.id ?? 0)
const requestedId = computed(() => { const value = Number(route.params.id); return Number.isInteger(value) && value > 0 ? value : null })
const isHistorical = computed(() => Boolean(batch.value && (batch.value.status === 'STALE'
  || !currentBatch.value || batch.value.id !== currentBatch.value.id)))
const statusInfo = computed(() => {
  const status = batch.value?.status
  const base = batchStatusDisplay(status)
  if (!['SCHEDULE_READY', 'MATERIAL_REVIEW', 'MATERIAL_RECALC_REQUIRED'].includes(status || '')) return base
  if (review.value?.schedule === 'CONFIRMED') return { ...base, label: '整机已确认', type: 'success' as const, guidance: '整机排产已确认；收到反馈后在本页处理。' }
  if (review.value?.schedule === 'EXPIRED') return { ...base, label: '整机确认已失效', type: 'warning' as const, guidance: '整机排产已调整，请重新确认当前结果。' }
  if (review.value?.schedule === 'PENDING') return { ...base, label: '整机待确认', guidance: '请核对整机日排产并确认。' }
  return base
})
const hasPlanResult = computed(() => (summary.value?.dailyPlanCount || 0) > 0 || ['READY','PUBLISHED','SUPERSEDED'].includes(batch.value?.status || ''))
const isReadOnly = computed(() => isHistorical.value || ['PUBLISHED','SUPERSEDED','CANCELLED','FAILED','STALE'].includes(batch.value?.status || ''))
const isLegacyIncompleteBatch = computed(() => Boolean(batch.value && (!batch.value.factoryWarehouseMappingVersionId || !batch.value.stockPolicyVersionId || !batch.value.businessWeekVersionId)))
const statusGuidance = computed(() => batch.value?.status === 'STALE' ? batchStatusDisplay('STALE').guidance : isHistorical.value ? '历史版本仅供查询，不允许修改、计算或发布。' : isLegacyIncompleteBatch.value ? '该版本缺少必要配置，请放弃后重新创建本期排产。' : calculationFailure.value ? '上次计算失败，请先查看错误原因，再决定是否重新计算。' : statusInfo.value.guidance)
const canCancel = computed(() => !isHistorical.value && permissionStore.hasPermission('mrp_batch:create') && ['DRAFT','WAITING_DECISION','READY_TO_CALCULATE','SCHEDULE_READY','MATERIAL_REVIEW','READY'].includes(batch.value?.status || ''))
const currentHasActiveWork = computed(() => Boolean(currentBatch.value && !['PUBLISHED','SUPERSEDED','CANCELLED','FAILED','STALE'].includes(currentBatch.value.status)))
const primaryAction = computed(() => {
  if (isHistorical.value || isLegacyIncompleteBatch.value) return null
  const status = batch.value?.status
  if (status === 'DRAFT' && permissionStore.hasPermission('mrp_batch:precheck')) return { key: 'precheck', label: '开始预检' }
  if (status === 'REANALYZING' && permissionStore.hasPermission('mrp_batch:precheck')) return { key: 'precheck', label: '重新分析' }
  if (status === 'READY_TO_CALCULATE' && permissionStore.hasPermission('mrp_batch:calculate')) return { key: 'calculate', label: '开始 MRP 计算' }
  if (status === 'SCHEDULE_READY' && review.value?.scheduleFingerprint && review.value.schedule !== 'CONFIRMED' && permissionStore.hasPermission('mrp_schedule:confirm')) return { key: 'confirm', label: '确认整机排产' }
  if (status === 'READY' && permissionStore.hasPermission('mrp_batch:publish')) return { key: 'publish', label: '发布排产结果' }
  if (status === 'PUBLISHED' && permissionStore.hasPermission('mrp_batch:publish')) return { key: 'revise', label: '生成修订版' }
  return null
})
const publishBlockedReason = computed(() => releaseChecking.value ? '正在核验发布条件，请稍候' : loadError.value || noticeError.value ? '发布条件核验失败，请重新加载后发布' : exceptionCount.value > 0 ? '当前版本尚有未处理的发布条件' : !batch.value?.activeRunId ? '尚无有效计算结果，无法发布' : '')
const statusOptions = ['DRAFT','ANALYZING','WAITING_DECISION','REANALYZING','READY_TO_CALCULATE','CALCULATING','WRITING','WAITING_DAILY_DISTRIBUTION','SCHEDULE_READY','MATERIAL_REVIEW','MATERIAL_RECALC_REQUIRED','READY','PUBLISHED','STALE','FAILED','CANCELLED','SUPERSEDED'].map(value => ({ value, label: batchStatusDisplay(value).label }))
function errorMessage(error: unknown) { return error instanceof Error ? error.message : String(error) }
function versionText(value: number | null) { return value ? `#${value}` : '未锁定' }
function formatTime(value?: string | null) { return value ? value.replace('T', ' ').substring(0, 19) : '-' }
function chooseDefaultTab() { activeTab.value = 'daily' }
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let requestSequence = 0
let disposed = false
function scheduleRefresh() {
  clearTimeout(refreshTimer)
  if (!disposed && (lifecycleBusy.value || ['CHECKING','ANALYZING','REANALYZING','CALCULATING','WRITING','WAITING_DAILY_DISTRIBUTION'].includes(batch.value?.status || ''))) refreshTimer = setTimeout(() => loadWorkspace(true), 2000)
}
async function loadWorkspace(preserveTab = false) {
  clearTimeout(refreshTimer)
  const sequence = ++requestSequence
  const isCurrentRequest = () => !disposed && sequence === requestSequence
  releaseChecking.value = true
  loading.value = !preserveTab; loadError.value = ''; noticeError.value = ''
  try {
    const [current, requested] = await Promise.all([
      mrpApi.getCurrentBatch(), requestedId.value ? mrpApi.getBatch(requestedId.value) : Promise.resolve(null)
    ])
    const selected = requestedId.value ? requested : current
    const nextSummary = selected ? await mrpApi.getBatchSummary(selected.id) : null
    if (!isCurrentRequest()) return
    const wasResult = hasPlanResult.value
    const sameBatch = batch.value?.id === selected?.id
    const followedLifecycleTaskId = sameBatch && lifecycleBusy.value ? lifecycleTask.value?.taskId : null
    const wasCalculating = sameBatch && ['CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION'].includes(batch.value?.status || '')
    if (!sameBatch) { calcProgress.value = null; lifecycleTask.value = null; lifecycleStatusUnknown.value = false; progressRetrying.value = false }
    currentBatch.value = current; batch.value = selected; summary.value = nextSummary
    review.value = null
    if (selected && selected.ruleVersion === 'v5-shifted-business-weeks') {
      try { const result = await mrpApi.getWorkbenchReviews(selected.id); if (!isCurrentRequest()) return; review.value = result }
      catch (error: unknown) { if (!isCurrentRequest()) return; noticeError.value = '加载双岗确认状态失败：' + errorMessage(error) }
    }
    if (selected && ['READY', 'PUBLISHED', 'SUPERSEDED'].includes(selected.status)) {
      const kind = selected.status === 'PUBLISHED' ? 'revise' : 'publish'
      try {
        const state = await mrpApi.getLifecycleTask(selected.id, kind)
        if (!isCurrentRequest()) return
        lifecycleStatusUnknown.value = false
        lifecycleTask.value = state
        if (state?.status === 'SUCCEEDED' && state.resultBatchId && state.resultBatchId !== selected.id
            && state.taskId === followedLifecycleTaskId) {
          // Follow only the task this view was waiting for. Historical successes must
          // never reopen cancelled results or interrupt an explicit history selection.
          const latestCurrent = await mrpApi.getCurrentBatch()
          if (!isCurrentRequest()) return
          currentBatch.value = latestCurrent
          if (latestCurrent?.id === state.resultBatchId) {
            await router.replace(`/planning/${state.resultBatchId}`)
            return
          }
        }
      } catch (error: unknown) {
        if (!isCurrentRequest()) return
        lifecycleStatusUnknown.value = true
        console.warn('读取发布或修订任务失败，将继续刷新', error)
      }
    } else if (selected) { lifecycleTask.value = null; lifecycleStatusUnknown.value = false }
    if (!preserveTab || (!wasResult && hasPlanResult.value)) chooseDefaultTab()
    pendingDefaultCount.value = 0; stockRiskCount.value = 0; calendarCheck.value = { total: 0, missingDays: [], configurationIssues: [] }
    if (selected) {
      try {
        const check = await mrpApi.getPlanningCalendarCheck(selected.id)
        if (!isCurrentRequest()) return
        calendarCheck.value = check
      } catch (error: unknown) {
        if (!isCurrentRequest()) return
        noticeError.value = '加载生产日历检查失败: ' + errorMessage(error)
      }
    }
    if (selected && (['CALCULATING', 'WRITING', 'WAITING_DAILY_DISTRIBUTION', 'READY_TO_CALCULATE'].includes(selected.status) || (selected.status === 'READY' && wasCalculating))) {
      try {
        const progress = await mrpApi.getCalculationProgress(selected.id)
        if (!isCurrentRequest()) return
        calcProgress.value = progress
        progressRetrying.value = false
      } catch (error: unknown) {
        if (!isCurrentRequest()) return
        progressRetrying.value = selected.status !== 'READY'
        console.warn('读取排产进度失败，将自动重试', error)
      }
    }
    if (selected?.activeRunId && hasPlanResult.value && permissionStore.hasPermission('mrp_shortage:view')) {
      try {
        const [pending, risks] = await Promise.all([
          mrpApi.getPendingDefaultExplosionCount(selected.id),
          mrpApi.getStockRisksPage(selected.id, 1, 1, selected.activeRunId)
        ])
        if (!isCurrentRequest()) return
        pendingDefaultCount.value = pending
        stockRiskCount.value = risks.total
      } catch (error: unknown) {
        if (!isCurrentRequest()) return
        noticeError.value = '加载发布条件失败: ' + errorMessage(error)
      }
    }
    if (!isCurrentRequest()) return
    if (autoGenerationBatch.value === selected?.id && selected?.status === 'READY_TO_CALCULATE' && permissionStore.hasPermission('mrp_batch:calculate')) {
      autoGenerationBatch.value = null
      const task = await mrpApi.calculate(selected.id)
      if (!isCurrentRequest()) return
      calcProgress.value = { taskId: task.id, status: task.status, progressCurrent: task.progressCurrent, progressTotal: task.progressTotal, phase: task.phase, errorMessage: null }
      await loadWorkspace(true)
    }
  } catch (error: unknown) {
    if (isCurrentRequest()) loadError.value = '加载排产工作台失败: ' + errorMessage(error)
  } finally {
    if (isCurrentRequest()) { releaseChecking.value = false; loading.value = false; scheduleRefresh() }
  }
}
async function loadHistory() { historyLoading.value = true; try { const result = await mrpApi.getBatchesPage(historyPage.value, historyPageSize.value, historyStatus.value || undefined, historyKeyword.value || undefined); const items = result.items || []; const currentMatches = Boolean(currentBatch.value && (!historyStatus.value || currentBatch.value.status === historyStatus.value) && (!historyKeyword.value || String(currentBatch.value.id).includes(historyKeyword.value) || currentBatch.value.factoryCode.includes(historyKeyword.value))); historyRows.value = items.filter(item => item.id !== currentBatch.value?.id); historyTotal.value = Math.max(0, (result.total || 0) - (currentMatches ? 1 : 0)) } catch (error: unknown) { ElMessage.error('加载历史版本失败: ' + errorMessage(error)) } finally { historyLoading.value = false } }
function reloadHistory() { historyPage.value = 1; loadHistory() }
function openHistory() { historyVisible.value = true; reloadHistory() }
async function selectHistory(row: MrpCalcBatch) { historyVisible.value = false; await router.push(`/planning/${row.id}`) }
async function showCurrent() { await router.push('/planning') }
function openCreate() { if (lifecycleBusy.value || currentHasActiveWork.value) { ElMessage.warning('当前已有未完成的排产任务，请先等待完成'); return }; createVisible.value = true }
async function handleCreated(id: number) {
  autoGenerationBatch.value = id
  await router.push(`/planning/${id}`)
  try {
    const created = await mrpApi.getBatch(id)
    if (created.status === 'DRAFT') await mrpApi.precheck(id)
  } catch (error: unknown) {
    autoGenerationBatch.value = null
    ElMessage.error('排产预检已终止：' + errorMessage(error))
  }
  await loadWorkspace()
}
async function handlePrimaryAction() {
  if (!primaryAction.value || !batch.value || actionLoading.value || lifecycleBusy.value) return
  if (primaryAction.value.key === 'confirm') { await confirmSchedule(); return }
  actionLoading.value = true
  try {
    if (primaryAction.value.key === 'precheck') await mrpApi.precheck(batch.value.id)
    else if (primaryAction.value.key === 'calculate') { const task = await mrpApi.calculate(batch.value.id); calcProgress.value = { taskId: task.id, status: task.status, progressCurrent: task.progressCurrent, progressTotal: task.progressTotal, phase: task.phase, errorMessage: null } }
    else if (primaryAction.value.key === 'publish') {
      if (publishBlockedReason.value) { ElMessage.warning(publishBlockedReason.value); return }
      const reviewedBatchId = batch.value.id; const reviewedRoute = route.fullPath
      const review = await mrpApi.getPublishCapacity(reviewedBatchId)
      if (disposed || batch.value?.id !== reviewedBatchId || route.fullPath !== reviewedRoute || primaryAction.value?.key !== 'publish') return
      const latest = await mrpApi.getBatch(reviewedBatchId)
      if (latest.status !== 'READY') {
        await loadWorkspace(true)
        ElMessage.info('该排产版本已更新或被替代，已刷新当前版本')
        return
      }
      publishBatchId.value = reviewedBatchId; publishWarnings.value = review.loads.filter(load => load.status !== 'NORMAL'); publishFingerprint.value = review.fingerprint; capacityAcknowledged.value = false; publishVisible.value = true; return
    }
    else if (primaryAction.value.key === 'revise') {
      const sourceId = batch.value.id; const sourceRoute = route.fullPath
      const check = await mrpApi.getPlanningCalendarCheck(sourceId, true)
      if (disposed || batch.value?.id !== sourceId || route.fullPath !== sourceRoute) return
      const missing = check.total ? `当前配置有 ${check.total} 项缺失：${[...check.missingDays.map(day => `${day.factoryCode} ${day.date}`), ...check.configurationIssues].join('、')}。建议先维护完整；继续生成的草稿将禁止发布，需补齐后重新生成。` : ''
      if (check.total) { ElMessage.error(missing); return }
      await ElMessageBox.confirm('修订版将采用当前最新生产日历和物料单位，经营计划、库存及其他配置沿用已锁定版本。确认生成？', '生成修订版', { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' })
      if (disposed || batch.value?.id !== sourceId || route.fullPath !== sourceRoute) return
      const submitted = await mrpApi.reviseBatch(sourceId); lifecycleTask.value = { ...submitted, taskType: 'MRP_REVISION', resultBatchId: null }; ElMessage.success('修订任务已提交，离开页面后仍会继续执行'); await loadWorkspace(true); return }
    ElMessage.success(primaryAction.value.key === 'calculate' ? '计算任务已提交' : '操作成功'); await loadWorkspace()
  } catch (error: unknown) { if (error !== 'cancel') ElMessage.error('操作失败: ' + errorMessage(error)) } finally { actionLoading.value = false }
}
async function confirmPublish() {
  if (publishBlockedReason.value) { ElMessage.warning(publishBlockedReason.value); return }
  if (actionLoading.value || !batch.value || batch.value.id !== publishBatchId.value || primaryAction.value?.key !== 'publish' || (publishWarnings.value.length && !capacityAcknowledged.value)) return
  actionLoading.value = true
  try {
    const latest = await mrpApi.getBatch(batch.value.id)
    if (latest.status !== 'READY') {
      publishVisible.value = false
      await loadWorkspace(true)
      ElMessage.info('该排产版本已更新或被替代，已刷新当前版本')
      return
    }
    const submitted = await mrpApi.publish(batch.value.id, publishFingerprint.value)
    lifecycleTask.value = { ...submitted, taskType: 'MRP_PUBLISH', resultBatchId: null }
    publishVisible.value = false
    ElMessage.success('发布任务已提交，离开页面后仍会继续执行')
    await loadWorkspace(true)
  }
  catch (error: unknown) { publishVisible.value = false; await loadWorkspace(true); ElMessage.error('发布状态可能已变化，已刷新版本列表：' + errorMessage(error)) }
  finally { actionLoading.value = false }
}
async function handleCancelBatch() { if (!batch.value) return; try { await ElMessageBox.confirm('作废后该版本仅作为历史记录保留。确认放弃当前排产？', '放弃当前排产', { confirmButtonText: '确认作废', cancelButtonText: '取消', type: 'warning' }); await mrpApi.cancelBatch(batch.value.id); ElMessage.success('当前排产已作废'); await router.push('/planning'); await loadWorkspace() } catch (error: unknown) { if (error !== 'cancel') ElMessage.error('作废失败: ' + errorMessage(error)) } }
async function handleExport() { if (!batch.value) return; try { await mrpApi.exportDailyPlans(batch.value.id) } catch (error: unknown) { ElMessage.error('导出失败: ' + errorMessage(error)) } }
watch(() => route.fullPath, () => { publishVisible.value = false; publishBatchId.value = null; publishFingerprint.value = ''; capacityAcknowledged.value = false; void loadWorkspace() })
onMounted(() => loadWorkspace())
onBeforeUnmount(() => { disposed = true; ++requestSequence; clearTimeout(refreshTimer) })
</script>

<style scoped>
.page-header,.title-line,.page-actions,.status-bar,.status-copy,.history-filter{display:flex;align-items:center}.page-header{justify-content:space-between;gap:20px;margin-bottom:14px}.page-title{margin:0;font-size:20px;color:#1F2329}.title-line{gap:10px}.version-line{margin-top:5px;color:#86909C;font-size:13px}.page-actions{gap:8px;flex-wrap:wrap;justify-content:flex-end}.page-alert{margin-bottom:12px}.workspace-content{min-height:360px;background:#fff;border-radius:8px;padding:14px 16px}.status-bar{justify-content:space-between;gap:18px;padding:10px 12px;margin-bottom:10px;background:#F2F6FF;border-left:3px solid #1677FF}.status-bar.historical{background:#F7F8FA;border-left-color:#86909C}.status-copy{flex:1;align-items:stretch;flex-direction:column;gap:8px;min-width:0}.status-message{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.status-copy strong{white-space:nowrap;color:#1F2329}.status-copy span{color:#4E5969;font-size:13px}.calculation-progress{display:flex;align-items:center;gap:8px;white-space:nowrap;color:#4E5969;font-size:12px}.status-actions{display:flex;gap:8px;white-space:nowrap}.workspace-tabs :deep(.el-tabs__header){margin-bottom:12px}.history-filter{gap:12px;margin-bottom:12px}.pagination-bar{display:flex;justify-content:flex-end;margin-top:14px}.checksum-value{overflow-wrap:anywhere}@media(max-width:1000px){.page-header,.status-bar{align-items:flex-start;flex-direction:column}.page-actions{justify-content:flex-start}.status-copy{align-items:flex-start;flex-direction:column;gap:4px}}
</style>
