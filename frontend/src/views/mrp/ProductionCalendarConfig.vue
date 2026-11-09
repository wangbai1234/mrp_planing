<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigImportDialog from './components/ConfigImportDialog.vue'
import { mrpApi, type CalendarEntry, type CalendarBatchRequest, type CalendarBatchPreview } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import ResourceAuditViewer from '../../components/ResourceAuditViewer.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = computed(() => permissionStore.hasPermission('mrp_calendar:manage'))
function thisMonth() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}` }
const factories = ref<string[]>([])
const factoryCode = ref('')
const month = ref(thisMonth())
const view = ref<'calendar' | 'list'>('calendar')
const monthRows = ref<CalendarEntry[]>([])
const listRows = ref<CalendarEntry[]>([])
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const importVisible = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const editing = ref<CalendarEntry | null>(null)
const dayForm = ref({ productionDay: true, dailyCapacity: '', capacityUnit: '台', reason: '' })
const logVisible = ref(false)
const logTarget = ref<CalendarEntry | null>(null)
const batchVisible = ref(false)
const batchSaving = ref(false)
const batchForm = ref({ startDate: '', endDate: '', weekdays: [1, 2, 3, 4, 5], productionDay: true, dailyCapacity: '', capacityUnit: '台', reason: '', overwrite: false, copyFromFactory: '' })
const batchPreview = ref<CalendarBatchPreview | null>(null)
let loadSequence = 0

const monthDate = computed(() => new Date(`${month.value}-01T12:00:00`))
const monthEnd = computed(() => `${month.value}-${String(new Date(monthDate.value.getFullYear(), monthDate.value.getMonth() + 1, 0).getDate()).padStart(2, '0')}`)
const dayCells = computed(() => {
  const count = new Date(monthDate.value.getFullYear(), monthDate.value.getMonth() + 1, 0).getDate()
  const offset = (monthDate.value.getDay() + 6) % 7
  return [...Array.from({ length: offset }, () => ''), ...Array.from({ length: count }, (_, i) => `${month.value}-${String(i + 1).padStart(2, '0')}`)]
})
const dayMap = computed(() => new Map(monthRows.value.map(row => [row.calendarDate, row])))
const copyFactories = computed(() => factories.value.filter(item => item !== factoryCode.value))
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
function quantity(value: CalendarEntry['dailyCapacity']) {
  if (value === null || value === undefined) return ''
  return String(value).replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '')
}
function scaledQuantity(value: string) {
  if (!/^\d+(\.\d{1,6})?$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  return BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6, '0'))
}
function formatQuantity(value: bigint) {
  const fraction = String(value % 1000000n).padStart(6, '0').replace(/0+$/, '')
  return `${value / 1000000n}${fraction ? `.${fraction}` : ''}`
}
function dayState(row?: CalendarEntry) {
  if (row?.productionDay === false) return 'REST'
  if (row?.productionDay === true && scaledQuantity(quantity(row.dailyCapacity)) !== null && (scaledQuantity(quantity(row.dailyCapacity)) ?? 0n) > 0n && row.capacityUnit?.trim() === '台' && (scaledQuantity(quantity(row.dailyCapacity)) ?? 0n) % 1000000n === 0n) return 'PRODUCTION'
  return 'UNCONFIGURED'
}
function stateLabel(row?: CalendarEntry) { return { PRODUCTION: '生产', REST: '停产', UNCONFIGURED: '未配置' }[dayState(row)] }
function legacyCapacity(row?: CalendarEntry | null) { return Boolean(row && row.dailyCapacity != null && row.capacityUnit?.trim() !== '台') }
function capacityLabel(row?: CalendarEntry) {
  if (legacyCapacity(row)) return `原配置 ${quantity(row!.dailyCapacity)} ${row!.capacityUnit || '单位缺失'}，需重新维护`
  if (dayState(row) === 'UNCONFIGURED') return '待维护'
  return `${row?.productionDay ? quantity(row.dailyCapacity) : '0'} ${row?.capacityUnit || ''}`.trim()
}
const summary = computed(() => {
  const totals = new Map<string, bigint>()
  let production = 0; let rest = 0; let unconfigured = 0
  for (const date of dayCells.value.filter(Boolean)) {
    const row = dayMap.value.get(date)
    const state = dayState(row)
    if (state === 'PRODUCTION' && row) { production++; const unit = row.capacityUnit!.trim(); totals.set(unit, (totals.get(unit) || 0n) + (scaledQuantity(quantity(row.dailyCapacity)) || 0n)) }
    else if (state === 'REST') rest++
    else unconfigured++
  }
  return { production, rest, unconfigured, capacity: [...totals].map(([unit, value]) => `${formatQuantity(value)} ${unit}`).join(' / ') || '—' }
})
function errorMessage(error: unknown) { return error instanceof Error ? error.message : String(error) }
function time(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '—' }
async function load() {
  if (!factoryCode.value || !month.value) return
  const sequence = ++loadSequence
  loading.value = true; loadError.value = ''
  try {
    const days = await mrpApi.getCalendarMonth(factoryCode.value, month.value)
    if (sequence !== loadSequence) return
    monthRows.value = days || []
    if (view.value === 'list') {
      const params = { factoryCode: factoryCode.value, month: month.value, page: page.value, pageSize: pageSize.value, keyword: keyword.value, status: status.value }
      const result = await mrpApi.getCalendarPage(params)
      if (sequence !== loadSequence) return
      listRows.value = result.items || []; total.value = result.total
    }
  } catch (error) { if (sequence === loadSequence) { monthRows.value = []; listRows.value = []; total.value = 0; loadError.value = errorMessage(error) } }
  finally { if (sequence === loadSequence) loading.value = false }
}
async function initialize() {
  loading.value = true; loadError.value = ''
  try {
    factories.value = await mrpApi.getFactories()
    if (!factories.value.length) { loadError.value = '暂无可用工厂'; return }
    factoryCode.value = factories.value[0]!
    await load()
  } catch (error) { loadError.value = errorMessage(error) }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function moveMonth(delta: number) { const date = new Date(monthDate.value); date.setMonth(date.getMonth() + delta); month.value = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}` }
