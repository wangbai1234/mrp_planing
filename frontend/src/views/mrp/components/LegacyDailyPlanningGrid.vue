<template>
  <div class="daily-planning-grid">
    <div class="grid-toolbar">
      <el-button-group>
        <el-button @click="moveWindow(-28)">前28天</el-button>
        <el-button @click="resetWindow">今天</el-button>
        <el-button @click="moveWindow(28)">后28天</el-button>
      </el-button-group>
      <el-radio-group v-if="machineCapacity" :model-value="selectedView" @update:model-value="changeView"><el-radio-button value="MACHINE_OUTPUT">整机产出</el-radio-button><el-radio-button value="TERMINAL">末级备料</el-radio-button></el-radio-group>
      <span class="window-text">{{ startDate }} 至 {{ endDate }}</span>
      <el-input v-model="keyword" :placeholder="selectedView === 'MACHINE_OUTPUT' ? '搜索整机料号' : '搜索末级物料'" clearable style="width: 220px" @keyup.enter="handleSearch" @clear="handleSearch" />
    </div>

    <el-alert :title="machineCapacity ? '整机日产能按整份计划的整机产出统计（台/日）。整机与末级备料分别调整并保持各自总量；整机调整不会自动搬移末级备料。' : '历史计划沿用自制末级物料及同单位产能口径。超产能后请跨日调整。'" type="info" :closable="false" show-icon class="grid-alert" />

    <div v-loading="loading" class="grid-card">
      <el-table v-if="capacityLoads.length" :data="capacityLoads" border height="190" class="capacity-summary" :row-class-name="({ row }: { row: DayCapacityLoad }) => row.status === 'OVERLOAD' || row.status === 'REST_LOAD' ? 'capacity-danger' : ''">
        <el-table-column prop="factoryCode" label="工厂" width="110" /><el-table-column prop="date" label="实际排产日" width="125" />
        <el-table-column :label="machineCapacity ? '整机已排产量' : '已排负荷'" width="130"><template #default="{ row }">{{ quantityText(row.load) }} {{ machineCapacity ? '台' : row.capacityUnit || '' }}</template></el-table-column>
        <el-table-column :label="machineCapacity ? '整机日产能' : '日产能'" width="130"><template #default="{ row }">{{ quantityText(row.dailyCapacity) }} {{ row.capacityUnit || '' }}</template></el-table-column>
        <el-table-column label="超出量" width="120"><template #default="{ row }">{{ quantityText(row.excess) }}</template></el-table-column>
        <el-table-column label="状态" min-width="190"><template #default="{ row }"><el-tag :type="loadTag(row)">{{ row.message }}</el-tag></template></el-table-column>
      </el-table>
      <el-table v-if="rows.length" :data="rows" border max-height="620" class="daily-table">
        <el-table-column prop="rootMaterialCode" label="整机料号" width="150" fixed="left" />
        <el-table-column v-if="selectedView !== 'MACHINE_OUTPUT'" prop="terminalMaterialCode" label="末级备料料号" width="160" fixed="left" />
        <el-table-column v-if="selectedView !== 'MACHINE_OUTPUT'" label="类型 / 供应" width="130" fixed="left">
          <template #default="{ row }"><el-tag size="small">{{ businessLabel(row.terminalType) }}</el-tag><span class="supply-text">{{ row.terminalType === 'MACHINE_OUTPUT' ? '台' : supplyModeLabel(row.supplyMode) }}</span></template>
        </el-table-column>
        <el-table-column v-if="selectedView === 'MACHINE_OUTPUT'" label="单位" width="80"><template #default>台</template></el-table-column>
        <el-table-column label="工厂 / 仓库" width="150" fixed="left">
          <template #default="{row}">{{ row.factoryCode }} / {{ row.warehouseName || row.warehouseCode || '未映射' }}</template>
        </el-table-column>
        <el-table-column v-for="week in displayWeekGroups" :key="week.weekKey" :label="weekLabel(week)" align="center">
          <el-table-column v-for="date in week.days" :key="date" :width="112" align="center" :class-name="dayInfo(date)?.productionDay ? '' : 'non-production-column'">
            <template #header><el-tooltip :content="dayInfo(date)?.reason || '生产日'" placement="top"><div><div>{{ dayLabel(date) }}</div><div class="weekday">{{ weekdayLabel(date) }}</div><div v-if="!dayInfo(date)?.productionDay" class="rest-label">非生产</div></div></el-tooltip></template>
            <template #default="{ row }">
              <el-button text class="day-cell" :class="{ manual: hasOverride(row, date), empty: !row.dayQuantities[date], readonly: !canEdit, overload: overloadedCell(row, date) }" :disabled="!row.dayQuantities[date] || !canEdit" @click="openEditor(row, date)">
                <span>{{ quantityText(row.dayQuantities[date]) }}</span>
                <small v-if="overloadedCell(row, date)">超产能</small><small v-else-if="hasOverride(row, date)">人工</small>
              </el-button>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="窗口合计" width="110" align="right" fixed="right"><template #default="{ row }">{{ quantityText(row.rowTotal) }}</template></el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="当前日期窗口暂无日排产数据" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="reloadFirstPage" @current-change="loadData" />
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="跨日调整排产" size="520px" destroy-on-close>
      <el-descriptions v-if="editing" :column="1" border>
        <el-descriptions-item label="来源整机">{{ editing.row.rootMaterialCode }}</el-descriptions-item>
        <el-descriptions-item label="排产物料">{{ editing.row.terminalMaterialCode }} / {{ editing.row.materialUnit || '单位未配置' }}</el-descriptions-item>
        <el-descriptions-item label="转出日期">{{ editing.date }}</el-descriptions-item>
        <el-descriptions-item label="系统值">{{ quantityText(editing.systemQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="当前生效值">{{ quantityText(editing.row.dayQuantities[editing.date]) }}</el-descriptions-item>
      </el-descriptions>
      <el-form v-if="editing" label-width="100px" class="edit-form" :disabled="saving">
        <el-form-item label="目标生产日" required><el-date-picker v-model="targetDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledTarget" @change="loadTarget" /></el-form-item>
        <el-form-item label="转移数量" required><el-input v-model="transferQuantity" :placeholder="editing.row.terminalType === 'MACHINE_OUTPUT' ? '正整数（台）' : '正数，最多6位小数'" /></el-form-item>
        <el-form-item label="调整原因" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="512" show-word-limit /></el-form-item>
      </el-form>
      <el-alert v-if="machineCapacity && editing" :title="editing.row.terminalType === 'MACHINE_OUTPUT' ? '本次只调整整机产出，不自动搬移末级备料。' : '本次只调整末级备料，整机产出与整机产能负荷保持不变。'" type="info" :closable="false" />
      <el-alert v-if="targetError" :title="targetError" type="error" :closable="false" />
      <div v-loading="targetLoading" class="transfer-preview">
        <el-descriptions v-if="transferPreview" title="调整后预览" :column="1" border>
          <el-descriptions-item label="转出日物料数量">{{ transferPreview.source }}</el-descriptions-item>
          <el-descriptions-item label="转入日物料数量">{{ transferPreview.target }}</el-descriptions-item>
          <el-descriptions-item label="转出日全厂负荷">{{ transferPreview.sourceLoad }}</el-descriptions-item>
          <el-descriptions-item label="转入日全厂负荷">{{ transferPreview.targetLoad }}</el-descriptions-item>
          <el-descriptions-item label="需求总量">保持不变</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer><el-button @click="drawerVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!transferPreview || targetLoading || Boolean(targetError)" @click="saveTransfer">确认</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { supplyModeLabel, businessLabel } from '../../../utils/mrpDisplay'
import { mrpApi, type DailyGridRow, type MrpDailyPlanDetail, type MrpDailyPlanOverride, type DayCapacityLoad, type DailyPlanView } from '../../../api/mrp'
import { quantityText, scaledQuantity, decimalText } from '../../../utils/dailyDecimal'
import { usePermissionStore } from '../../../stores/permission'

const props = withDefaults(defineProps<{ batchId: number; readOnly?: boolean }>(), { readOnly: false })
const emit = defineEmits<{ changed: [] }>()
const capacityLoads = ref<DayCapacityLoad[]>([])
const selectedView = ref<DailyPlanView | undefined>()
const capacityBasis = ref<DailyPlanView>('TERMINAL')
const machineCapacity = computed(() => capacityBasis.value === 'MACHINE_OUTPUT')
function changeView(value: string | number | boolean | undefined) {
  if (value !== 'MACHINE_OUTPUT' && value !== 'TERMINAL') return
  selectedView.value = value; drawerVisible.value = false; editing.value = null; ++targetSequence; targetLoading.value = false; page.value = 1; void loadData()
}
const activeRunId = ref<number | null>(null)
const targetDate = ref(''); const transferQuantity = ref(''); const targetLoading = ref(false); const targetError = ref('')
const targetLoad = ref<DayCapacityLoad | null>(null); const targetQuantity = ref('0'); const targetRevision = ref(-1)
const permissionStore = usePermissionStore()
const canEdit = computed(() => !props.readOnly && permissionStore.hasPermission('mrp_plan:adjust'))
const loading = ref(false)
const saving = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const rows = ref<DailyGridRow[]>([])
const calendarDays = ref<string[]>([])
const calendarDayInfo = ref<Array<{ date: string; productionDay: boolean; reason: string }>>([])
const weekGroups = ref<Array<{ weekKey: string; weekStart: string; weekEnd: string; days: string[] }>>([])
const keyword = ref('')
const start = ref(new Date())
const end = ref(new Date())
const overrides = ref<MrpDailyPlanOverride[]>([])
const rawPlans = ref<MrpDailyPlanDetail[]>([])
const drawerVisible = ref(false)
const editing = ref<{ row: DailyGridRow; date: string; systemQuantity: string } | null>(null)
const reason = ref('')

function localDate(value: Date) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}` }
const startDate = computed(() => localDate(start.value))
const endDate = computed(() => localDate(end.value))
function initializeWindow() { const now = new Date(); const day = now.getDay() || 7; start.value = new Date(now); start.value.setDate(now.getDate() - day + 1); end.value = new Date(start.value); end.value.setDate(start.value.getDate() + 27) }
function dayLabel(date: string) { const d = new Date(`${date}T00:00:00`); return `${d.getMonth() + 1}/${d.getDate()}` }
function weekdayLabel(date: string) { return `周${['日','一','二','三','四','五','六'][new Date(`${date}T00:00:00`).getDay()]}` }
function dayInfo(date: string) { return calendarDayInfo.value.find(item => item.date === date) }
function weekLabel(week: { weekKey: string; weekStart: string; weekEnd: string }) { return `${week.weekKey}  ${week.weekStart.slice(5)}–${week.weekEnd.slice(5)}` }
const displayWeekGroups = computed(() => {
  const configuredDays = new Set(weekGroups.value.flatMap(item => item.days))
  const result = weekGroups.value.map(item => ({ ...item, days: item.days.filter(day => calendarDays.value.includes(day)) })).filter(item => item.days.length)
  const unconfigured = calendarDays.value.filter(day => !configuredDays.has(day))
  if (unconfigured.length) result.push({ weekKey: '未配置业务周', weekStart: unconfigured[0], weekEnd: unconfigured[unconfigured.length - 1], days: unconfigured })
  return result
})
function overrideKey(factory: string, root: string, terminal: string, type: string, supply: string, path: string, date: string) { return [factory, root, terminal, type, supply, path, date].join('|') }
const overrideMap = computed(() => new Map(overrides.value.map(item => [overrideKey(item.factoryCode, item.rootMaterialCode, item.terminalMaterialCode, item.terminalType, item.supplyMode, item.pathGroupKey, item.scheduleDate), item])))
function rowOverride(row: DailyGridRow, date: string) { return overrideMap.value.get(overrideKey(row.factoryCode, row.rootMaterialCode, row.terminalMaterialCode, row.terminalType, row.supplyMode, row.pathGroupKey, date)) }
function hasOverride(row: DailyGridRow, date: string) { return Boolean(rowOverride(row, date)) }
const editingOverride = computed(() => editing.value ? rowOverride(editing.value.row, editing.value.date) : undefined)

let loadSequence = 0
async function loadData() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const [grid, overrideList, plans] = await Promise.all([
      mrpApi.getDailyGrid(props.batchId, { page: page.value, pageSize: pageSize.value, startDate: startDate.value, endDate: endDate.value, terminalMaterialCode: keyword.value || undefined, view: selectedView.value }),
      mrpApi.getDailyOverrides(props.batchId),
      mrpApi.getDailyPlans(props.batchId, startDate.value, endDate.value)
    ])
    if (sequence !== loadSequence) return
    capacityBasis.value = grid.capacityBasis || (grid.ruleVersion === 'v3-whole-machine-capacity' ? 'MACHINE_OUTPUT' : 'TERMINAL')
    selectedView.value = grid.view || selectedView.value || capacityBasis.value
    capacityLoads.value = grid.capacityLoads || []; activeRunId.value = grid.activeRunId; rows.value = grid.rows || []; total.value = grid.totalRows || 0; calendarDays.value = grid.calendarDays || []; calendarDayInfo.value = grid.calendarDayInfo || []; weekGroups.value = grid.weekGroups || []
    overrides.value = overrideList || []; rawPlans.value = plans || []
  } catch (e: unknown) {
    if (sequence !== loadSequence) return
    // Element Plus mounts disabled tabs too; a draft batch has no active run yet.
    // Keep that expected state empty and quiet instead of showing a false error.
    const error = e as { code?: string }
    if (error.code === 'NO_ACTIVE_RUN') {
      capacityLoads.value = []; rows.value = []; total.value = 0; weekGroups.value = []; calendarDayInfo.value = []; calendarDays.value = []; rawPlans.value = []; overrides.value = []; activeRunId.value = null
    } else {
      ElMessage.error('加载日排产失败: ' + errorMessage(e))
    }
  }
  finally { if (sequence === loadSequence) loading.value = false }
}
function errorMessage(error: unknown) { return error instanceof Error ? error.message : String(error) }
function reloadFirstPage() { page.value = 1; loadData() }
function handleSearch() { page.value = 1; loadData() }
function moveWindow(days: number) { start.value = new Date(start.value); end.value = new Date(end.value); start.value.setDate(start.value.getDate() + days); end.value.setDate(end.value.getDate() + days); page.value = 1; loadData() }
function resetWindow() { initializeWindow(); page.value = 1; loadData() }
function loadInfo(date: string, factory?: string) { return capacityLoads.value.find(item => item.date === date && (!factory || item.factoryCode === factory)) }
function loadTag(load: DayCapacityLoad) { return load.status === 'OVERLOAD' || load.status === 'REST_LOAD' ? 'danger' : load.status === 'UNASSESSED' ? 'warning' : 'success' }
function overloadedCell(row: DailyGridRow, date: string) {
  const load = loadInfo(date, row.factoryCode); const qty = scaledQuantity(row.dayQuantities[date])
  return Boolean(qty && qty > 0n && participatesInCapacity(row) && load && (!machineCapacity.value || load.capacityUnit === '台') &&
    (load.status === 'REST_LOAD' || (load.status === 'OVERLOAD' && row.materialUnit && row.materialUnit === load.capacityUnit)))
}
function participatesInCapacity(row: DailyGridRow) { return machineCapacity.value ? row.terminalType === 'MACHINE_OUTPUT' : row.supplyMode === 'SELF_MADE' }
function disabledTarget(value: Date) { return Boolean(editing.value && localDate(value) === editing.value.date) }
function openEditor(row: DailyGridRow, date: string) {
  if (!canEdit.value || !scaledQuantity(row.dayQuantities[date])) return
  const plan = rawPlans.value.find(p => p.factoryCode === row.factoryCode && p.rootMaterialCode === row.rootMaterialCode && p.terminalMaterialCode === row.terminalMaterialCode && p.terminalType === row.terminalType && p.supplyMode === row.supplyMode && p.pathGroupKey === row.pathGroupKey && p.scheduleDate === date)
  // A transfer into an empty production day has an override but no system plan.
  if (!plan && !rowOverride(row, date)) return
  editing.value = { row, date, systemQuantity: String(plan?.systemQuantity ?? '0') }
  ++targetSequence; targetLoading.value = false
  transferQuantity.value = ''; reason.value = ''; targetDate.value = ''; targetLoad.value = null; targetError.value = ''; drawerVisible.value = true
}
let targetSequence = 0
async function loadTarget() {
  const sequence = ++targetSequence; targetLoad.value = null; targetError.value = ''
  if (!editing.value || !targetDate.value) { targetLoading.value = false; return }
  targetLoading.value = true
  try {
    const [plans, loads, latestOverrides] = await Promise.all([mrpApi.getDailyPlans(props.batchId, targetDate.value, targetDate.value), mrpApi.getCapacityLoads(props.batchId, targetDate.value, targetDate.value), mrpApi.getDailyOverrides(props.batchId)])
    if (sequence !== targetSequence) return
    const row = editing.value.row
    const plan = plans.find(p => overrideKey(p.factoryCode,p.rootMaterialCode,p.terminalMaterialCode,p.terminalType,p.supplyMode,p.pathGroupKey,p.scheduleDate) === overrideKey(row.factoryCode,row.rootMaterialCode,row.terminalMaterialCode,row.terminalType,row.supplyMode,row.pathGroupKey,targetDate.value))
    targetQuantity.value = String(plan?.effectiveQuantity ?? plan?.systemQuantity ?? '0')
    const override = latestOverrides.find(p => overrideKey(p.factoryCode,p.rootMaterialCode,p.terminalMaterialCode,p.terminalType,p.supplyMode,p.pathGroupKey,p.scheduleDate) === overrideKey(row.factoryCode,row.rootMaterialCode,row.terminalMaterialCode,row.terminalType,row.supplyMode,row.pathGroupKey,targetDate.value))
    targetRevision.value = override?.revision ?? -1; targetLoad.value = loads.find(load => load.factoryCode === row.factoryCode) || null
    if (!targetLoad.value?.configured || !targetLoad.value.productionDay) targetError.value = '请选择本计划冻结日历中的生产日；需要更改日历时请生成修订版。'
  } catch (error: unknown) { if (sequence === targetSequence) targetError.value = '加载目标日期失败: ' + errorMessage(error) }
  finally { if (sequence === targetSequence) targetLoading.value = false }
}
function previewLoad(load: DayCapacityLoad | undefined | null, delta: bigint, row: DailyGridRow) {
  if (!load || load.status === 'UNASSESSED' || (machineCapacity.value && load.capacityUnit !== '台') || (participatesInCapacity(row) && row.materialUnit !== load.capacityUnit)) return '产能未评估'
  const current = scaledQuantity(load.load); const cap = scaledQuantity(load.dailyCapacity)
  if (current == null || cap == null) return '产能未评估'
  const after = current + (participatesInCapacity(row) ? delta : 0n)
  const status = after > cap ? '超产能 ' + quantityText(decimalText(after - cap)) : load.partiallyUnassessed ? '产能未评估' : load.productionDay ? '正常' : after > 0n ? '停产日存在排产' : '停产'
  return `${quantityText(decimalText(after))} / ${quantityText(decimalText(cap))} ${load.capacityUnit || ''} · ${status}${load.partiallyUnassessed ? '；部分需求未评估' : ''}`
}
const transferPreview = computed(() => {
  if (!editing.value || !targetDate.value || targetDate.value === editing.value.date || !targetLoad.value) return null
  const qty = scaledQuantity(transferQuantity.value); const source = scaledQuantity(editing.value.row.dayQuantities[editing.value.date]); const target = scaledQuantity(targetQuantity.value)
  if (editing.value.row.terminalType === 'MACHINE_OUTPUT' && (qty === null || qty % 1000000n !== 0n)) return null
  if (qty == null || qty <= 0n || source == null || target == null || qty > source || target + qty > 999999999999999999n) return null
  return { source: quantityText(decimalText(source - qty)), target: quantityText(decimalText(target + qty)), sourceLoad: previewLoad(loadInfo(editing.value.date, editing.value.row.factoryCode), -qty, editing.value.row), targetLoad: previewLoad(targetLoad.value, qty, editing.value.row) }
})
async function saveTransfer() {
  if (saving.value || !canEdit.value || targetLoading.value || targetError.value) return
  if (!editing.value || !transferPreview.value || activeRunId.value == null) { ElMessage.warning('请检查目标日期和转移数量'); return }
  if (!reason.value.trim()) { ElMessage.warning('请填写调整原因'); return }
  saving.value = true
  try {
    await mrpApi.transferDaily(props.batchId, {rowKey: editing.value.row.rowKey, sourceDate: editing.value.date, targetDate: targetDate.value, quantity: transferQuantity.value.trim(), reason: reason.value.trim(), expectedRunId: activeRunId.value, expectedSourceQuantity: editing.value.row.dayQuantities[editing.value.date] || '0', expectedTargetQuantity: targetQuantity.value, expectedSourceRevision: editingOverride.value?.revision ?? -1, expectedTargetRevision: targetRevision.value})
    ElMessage.success('跨日调整已保存，需求总量不变'); drawerVisible.value = false; await loadData(); emit('changed')
  } catch (error: unknown) { ElMessage.error('调整失败: ' + errorMessage(error)) }
  finally { saving.value = false }
}
onMounted(() => { initializeWindow(); loadData() })
</script>

<style scoped>
.grid-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 12px; }
.window-text { color: #4E5969; font-size: 13px; }
.grid-alert { margin-bottom: 12px; }
.grid-card { min-height: 180px; }
.daily-table :deep(.el-table__cell) { padding: 0; }
.daily-table :deep(th.el-table__cell) { padding: 8px 0; }
.weekday { color: #86909C; font-size: 11px; margin-top: 2px; }
.rest-label { color: #F53F3F; font-size: 10px; margin-top: 2px; }
.daily-table :deep(.non-production-column) { background: #F7F8FA; }
.supply-text { display: block; color: #86909C; font-size: 11px; margin-top: 3px; }
.capacity-summary { margin-bottom:16px; }
.capacity-summary :deep(.capacity-danger td) { background:#FFF1F0; color:#F53F3F; }
.transfer-preview { margin-top:16px; }
.day-cell.overload { background:#FFECE8; color:#F53F3F; font-weight:600; }
.day-cell :deep(span) { display:block; }
.day-cell { width: 100%; min-height: 48px; border: 0; background: transparent; color: #1F2329; cursor: pointer; }
.day-cell:hover:not(:disabled) { background: #F2F6FF; }
.day-cell.manual { box-shadow: inset 0 -3px #1677FF; color: #1677FF; }
.day-cell small { display: block; font-size: 10px; margin-top: 2px; }
.day-cell.empty { color: #C9CDD4; cursor: default; }
.day-cell.readonly { cursor: default; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
.edit-form { margin-top: 18px; }
</style>
