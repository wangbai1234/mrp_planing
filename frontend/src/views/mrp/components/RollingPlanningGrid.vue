<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { ElButton, ElMessage, TableV2FixedDir, type Column, type TableV2Instance } from 'element-plus'
import { mrpApi, type DailyGridResponse, type DailyGridRow, type DayCapacityLoad, type PlanningCellFact, type LinkedTransferInput, type LinkedTransferPreview } from '../../../api/mrp'
import { decimalText, quantityText, scaledQuantity } from '../../../utils/dailyDecimal'
import { businessLabel, supplyModeLabel } from '../../../utils/mrpDisplay'
import { usePermissionStore } from '../../../stores/permission'

type Period = { key: string; title: string; subtitle: string; days: string[] }
type DisplayRow = DailyGridRow & { kind?: 'load' }
const props = withDefaults(defineProps<{ batchId: number; mode: 'day' | 'week' | 'month'; view?: 'MACHINE_OUTPUT' | 'TERMINAL'; rootMaterialCode?: string; factoryCode?: string; sourceMonth?: string; focusDate?: string; readOnly?: boolean }>(), { readOnly: false, view: 'MACHINE_OUTPUT' })
const emit = defineEmits<{ changed: [] }>()
const permission = usePermissionStore()
const canEdit = computed(() => !props.readOnly && ['v4-rolling-business-weeks', 'v5-shifted-business-weeks'].includes(data.value?.ruleVersion || '') && props.mode === 'day' && permission.hasPermission('mrp_plan:adjust'))
const loading = ref(false); const error = ref(''); const data = shallowRef<DailyGridResponse | null>(null)
const page = ref(1); const pageSize = ref(20); const factory = ref(props.factoryCode || ''); const keyword = ref(''); const appliedKeyword = ref(''); const locateMonth = ref('')
let focusedDate = ''
const table = ref<TableV2Instance>()
const days = computed(() => data.value?.calendarDays || [])
const loads = computed(() => data.value?.capacityLoads || [])
const months = computed(() => [...new Set(days.value.map(day => day.slice(0, 7)))])
const loadIndex = computed(() => new Map(loads.value.map(item => [`${item.factoryCode}|${item.date}`, item])))
const loadFor = (code: string, date: string) => loadIndex.value.get(`${code}|${date}`)
const machineBasis = computed(() => data.value?.capacityBasis === 'MACHINE_OUTPUT')
const legacyPlan = computed(() => Boolean(data.value && !['v4-rolling-business-weeks', 'v5-shifted-business-weeks'].includes(data.value.ruleVersion || '')))
const factories = computed(() => [...new Set(loads.value.map(item => item.factoryCode).concat(data.value?.rows.map(row => row.factoryCode) || []))])
const periods = computed<Period[]>(() => {
  if (props.mode === 'day') return days.value.map(day => {
    const week = data.value?.weekGroups.find(item => item.days.includes(day))
    return { key: day, title: day.slice(5).replace('-', '/'), subtitle: week?.weekKey || '未归属业务周', days: [day] }
  })
  if (props.mode === 'month') return months.value.map(month => ({ key: month, title: `${month.slice(0, 4)}年${Number(month.slice(5))}月`, subtitle: '实际生产月', days: days.value.filter(day => day.startsWith(month)) }))
  const covered = new Set<string>()
  const result = (data.value?.weekGroups || []).map(week => {
    week.days.forEach(day => covered.add(day))
    return { key: week.weekKey, title: week.weekKey, subtitle: `${week.weekStart.slice(5)}–${week.weekEnd.slice(5)}`, days: week.days }
  })
  const unassigned = days.value.filter(day => !covered.has(day))
  if (unassigned.length) result.push({ key: 'unassigned', title: '未归属业务周', subtitle: '请检查业务周配置', days: unassigned })
  return result
})
function total(values: Array<string | number | null | undefined>) { return decimalText(values.reduce<bigint>((sum, value) => sum + (scaledQuantity(value ?? '0') || 0n), 0n)) }
function periodQuantity(row: DisplayRow, period: Period) {
  return total(period.days.map(day => row.kind === 'load' ? loadFor(row.factoryCode, day)?.load || '0' : row.dayQuantities[day] || '0'))
}
const fixedRows = computed<DisplayRow[]>(() => machineBasis.value && props.view === 'MACHINE_OUTPUT' ? factories.value.filter(code => !factory.value || code === factory.value).map(code => ({ rowKey: `load-${code}`, kind: 'load', factoryCode: code, rootMaterialCode: '整机负荷', terminalMaterialCode: '全厂整机汇总', terminalType: 'MACHINE_OUTPUT', supplyMode: '', pathGroupKey: '', dayQuantities: {}, weekTotals: {}, rowTotal: total(loads.value.filter(load => load.factoryCode === code).map(load => load.load)), materialUnit: '台' })) : [])
function isOver(code: string, dates: string[]) { return dates.some(day => ['OVERLOAD','REST_LOAD'].includes(loadFor(code, day)?.status || '')) }
// Exact decimal totals are prepared once, outside the scrolling render path.
const cellValues = computed(() => {
  const result = new Map<string, Map<string, { text: string; over: boolean; rest: boolean; unassessed: boolean; positive: boolean }>>()
  const rows: DisplayRow[] = [...(data.value?.rows || []), ...fixedRows.value]
  for (const row of rows) {
    const cells = new Map<string, { text: string; over: boolean; rest: boolean; unassessed: boolean; positive: boolean }>()
    for (const period of periods.value) {
      const value = periodQuantity(row, period)
      const load = loadFor(row.factoryCode, period.days[0] || '')
      cells.set(period.key, {
        text: row.kind === 'load' && props.mode === 'day' ? `已排 ${quantityText(value)} 台\n日产能 ${load?.dailyCapacity == null ? '未配置' : quantityText(load.dailyCapacity) + ' 台'}` : quantityText(value),
        over: isOver(row.factoryCode, period.days), rest: props.mode === 'day' && load?.productionDay === false,
        unassessed: row.kind === 'load' && props.mode === 'day' && load?.status === 'UNASSESSED',
        positive: (scaledQuantity(value) || 0n) > 0n
      })
    }
    result.set(row.rowKey, cells)
  }
  return result
})
function quantityCell(row: DisplayRow, period: Period) {
  const cell = cellValues.value.get(row.rowKey)?.get(period.key)
  if (!cell) return h('span')
  const isLoad = row.kind === 'load'
  return h(ElButton, { text: true, title: cell.text.replace('\n', ' · '), class: ['period-cell', { 'cell-over': cell.over && (isLoad || cell.positive), 'cell-rest': cell.rest, 'cell-load': isLoad }], disabled: !isLoad && !cell.positive, onClick: () => openDetails(row, period) }, () => [h('span', { class: 'cell-lines' }, cell.text.split('\n').map(line => h('span', { class: 'cell-line' }, line))), cell.over ? h('small', isLoad && props.mode === 'day' ? `超出 ${quantityText(loadFor(row.factoryCode, period.days[0] || '')?.excess)} 台` : '超产能') : cell.unassessed ? h('small', '未评估') : cell.rest ? h('small', '非生产日') : null])
}
const scrollLeft = ref(0); const viewportWidth = ref(1200)
const periodWidth = computed(() => props.mode === 'day' ? 128 : 150)
function onScroll(position: { scrollLeft: number }) { scrollLeft.value = position.scrollLeft }
function onResize(size: { width: number }) { viewportWidth.value = size.width }
// Table V2 virtualizes rows only. Spacers preserve the complete horizontal axis.
const columnWindow = computed(() => {
  if (props.mode === 'month') return { start: 0, end: periods.value.length }
  const width = periodWidth.value
  const start = Math.min(Math.max(0, periods.value.length - 1), Math.max(0, Math.floor(scrollLeft.value / width) - 2))
  const end = Math.max(start, Math.min(periods.value.length, Math.ceil((scrollLeft.value + Math.max(width, viewportWidth.value - 660)) / width) + 2))
  return { start, end }
})
function spacer(key: string, width: number): Column<DisplayRow> {
  return { key, title: '', width, cellRenderer: () => h('span'), headerCellRenderer: () => h('span') }
}
const columns = computed<Column<DisplayRow>[]>(() => [
  { key: 'root', dataKey: 'rootMaterialCode', title: '整机料号', width: 130, fixed: TableV2FixedDir.LEFT, cellRenderer: ({ rowData }) => h('strong', rowData.rootMaterialCode) },
  ...(props.view === 'TERMINAL' ? [{ key: 'terminal', dataKey: 'terminalMaterialCode', title: '末级需求物料', width: 155, fixed: TableV2FixedDir.LEFT, cellRenderer: ({ rowData }: { rowData: DisplayRow }) => h('div', [h('div', rowData.terminalMaterialCode), h('small', { class: 'muted' }, rowData.kind === 'load' ? '不随物料筛选变化' : rowData.materialUnit || '单位未配置')]) }, { key: 'type', title: '类型 / 供应', width: 125, fixed: TableV2FixedDir.LEFT, cellRenderer: ({ rowData }: { rowData: DisplayRow }) => h('div', rowData.kind === 'load' ? '整机 / 台' : `${businessLabel(rowData.terminalType)} / ${supplyModeLabel(rowData.supplyMode)}`) }] : []),
  ...(props.view === 'MACHINE_OUTPUT' && data.value?.ruleVersion === 'v5-shifted-business-weeks' ? [] : [{ key: 'month', dataKey: 'sourceMonth', title: '来源月', width: 100, fixed: TableV2FixedDir.LEFT, cellRenderer: ({ rowData }: { rowData: DisplayRow }) => h('span', rowData.sourceMonth?.slice(0, 7) || '—') }]),
  { key: 'factory', title: '工厂 / 仓库', width: 140, fixed: TableV2FixedDir.LEFT, cellRenderer: ({ rowData }) => h('div', [h('div', rowData.factoryCode), h('small', { class: 'muted' }, rowData.warehouseName || rowData.warehouseCode || (rowData.kind === 'load' ? '已排 / 日产能' : '未映射'))]) },
  ...(columnWindow.value.start ? [spacer('periods-before', columnWindow.value.start * periodWidth.value)] : []),
  ...periods.value.slice(columnWindow.value.start, columnWindow.value.end).map(period => ({ key: period.key, title: period.title, width: periodWidth.value, align: 'center' as const, headerCellRenderer: () => h('div', { class: 'period-heading' }, [h('div', period.title), h('small', period.subtitle), props.mode === 'day' ? h('small', `周${['日','一','二','三','四','五','六'][new Date(`${period.key}T00:00:00`).getDay()]}`) : null]), cellRenderer: ({ rowData }: { rowData: DisplayRow }) => quantityCell(rowData, period) })),
  ...(columnWindow.value.end < periods.value.length ? [spacer('periods-after', (periods.value.length - columnWindow.value.end) * periodWidth.value)] : []),
  { key: 'total', title: '全计划合计', width: 110, fixed: TableV2FixedDir.RIGHT, align: 'right', cellRenderer: ({ rowData }) => h('span', rowData.kind === 'load' ? '—' : quantityText(rowData.rowTotal)) }
])
let disposed = false; let gridSequence = 0
async function load() {
  const sequence = ++gridSequence; loading.value = true; error.value = ''
  try { const result = await mrpApi.getRollingGrid(props.batchId, { page: page.value, pageSize: pageSize.value, factoryCode: factory.value || undefined, keyword: appliedKeyword.value || undefined, rootMaterialCode: props.rootMaterialCode, view: props.view }); if (!disposed && sequence === gridSequence) { data.value = result; if (props.mode === 'day' && props.focusDate && props.focusDate !== focusedDate) { const period = periods.value.find(item => item.key === props.focusDate); const row = result.rows.find(item => item.rootMaterialCode === props.rootMaterialCode && (!props.factoryCode || item.factoryCode === props.factoryCode)); if (period && row) { focusedDate = props.focusDate; await nextTick(); table.value?.scrollToLeft(periods.value.indexOf(period) * periodWidth.value); await openDetails(row, period) } } } }
  catch (cause: unknown) { if (!disposed && sequence === gridSequence) { data.value = null; const code = (cause as { code?: string }).code; if (code !== 'NO_ACTIVE_RUN') error.value = '加载排产失败: ' + errorMessage(cause) } }
  finally { if (!disposed && sequence === gridSequence) loading.value = false }
}
function search() { page.value = 1; appliedKeyword.value = keyword.value.trim(); void load() }
function resetPage() { page.value = 1; void load() }
function locate(month: string) { const index = periods.value.findIndex(period => period.days.some(day => day.startsWith(month))); if (index >= 0) table.value?.scrollToLeft(index * periodWidth.value) }
function today() { const now = new Date(); const date = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`; const index = periods.value.findIndex(period => period.days.includes(date)); if (index < 0) { ElMessage.info('今天不在本次排产范围内'); return }; table.value?.scrollToLeft(index * periodWidth.value) }
function errorMessage(cause: unknown) { return cause instanceof Error ? cause.message : String(cause) }

const detailsVisible = ref(false); const detailsLoading = ref(false); const detailsError = ref(''); const detailPage = ref(1); const detailPageSize = ref(20); const detailTotal = ref(0); const detailRows = ref<PlanningCellFact[]>([])
const selection = ref<{ row: DisplayRow; period: Period } | null>(null)
let detailSequence = 0
async function openDetails(row: DisplayRow, period: Period) { selection.value = { row, period }; detailPage.value = 1; detailsVisible.value = true; await loadDetails() }
async function loadDetails() {
  const selected = selection.value; if (!selected) return
  const sequence = ++detailSequence; detailsLoading.value = true; detailsError.value = ''
  try { const result = await mrpApi.getPlanningCellDetails(props.batchId, { page: detailPage.value, pageSize: detailPageSize.value, factoryCode: selected.row.factoryCode, startDate: selected.period.days[0]!, endDate: selected.period.days[selected.period.days.length-1]!, rootMaterialCode: selected.row.kind === 'load' ? undefined : selected.row.rootMaterialCode, terminalMaterialCode: selected.row.kind === 'load' ? undefined : selected.row.terminalMaterialCode, sourceMonth: selected.row.kind === 'load' ? undefined : props.sourceMonth || selected.row.sourceMonth || undefined, view: props.view }); if (!disposed && sequence === detailSequence) { detailRows.value = result.items || []; detailTotal.value = result.total || 0 } }
  catch (cause: unknown) { if (sequence === detailSequence) { detailRows.value = []; detailsError.value = '加载日明细失败: ' + errorMessage(cause) } }
  finally { if (sequence === detailSequence) detailsLoading.value = false }
}
const transferVisible = ref(false); const moving = ref<PlanningCellFact | null>(null); const targetDate = ref(''); const machineQuantity = ref(''); const reason = ref(''); const preview = ref<LinkedTransferPreview | null>(null); const transferError = ref(''); const previewLoading = ref(false); const saving = ref(false); const edits = ref<Record<string,string>>({})
let previewSequence = 0
function protectedFact(fact: PlanningCellFact) { return ['EXECUTED','LOCKED'].includes(fact.executionStatus || '') }
function startTransfer(fact: PlanningCellFact) { moving.value = fact; targetDate.value = ''; machineQuantity.value = ''; reason.value = ''; preview.value = null; edits.value = {}; transferError.value = ''; transferVisible.value = true }
const input = computed<LinkedTransferInput | null>(() => moving.value && data.value?.activeRunId && targetDate.value && /^[1-9]\d{0,11}$/.test(machineQuantity.value.trim()) ? { rowKey: moving.value.transferRowKey || moving.value.rowKey, sourceDate: moving.value.scheduleDate, targetDate: targetDate.value, quantity: machineQuantity.value.trim(), expectedRunId: data.value.activeRunId } : null)
watch([targetDate,machineQuantity], () => { ++previewSequence; preview.value = null; edits.value = {}; transferError.value = ''; previewLoading.value = false })
async function makePreview() {
  const request = input.value; if (!request) { transferError.value = '请选择目标生产日，并输入正整数整机转移量'; return }
  const sequence = ++previewSequence; previewLoading.value = true; transferError.value = ''; preview.value = null
  try { const result = await mrpApi.previewLinkedTransfer(props.batchId, request); if (sequence !== previewSequence || disposed) return; const lines = result.lines.map(line => { const qty = scaledQuantity(line.suggestedQuantity); if (qty === null || qty % 1000000n !== 0n) throw new Error('接口返回了非整数转移建议，请刷新后重试'); return { ...line, suggestedQuantity: decimalText(qty) } }); preview.value = { ...result, lines }; edits.value = Object.fromEntries(lines.map(line => [line.rowKey, line.suggestedQuantity])) }
  catch (cause: unknown) { if (sequence === previewSequence) transferError.value = '预览失败: ' + errorMessage(cause) }
  finally { if (sequence === previewSequence) previewLoading.value = false }
}
function amount(key: string) { return edits.value[key] || '' }
const validTransfers = computed(() => Boolean(preview.value && preview.value.lines.every(line => { const text = amount(line.rowKey).trim(); const qty = scaledQuantity(text); const source = scaledQuantity(line.sourceQuantity); return /^\d{1,12}$/.test(text) && qty !== null && source !== null && qty <= source && text === (line.terminalType === 'MACHINE_OUTPUT' ? machineQuantity.value.trim() : line.suggestedQuantity) })))
function after(before: string, key: string, direction: -1 | 1) { const a = scaledQuantity(before); const q = scaledQuantity(amount(key)); return a !== null && q !== null && a + BigInt(direction) * q >= 0n ? quantityText(decimalText(a + BigInt(direction) * q)) : '—' }
function loadText(load: DayCapacityLoad | null | undefined, direction: -1 | 1) { if (!load) return '未评估'; const q = scaledQuantity(machineQuantity.value); const before = scaledQuantity(load.load); if (q === null || before === null) return '未评估'; return `${quantityText(load.load)} → ${quantityText(decimalText(before + BigInt(direction)*q))} / ${quantityText(load.dailyCapacity)} 台` }
async function confirmTransfer() {
  if (!input.value || !preview.value || !validTransfers.value || saving.value) return
  if (!reason.value.trim()) { transferError.value = '请填写调整原因'; return }
  saving.value = true; transferError.value = ''
  try { await mrpApi.commitLinkedTransfer(props.batchId, { ...input.value, fingerprint: preview.value.fingerprint, reason: reason.value.trim(), lines: preview.value.lines.map(line => ({ rowKey: line.rowKey, quantity: amount(line.rowKey).trim() })) }); transferVisible.value = false; ElMessage.success('联动调整已保存，各物料总量保持不变'); await load(); await loadDetails(); emit('changed') }
  catch (cause: unknown) { transferError.value = '调整失败: ' + errorMessage(cause); preview.value = null }
  finally { saving.value = false }
}
function disableTarget(value: Date) { const date = `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`; return date === moving.value?.scheduleDate || loadFor(moving.value?.factoryCode || '', date)?.productionDay === false }
watch(() => props.mode, async () => { scrollLeft.value = 0; locateMonth.value = ''; await nextTick(); table.value?.scrollToLeft(0) })
watch(() => props.rootMaterialCode, () => { page.value = 1; void load() })
watch(() => props.factoryCode, value => { factory.value = value || ''; page.value = 1; focusedDate = ''; void load() })
watch(() => props.focusDate, () => { focusedDate = ''; void load() })
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++gridSequence; ++detailSequence; ++previewSequence })
</script>

<template>
  <div class="rolling-grid" v-loading="loading">
    <el-alert v-if="legacyPlan" title="当前为旧规则计划，使用新版表格只读展示原排产；新建排产后按本月业务周数均分、整体前移一业务周，并支持整机联动调整。" type="info" :closable="false" class="compatibility-notice" />
    <div class="filter-bar"><el-select v-model="factory" placeholder="全部工厂" clearable style="width:140px" @change="resetPage"><el-option v-for="code in factories" :key="code" :label="code" :value="code" /></el-select><span class="spacer" /><el-input v-model="keyword" :placeholder="view === 'MACHINE_OUTPUT' ? '搜索整机料号' : '搜索整机 / 末级需求物料'" clearable style="width:220px" @keyup.enter="search" @clear="search" /></div>
    <div class="grid-toolbar"><span class="muted">{{ days[0] || '—' }} 至 {{ days.at(-1) || '—' }} · {{ mode === 'day' ? '完整连续日期' : mode === 'week' ? '按冻结业务周汇总' : '按实际生产自然月汇总' }}</span><div><el-select v-model="locateMonth" placeholder="定位月份" style="width:140px" @change="locate"><el-option v-for="month in months" :key="month" :label="month" :value="month" /></el-select><el-button @click="today">定位今天</el-button></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
    <div v-else-if="data && (data.rows.length || fixedRows.length)" class="virtual-table"><el-auto-resizer @resize="onResize"><template #default="{width,height}"><el-table-v2 ref="table" :columns="columns" :data="data.rows" :fixed-data="fixedRows" :width="width" :height="height" :row-height="72" :header-height="76" row-key="rowKey" fixed @scroll="onScroll" /></template></el-auto-resizer><el-empty v-if="!data.rows.length && fixedRows.length" class="grid-no-match" :style="{ top: `${76 + fixedRows.length * 72}px` }" description="暂无匹配的整机" /></div>
    <el-empty v-else-if="!loading" description="暂无匹配的排产数据" />
    <div class="grid-footer"><span class="muted">{{machineBasis ? '全厂已排负荷来自整机日事实；日产能来自冻结日历 · 点击日格查看来源' : '旧版本未记录整机产出，不展示整机产能负荷 · 点击数量查看来源明细'}}</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="data?.totalRows || 0" layout="total, sizes, prev, pager, next" @size-change="resetPage" @current-change="load" /></div>
    <el-drawer v-model="detailsVisible" :title="selection?.row.kind === 'load' ? '整机排产明细' : '数量与来源追溯'" size="850px" destroy-on-close>
      <div v-if="selection" class="detail-heading">{{selection.row.factoryCode}} · {{selection.period.title}} · {{selection.row.kind === 'load' ? '整机产出' : selection.row.terminalMaterialCode}}</div>
      <el-alert v-if="detailsError" :title="detailsError" type="error" :closable="false" />
      <el-table :data="detailRows" v-loading="detailsLoading" border max-height="480"><el-table-column prop="rootMaterialCode" label="整机料号" width="120" /><el-table-column label="物料" width="120"><template #default="{row}">{{row.terminalType === 'MACHINE_OUTPUT' ? '整机产出' : row.terminalMaterialCode}}</template></el-table-column><el-table-column label="来源月份" width="105"><template #default="{row}">{{row.sourceMonth?.slice(0,7)}}</template></el-table-column><el-table-column prop="scheduleDate" label="生产日期" width="115" /><el-table-column label="当前量" width="100"><template #default="{row}">{{quantityText(row.effectiveQuantity ?? row.systemQuantity)}} {{row.materialUnit || ''}}</template></el-table-column><el-table-column label="BOM路径" min-width="140"><template #default="{row}">{{row.pathGroupKey}}</template></el-table-column><el-table-column v-if="canEdit" label="操作" fixed="right" width="115"><template #default="{row}"><el-button v-if="row.terminalType === 'MACHINE_OUTPUT'" type="primary" link :disabled="protectedFact(row)" @click="startTransfer(row)">{{protectedFact(row) ? '已执行 / 锁定' : '联动调整'}}</el-button><span v-else class="muted">由整机负荷调整</span></template></el-table-column></el-table>
      <el-empty v-if="!detailsLoading && !detailsError && !detailRows.length" description="暂无日明细" /><el-pagination v-model:current-page="detailPage" :page-size="detailPageSize" :total="detailTotal" layout="total, prev, pager, next" class="detail-pagination" @current-change="loadDetails" />
    </el-drawer>
    <el-dialog v-model="transferVisible" title="整机与末级联动调整" width="980px" :close-on-click-modal="false" :close-on-press-escape="!saving">
      <template v-if="moving"><el-alert :title="`${moving.factoryCode} · ${moving.rootMaterialCode} · 来源 ${moving.sourceMonth.slice(0,7)} · 转出 ${moving.scheduleDate}（剩余 ${quantityText(moving.effectiveQuantity ?? moving.systemQuantity)} 台）`" type="info" :closable="false" />
      <el-form inline label-width="100px" class="transfer-form" :disabled="saving"><el-form-item label="目标生产日"><el-date-picker v-model="targetDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disableTarget" style="width:160px" /></el-form-item><el-form-item label="整机转移量"><el-input v-model="machineQuantity" inputmode="numeric" placeholder="正整数" style="width:150px" /></el-form-item><el-form-item><el-button type="primary" :loading="previewLoading" @click="makePreview">预览明细</el-button></el-form-item></el-form>
      <el-alert v-if="transferError" :title="transferError" type="error" :closable="false" /><template v-if="preview"><div class="load-preview"><div>转出日全厂整机负荷<strong>{{loadText(preview.sourceLoad,-1)}}</strong></div><div>转入日全厂整机负荷<strong>{{loadText(preview.targetLoad,1)}}</strong></div></div><el-table :data="preview.lines" border max-height="350"><el-table-column label="物料 / 单位" width="160"><template #default="{row}">{{row.terminalType === 'MACHINE_OUTPUT' ? moving.rootMaterialCode : row.terminalMaterialCode}} / {{row.materialUnit || '未知'}}<div class="muted">{{row.sourceMonth.slice(0,7)}}</div></template></el-table-column><el-table-column label="转出前 → 后" min-width="150"><template #default="{row}">{{quantityText(row.sourceQuantity)}} → {{after(row.sourceQuantity,row.rowKey,-1)}}</template></el-table-column><el-table-column label="转移数量（整数）" width="200"><template #default="{row}"><span>{{row.terminalType === 'MACHINE_OUTPUT' ? machineQuantity : row.suggestedQuantity}}</span><div class="muted">{{row.terminalType === 'MACHINE_OUTPUT' ? '排产员输入' : '系统按比例联动'}}</div></template></el-table-column><el-table-column label="转入前 → 后" min-width="150"><template #default="{row}">{{quantityText(row.targetQuantity)}} → {{after(row.targetQuantity,row.rowKey,1)}}</template></el-table-column><el-table-column label="校验" width="110"><template #default>各物料总量不变</template></el-table-column></el-table><div v-if="!validTransfers" class="validation-error">转移量必须为整数，且不能超过当前可转出量。</div></template>
      <el-form label-width="100px" class="transfer-form"><el-form-item label="调整原因" required><el-input v-model="reason" type="textarea" :rows="2" maxlength="300" show-word-limit :disabled="saving" /></el-form-item></el-form>
      </template><template #footer><el-button :disabled="saving" @click="transferVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!validTransfers || !reason.trim() || previewLoading" @click="confirmTransfer">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.compatibility-notice{margin-bottom:14px}.filter-bar,.grid-toolbar,.grid-footer{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.filter-bar{margin-bottom:14px}.spacer{flex:1}.grid-toolbar{justify-content:space-between;margin-bottom:12px}.grid-toolbar>div{display:flex;gap:8px}.muted{color:#86909c;font-size:12px}.virtual-table{position:relative;height:570px;border:1px solid #e5e6eb;border-radius:6px;overflow:hidden}.grid-no-match{position:absolute;left:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;background:#fff;pointer-events:none}.grid-footer{justify-content:space-between;margin-top:14px}.detail-heading{padding:8px 0 16px;color:#4e5969}.detail-pagination{margin-top:16px;justify-content:flex-end}.transfer-form{margin-top:20px}.load-preview{display:flex;gap:12px}.load-preview>div{flex:1;background:#f7f8fa;border-radius:6px;padding:12px;color:#4e5969}.load-preview strong{display:block;color:#1f2329;margin-top:6px}.validation-error{color:#f53f3f;margin-top:10px}:deep(.period-heading){display:flex;flex-direction:column;line-height:21px;text-align:center}:deep(.period-heading small){font-size:11px;color:#86909c}:deep(.period-cell){width:100%;height:100%;min-width:0;padding:4px!important;display:flex;flex-direction:column;justify-content:center;font-variant-numeric:tabular-nums;overflow:hidden}:deep(.period-cell>span){display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;min-width:0;max-width:100%}:deep(.cell-lines){display:flex;flex-direction:column;gap:2px;max-width:100%;line-height:16px}:deep(.cell-line){display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}:deep(.period-cell small){display:block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px;line-height:14px}:deep(.cell-over){background:#fff0f0!important;color:#d93030!important}:deep(.cell-rest:not(.cell-over)){background:#f7f8fa}:deep(.cell-load){font-weight:600}:deep(.el-table-v2__header-cell){background:#f7f8fa}:deep(.el-table-v2__cell){border-right:1px solid #ebeef5;padding:0 8px;font-size:12px}:deep(.el-table-v2__fixed-header-row){background:#f0f6ff}:deep(.el-dialog){border-radius:12px}
.load-preview{margin:16px 0}
</style>