function showLogs(row: CalendarEntry) {
  logTarget.value = { ...row }
  logVisible.value = true
}
function openDay(date: string) {
  const row = dayMap.value.get(date)
  editing.value = row ? { ...row } : { factoryCode: factoryCode.value, calendarDate: date, productionDay: null, dailyCapacity: null, capacityUnit: '台', reason: '', revision: 0 }
  dayForm.value = { productionDay: row?.productionDay !== false, dailyCapacity: quantity(row?.dailyCapacity ?? null), capacityUnit: '台', reason: row?.reason || '' }
  editVisible.value = true
}
function capacityValid(production: boolean, capacity: string, unit: string) {
  if (!production) return true
  const value = scaledQuantity(capacity.trim())
  if (value === null || value <= 0n || value > 999999999999000000n || value % 1000000n !== 0n) { ElMessage.error('整机日产能须为正整数，最多 12 位整数，单位为台'); return false }
  if (unit !== '台') { ElMessage.error('整机日产能单位须为台'); return false }
  return true
}
async function saveDay() {
  if (!canManage.value || !editing.value || !capacityValid(dayForm.value.productionDay, dayForm.value.dailyCapacity, dayForm.value.capacityUnit)) return
  if (legacyCapacity(editing.value)) {
    try { await ElMessageBox.confirm(`原配置为 ${quantity(editing.value.dailyCapacity)} ${editing.value.capacityUnit || '单位缺失'}。确认将本日重新维护为整机日产能 ${dayForm.value.productionDay ? dayForm.value.dailyCapacity : '0'} 台？原数值不会自动换算。`, '确认整机产能口径', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }) }
    catch { return }
  }
  const row: CalendarEntry = { ...editing.value, ...dayForm.value, dailyCapacity: dayForm.value.productionDay ? dayForm.value.dailyCapacity.trim() : '0', capacityUnit: dayForm.value.capacityUnit.trim() }
  saving.value = true
  try {
    if (row.id) await mrpApi.updateProductionCalendar(row.id, row)
    else await mrpApi.createProductionCalendar(row)
    editVisible.value = false; ElMessage.success('已保存'); await load()
  } catch (error) { ElMessage.error('保存失败：' + errorMessage(error)) }
  finally { saving.value = false }
}
async function removeDay(row: CalendarEntry) {
  if (!canManage.value || !row.id || saving.value) return
  try {
    await ElMessageBox.confirm(`确认删除 ${row.factoryCode} ${row.calendarDate} 的配置？删除后该日期将为未配置。`, '删除日期配置', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    saving.value = true
    await mrpApi.deleteProductionCalendar(row.id)
    editVisible.value = false; ElMessage.success('已删除'); await load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error('删除失败：' + errorMessage(error)) }
  finally { saving.value = false }
}
function openBatch() {
  batchForm.value = { startDate: `${month.value}-01`, endDate: monthEnd.value, weekdays: [1, 2, 3, 4, 5], productionDay: true, dailyCapacity: '', capacityUnit: '台', reason: '', overwrite: false, copyFromFactory: '' }
  batchPreview.value = null; batchVisible.value = true
}
function batchRequest(): CalendarBatchRequest {
  const form = batchForm.value
  return { factoryCode: factoryCode.value, ...form, dailyCapacity: form.productionDay ? form.dailyCapacity.trim() : '0', capacityUnit: form.capacityUnit.trim(), copyFromFactory: form.copyFromFactory || undefined }
}
async function previewBatch() {
  if (!canManage.value) return
  if (!batchForm.value.startDate || !batchForm.value.endDate || batchForm.value.startDate > batchForm.value.endDate) { ElMessage.error('请选择有效的日期范围'); return }
  if (!batchForm.value.weekdays.length) { ElMessage.error('请选择适用星期'); return }
  if (!batchForm.value.copyFromFactory && !capacityValid(batchForm.value.productionDay, batchForm.value.dailyCapacity, batchForm.value.capacityUnit)) return
  batchSaving.value = true; batchPreview.value = null
  try { batchPreview.value = await mrpApi.previewCalendarBatch(batchRequest()) }
  catch (error) { ElMessage.error('预览失败：' + errorMessage(error)) }
  finally { batchSaving.value = false }
}
async function saveBatch() {
  if (!canManage.value || !batchPreview.value) return
  batchSaving.value = true
  try {
    await mrpApi.saveCalendarBatch({ ...batchRequest(), expectedFingerprint: batchPreview.value.fingerprint })
    batchVisible.value = false; ElMessage.success('已保存批量配置'); await load()
  } catch (error) { batchPreview.value = null; ElMessage.error('保存失败，请重新预览：' + errorMessage(error)) }
  finally { batchSaving.value = false }
}
watch(batchForm, () => { batchPreview.value = null }, { deep: true })
watch([factoryCode, month, view], (_values, previous) => { if (!previous[0]) return; page.value = 1; void load() })
onMounted(initialize)
</script>

<template>
  <div class="page-header">
    <h1 class="page-title">生产日历</h1>
    <div class="page-actions">
      <el-button v-if="canManage" type="primary" :disabled="!factoryCode" @click="importVisible = true">导入</el-button>
      <el-button v-if="canManage" type="primary" :disabled="!factoryCode" @click="openBatch">新增</el-button>
    </div>
  </div>
  <el-alert v-if="loadError" :title="`加载失败：${loadError}`" type="error" :closable="false" class="page-alert">
    <template #default><el-button type="primary" link @click="factories.length ? load() : initialize()">重新加载</el-button></template>
  </el-alert>
  <div class="filter-bar">
    <el-select v-model="factoryCode" placeholder="工厂" style="width:140px"><el-option v-for="factory in factories" :key="factory" :label="factory" :value="factory" /></el-select>
    <el-button @click="moveMonth(-1)">上月</el-button>
    <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" style="width:140px" />
    <el-button @click="moveMonth(1)">下月</el-button>
    <el-button @click="month = thisMonth()">本月</el-button>
    <el-radio-group v-model="view" class="view-switch"><el-radio-button value="calendar">月历</el-radio-button><el-radio-button value="list">列表</el-radio-button></el-radio-group>
  </div>
  <div v-loading="loading" class="summary-row">
    <div><span>生产天数</span><strong>{{ summary.production }} 天</strong></div>
    <div><span>月整机配置产能</span><strong>{{ summary.capacity }}</strong></div>
    <div><span>停产天数</span><strong>{{ summary.rest }} 天</strong></div>
    <div><span>未配置天数</span><strong :class="{ 'unconfigured-number': summary.unconfigured > 0 }">{{ summary.unconfigured }} 天</strong></div>
  </div>
  <el-alert title="整机日产能单位为台/日，用于新计划的整机产出预警；已生成计划按锁定的日历评估。旧单位配置需重新维护。" type="info" :closable="false" class="page-alert" />
  <el-card v-if="view === 'calendar'" v-loading="loading" shadow="never" class="table-card">
    <div class="week-head"><span v-for="day in weekdays" :key="day">周{{ day }}</span></div>
    <div class="calendar-grid">
      <div v-for="(date, index) in dayCells" :key="date || index" class="day-cell" :class="{ 'padding-cell': !date, 'rest-cell': date && dayState(dayMap.get(date)) === 'REST' }">
        <template v-if="date">
          <div class="day-heading"><el-button type="primary" link :aria-label="`查看 ${date} 配置`" @click="openDay(date)">{{ date.slice(-2) }}</el-button><el-tag :type="dayState(dayMap.get(date)) === 'PRODUCTION' ? 'success' : dayState(dayMap.get(date)) === 'REST' ? 'info' : 'warning'" size="small">{{ stateLabel(dayMap.get(date)) }}</el-tag></div>
          <el-button class="capacity-button" link :aria-label="`${date} ${capacityLabel(dayMap.get(date))}`" @click="openDay(date)">{{ capacityLabel(dayMap.get(date)) }}</el-button>
          <el-tooltip v-if="dayMap.get(date)?.reason" :content="dayMap.get(date)?.reason" placement="top"><div class="day-reason">{{ dayMap.get(date)?.reason }}</div></el-tooltip>
        </template>
      </div>
    </div>
    <el-empty v-if="!loading && !loadError && summary.unconfigured === dayCells.filter(Boolean).length" description="本月尚无有效整机产能配置，点击新增批量设置生产日和整机日产能" :image-size="64" />
  </el-card>
  <el-card v-else v-loading="loading" shadow="never" class="table-card">
    <div class="list-filters">
      <el-select v-model="status" placeholder="全部状态" clearable value-on-clear="" style="width:140px" @change="search"><el-option label="生产" value="PRODUCTION" /><el-option label="停产" value="REST" /><el-option label="未配置" value="UNCONFIGURED" /></el-select>
      <el-input v-model="keyword" placeholder="搜索备注" clearable style="width:220px" @keyup.enter="search" @clear="search" />
    </div>
    <el-table :data="listRows" border>
      <el-table-column prop="calendarDate" label="日期" width="125" />
      <el-table-column label="状态" width="95"><template #default="{ row }"><el-tag :type="dayState(row) === 'PRODUCTION' ? 'success' : dayState(row) === 'REST' ? 'info' : 'warning'">{{ stateLabel(row) }}</el-tag></template></el-table-column>
      <el-table-column label="整机日产能（台/日）" min-width="190"><template #default="{ row }">{{ capacityLabel(row) }}</template></el-table-column>
      
      <el-table-column prop="reason" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="更新人" min-width="110"><template #default="{ row }">{{ row.updatedByName || '—' }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="165"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="showLogs(row)">查看日志</el-button><el-button type="primary" link @click="openDay(row.calendarDate)">{{ canManage ? '编辑' : '查看' }}</el-button><el-button v-if="canManage && row.id" type="danger" link :disabled="saving" @click="removeDay(row)">删除</el-button></template></el-table-column>
      <template #empty><el-empty description="暂无符合条件的日期" :image-size="64" /></template>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" class="pagination" @current-change="load" @size-change="search" />
  </el-card>
  <el-drawer v-model="editVisible" :title="`${canManage ? '日期设置' : '日期详情'} · ${editing?.calendarDate || ''}`" size="520px">
    <el-form v-if="editing" label-width="110px" :disabled="!canManage || saving">
      <el-form-item label="工厂"><el-input :model-value="editing.factoryCode" disabled /></el-form-item>
      <el-form-item label="日期"><el-input :model-value="editing.calendarDate" disabled /></el-form-item>
      <el-alert v-if="legacyCapacity(editing)" :title="capacityLabel(editing)" type="warning" :closable="false" class="page-alert" />
      <el-form-item label="生产日"><el-switch v-model="dayForm.productionDay" active-text="生产" inactive-text="停产" /></el-form-item>
      <el-form-item label="整机日产能" :required="dayForm.productionDay"><el-input v-model="dayForm.dailyCapacity" :disabled="!dayForm.productionDay" placeholder="正整数（台/日）" /><span v-if="!dayForm.productionDay" class="field-hint">停产日期保存为 0</span></el-form-item>
      <el-form-item label="单位"><el-input model-value="台" disabled /></el-form-item>
      <el-form-item label="备注"><el-input v-model="dayForm.reason" type="textarea" :rows="3" maxlength="500" /></el-form-item>
    </el-form>
    <template v-if="view === 'calendar'">
    <el-divider content-position="left">修改记录</el-divider>
    <ResourceAuditViewer v-if="view === 'calendar' && editing" :key="`${editing.factoryCode}|${editing.calendarDate}`" resource-type="mrp_production_calendar" :resource-id="`${editing.factoryCode}|${editing.calendarDate}`" />
    </template>
    <template #footer><div class="drawer-actions"><el-button v-if="view === 'calendar' && canManage && editing?.id" type="danger" link :disabled="saving" @click="removeDay(editing)">删除</el-button><div class="page-actions"><el-button @click="editVisible = false">{{ canManage ? '取消' : '关闭' }}</el-button><el-button v-if="canManage" type="primary" :loading="saving" @click="saveDay">确认</el-button></div></div></template>
  </el-drawer>
  <ResourceAuditDialog v-model="logVisible" resource-type="mrp_production_calendar" :resource-id="logTarget ? `${logTarget.factoryCode}|${logTarget.calendarDate}` : ''" :target="logTarget ? `${logTarget.factoryCode} · ${logTarget.calendarDate}` : ''" />
  <el-dialog v-model="batchVisible" title="批量设置生产日历" width="760px" :close-on-click-modal="false" class="calendar-dialog">
    <el-form label-width="110px" :disabled="batchSaving">
      <el-form-item label="工厂"><el-input :model-value="factoryCode" disabled /></el-form-item>
      <el-form-item label="日期范围" required><div class="date-range"><el-date-picker v-model="batchForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" /><span>至</span><el-date-picker v-model="batchForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" /></div></el-form-item>
      <el-form-item label="适用星期" required><el-checkbox-group v-model="batchForm.weekdays"><el-checkbox v-for="(day, index) in weekdays" :key="day" :value="index + 1">周{{ day }}</el-checkbox></el-checkbox-group></el-form-item>
      <el-form-item label="复制工厂"><el-select v-model="batchForm.copyFromFactory" placeholder="自行设置" clearable value-on-clear="" style="width:100%"><el-option v-for="factory in copyFactories" :key="factory" :value="factory" :label="factory" /></el-select></el-form-item>
      <template v-if="!batchForm.copyFromFactory">
        <el-form-item label="生产日"><el-switch v-model="batchForm.productionDay" active-text="生产" inactive-text="停产" /></el-form-item>
        <el-form-item label="整机日产能" :required="batchForm.productionDay"><el-input v-model="batchForm.dailyCapacity" :disabled="!batchForm.productionDay" placeholder="正整数（台/日）" /></el-form-item>
        <el-form-item label="单位"><el-input model-value="台" disabled /></el-form-item>
        <el-form-item label="备注"><el-input v-model="batchForm.reason" maxlength="500" /></el-form-item>
      </template>
      <el-form-item label="已有日期"><el-switch v-model="batchForm.overwrite" active-text="覆盖已有配置" inactive-text="保留已有配置" /></el-form-item>
    </el-form>
    <el-alert v-if="!batchPreview" title="先预览日期变更，再确认保存；默认保留已维护的单日特例。" type="info" :closable="false" />
    <template v-else>
      <el-alert :title="`新增 ${batchPreview.inserted} 日，覆盖 ${batchPreview.updated} 日，保留 ${batchPreview.preserved} 日`" type="info" :closable="false" />
      <el-table :data="batchPreview.rows" border max-height="240" class="preview-table"><el-table-column prop="calendarDate" label="日期" width="120" /><el-table-column label="预览状态" width="105"><template #default="{ row }">{{ stateLabel(row) }}</template></el-table-column><el-table-column label="整机日产能（台/日）" min-width="190"><template #default="{ row }">{{ capacityLabel(row) }}</template></el-table-column><el-table-column prop="capacityUnit" label="单位" width="80" /><el-table-column prop="reason" label="备注" min-width="130" /></el-table>
    </template>
    <template #footer><el-button :disabled="batchSaving" @click="batchVisible = false">取消</el-button><el-button type="primary" :loading="batchSaving" @click="previewBatch">预览</el-button><el-button type="primary" :disabled="!batchPreview || batchPreview.inserted + batchPreview.updated === 0" :loading="batchSaving" @click="saveBatch">确认</el-button></template>
  </el-dialog>
  <ConfigImportDialog v-model="importVisible" title="生产日历批量导入"
    :rules="['仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件', '工厂须为已维护且启用的工厂，日期格式为 yyyy-MM-dd', '生产日填写正整数整机日产能，单位固定为台；停产日产能为 0', '按工厂和日期新增或更新，文件里没有的日期不会被删除', '有错误时整份不导入，请下载错误数据修正后重新上传']"
    :columns="['工厂编码', '日期', '生产日', '日产能', '单位', '备注']"
    :preview="mrpApi.previewProductionCalendarImport" :commit="mrpApi.importProductionCalendar" :template="mrpApi.downloadProductionCalendarTemplate" :errors="mrpApi.downloadProductionCalendarErrors" @imported="load" />
</template>

<style scoped>
.page-header,.page-actions,.filter-bar,.list-filters,.date-range,.drawer-actions{display:flex;align-items:center;gap:10px}.page-header{justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-alert{margin-bottom:14px}.filter-bar{padding:14px 18px;background:#fff;border-radius:8px;margin-bottom:14px;flex-wrap:wrap}.view-switch{margin-left:auto}.summary-row{display:grid;grid-template-columns:1fr 2fr 1fr 1fr;gap:24px;background:#fff;padding:18px 22px;margin-bottom:14px;border-radius:8px}.summary-row span{display:block;color:#86909C;font-size:13px}.summary-row strong{display:block;color:#1F2329;font-size:20px;margin-top:8px;word-break:break-word}.summary-row .unconfigured-number{color:#C65300}.week-head,.calendar-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr))}.week-head span{text-align:center;color:#86909C;font-size:13px;padding:8px 0 14px}.calendar-grid{border-top:1px solid #E5E6EB;border-left:1px solid #E5E6EB}.day-cell{min-height:118px;border-right:1px solid #E5E6EB;border-bottom:1px solid #E5E6EB;padding:10px 12px;min-width:0}.day-cell:hover{background:#FAFCFF}.padding-cell{background:#FAFBFC}.rest-cell{background:#F7F8FA}.day-heading{display:flex;justify-content:space-between;align-items:center;gap:6px}.capacity-button{margin-top:12px;font-variant-numeric:tabular-nums;max-width:100%;white-space:normal;text-align:left;color:#4E5969}.day-reason{margin-top:8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:#86909C}.list-filters{margin-bottom:14px;flex-wrap:wrap}.pagination{margin-top:16px;justify-content:flex-end}.drawer-actions{justify-content:space-between}.field-hint{font-size:12px;color:#86909C}.log-value{white-space:pre-wrap;word-break:break-all;font-size:12px;color:#4E5969;margin-top:6px}.date-range{width:100%}.date-range :deep(.el-date-editor){flex:1;min-width:0}.preview-table{margin-top:14px}:deep(.calendar-dialog){border-radius:12px}@media(max-width:1000px){.summary-row{gap:14px}.day-cell{padding:8px;min-height:110px}.day-heading{flex-wrap:wrap}}
</style>
