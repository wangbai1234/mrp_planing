<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { mrpApi, type CategoryRoleVersion, type ForecastMaterialSummary, type ForecastVersion, type FactoryWarehouseMappingDetail, type InventorySnapshot, type BusinessWeekDetail } from '../../../api/mrp'
import { businessLabel } from '../../../utils/mrpDisplay'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [number] }>()
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const factories = ref<string[]>([])
const forecasts = ref<ForecastVersion[]>([])
const inventorySnapshots = ref<InventorySnapshot[]>([])
// The snapshot header may have no warehouse: a single import contains both factories.
const snapshotsByWarehouse = ref<Record<string, number[]>>({})
const currentBomCount = ref(0)
const categoryRoles = ref<CategoryRoleVersion[]>([])
const mappings = ref<FactoryWarehouseMappingDetail[]>([])
const businessWeeks = ref<BusinessWeekDetail[]>([])
const forecastMaterials = ref<ForecastMaterialSummary[]>([])
const factoryCode = ref('ALL')
const forecastVersionId = ref<number | null>(null)
const inventorySnapshotId = ref<number | null>(null)
const factoryOptions = computed(() => factories.value)
const canSelectAll = computed(() => factoryOptions.value.length > 1)
const selectedFactories = computed(() => factoryCode.value === 'ALL' ? (canSelectAll.value ? factoryOptions.value : []) : factoryOptions.value.filter(code => code === factoryCode.value))
const activeMappings = computed(() => mappings.value.filter(item => item.enabled && selectedFactories.value.includes(item.factoryCode)))
const missingFactories = computed(() => selectedFactories.value.filter(code => !activeMappings.value.some(item => item.factoryCode === code)))
const unconfirmedWarehouses = computed(() => activeMappings.value.filter(item => item.qualityConfigured !== true).map(item => item.warehouseName || item.warehouseCode))
const availableSnapshots = computed(() => inventorySnapshots.value.filter(item => item.status === 'IMPORTED'))
const requiredWarehouses = computed(() => [...new Set(activeMappings.value.map(item => item.warehouseCode))])
const missingWarehouses = computed(() => requiredWarehouses.value.filter(code => !snapshotsByWarehouse.value[code]?.includes(inventorySnapshotId.value!)))
const warehouseLabel = (code: string) => activeMappings.value.find(item => item.warehouseCode === code)?.warehouseName || code
const requiredMonths = computed(() => [...new Set(forecastMaterials.value.filter(item => selectedFactories.value.includes(item.factoryCode)).flatMap(item => Object.keys(item.months || {}).map(month => month.substring(0, 7))))].sort())
const calendarMonths = computed(() => {
  const months = new Set(requiredMonths.value)
  for (const month of requiredMonths.value) {
    const [year, number] = month.split('-').map(Number)
    const previous = new Date(year!, number! - 2, 1)
    months.add(`${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`)
  }
  return [...months].sort()
})
const maintainedMonths = computed(() => new Set(businessWeeks.value.map(item => item.businessMonth?.substring(0, 7))))
const missingMonths = computed(() => calendarMonths.value.filter(month => !maintainedMonths.value.has(month)))
const hasRoles = computed(() => categoryRoles.value.some(item => item.status === 'PUBLISHED'))
const ready = computed(() => Boolean(!loading.value && !loadError.value && selectedFactories.value.length && !missingFactories.value.length && !unconfirmedWarehouses.value.length && forecastVersionId.value && availableSnapshots.value.some(item => item.id === inventorySnapshotId.value) && requiredWarehouses.value.length && !missingWarehouses.value.length && currentBomCount.value > 0 && hasRoles.value && requiredMonths.value.length && !missingMonths.value.length))

function close() { emit('update:modelValue', false) }
function errorMessage(error: unknown) { return error instanceof Error ? error.message : String(error) }
let loadSequence = 0
let materialSequence = 0
async function load() {
  const sequence = ++loadSequence
  loading.value = true; loadError.value = ''; inventorySnapshotId.value = null
  forecastMaterials.value = []; snapshotsByWarehouse.value = {}
  try {
    const [lines, forecastList, config, mappingList, weekList, bom, snapshots] = await Promise.all([
      mrpApi.getFactories(), mrpApi.getForecastVersions(), mrpApi.getBatchCreationVersions(), mrpApi.getFactoryWarehouseMappings(), mrpApi.getBusinessWeeks(), mrpApi.getCurrentBomStatus(), mrpApi.getInventorySnapshots()
    ])
    if (sequence !== loadSequence) return
    factories.value = lines || []; forecasts.value = (forecastList || []).filter(item => item.status === 'IMPORTED')
    categoryRoles.value = config?.categoryRoleVersions || []; mappings.value = mappingList || []
    businessWeeks.value = weekList || []; currentBomCount.value = bom?.localCount || 0; inventorySnapshots.value = snapshots || []
    factoryCode.value = canSelectAll.value ? 'ALL' : factoryOptions.value[0] || ''
    const currentForecast = await mrpApi.getCurrentForecastVersion()
    forecastVersionId.value = currentForecast?.id ?? null
    const warehouseCodes = [...new Set(mappings.value.filter(item => item.enabled && factoryOptions.value.includes(item.factoryCode)).map(item => item.warehouseCode))]
    const coverage = await Promise.all(warehouseCodes.map(async code => [code, (await mrpApi.getInventorySnapshots(code) || []).filter(item => item.status === 'IMPORTED').map(item => item.id)] as const))
    if (sequence !== loadSequence) return
    snapshotsByWarehouse.value = Object.fromEntries(coverage)
    await refreshMaterials()
  } catch (error: unknown) {
    if (sequence === loadSequence) loadError.value = '加载排产准备数据失败: ' + errorMessage(error)
  } finally { if (sequence === loadSequence) loading.value = false }
}
async function refreshMaterials() {
  const sequence = ++materialSequence
  forecastMaterials.value = []
  const materials = forecastVersionId.value ? await mrpApi.getForecastMaterials(forecastVersionId.value) : []
  if (sequence === materialSequence) forecastMaterials.value = materials || []
}
async function submit() {
  if (!ready.value || submitting.value) return
  submitting.value = true
  try {
    // The server validates current synced BOM and records its checksum at creation.
    const batch = await mrpApi.createBatch(factoryCode.value, forecastVersionId.value!, inventorySnapshotId.value!)
    ElMessage.success('本期排产已创建'); close(); emit('created', batch.id)
  } catch (error: unknown) { ElMessage.error('创建失败: ' + errorMessage(error)) }
  finally { submitting.value = false }
}
watch(() => props.modelValue, value => {
  if (value) void load()
  else { ++loadSequence; ++materialSequence }
}, { immediate: true })
watch(forecastVersionId, () => {
  if (props.modelValue && !loading.value) refreshMaterials().catch(error => ElMessage.error('加载经营计划失败: ' + errorMessage(error)))
})
</script>

<template>
  <el-dialog :model-value="modelValue" title="生成排产" width="680px" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <el-alert title="按所选工厂使用经营计划和库存；BOM读取管理页面当前同步数据，同步变化后需重新创建本批次。" type="info" :closable="false" class="create-alert" />
      <el-form label-width="100px">
        <el-form-item label="工厂" required><el-select v-model="factoryCode" style="width:100%"><el-option v-if="canSelectAll" label="全部" value="ALL" /><el-option v-for="code in factoryOptions" :key="code" :label="code" :value="code" /></el-select></el-form-item>
        <el-form-item label="经营计划" required><el-select v-model="forecastVersionId" placeholder="选择经营计划" style="width:100%"><el-option v-for="version in forecasts" :key="version.id" :label="`V${version.versionNo} · ${version.fileName || '经营计划'}`" :value="version.id" /></el-select></el-form-item>
        <el-form-item label="库存版本" required><el-select v-model="inventorySnapshotId" placeholder="选择本次统一导入的库存版本" style="width:100%"><el-option v-for="snapshot in availableSnapshots" :key="snapshot.id" :label="`#${snapshot.id} · ${snapshot.snapshotDate || '未填写日期'} · ${snapshot.fileName || businessLabel(snapshot.status)}`" :value="snapshot.id" /></el-select></el-form-item>
      </el-form>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"><el-button type="primary" link @click="load">重新加载</el-button></el-alert>
      <template v-else-if="!loading">
        <el-alert v-if="!factoryOptions.length" title="暂无可用工厂" type="warning" :closable="false" />
        <el-alert v-else-if="missingFactories.length" :title="`尚未在仓库管理中维护工厂仓库：${missingFactories.join('、')}`" type="warning" :closable="false" />
        <el-alert v-else-if="unconfirmedWarehouses.length" :title="`请先在仓库管理中确认良品属性：${unconfirmedWarehouses.join('、')}`" type="warning" :closable="false" />
        <el-alert v-else-if="!forecasts.length" title="暂无已导入的经营计划" type="warning" :closable="false" />
        <el-alert v-else-if="!requiredMonths.length" title="经营计划中暂无所选工厂的数据" type="warning" :closable="false" />
        <el-alert v-else-if="!availableSnapshots.length" title="暂无已导入的库存版本" type="warning" :closable="false" />
        <el-alert v-else-if="inventorySnapshotId && missingWarehouses.length" :title="`所选库存版本缺少对应仓库数据：${missingWarehouses.map(warehouseLabel).join('、')}，请重新选择或统一导入库存。`" type="warning" :closable="false" />
        <el-alert v-else-if="currentBomCount === 0" title="尚无当前同步的 BOM，请先完成 NC 同步" type="warning" :closable="false" />
        <el-alert v-else-if="!hasRoles" title="尚无已发布的分类角色配置，请先维护计算配置" type="warning" :closable="false" />
        <el-alert v-else-if="missingMonths.length" :title="`尚未维护业务周：${missingMonths.join('、')}`" type="warning" :closable="false" />
      </template>
    </div>
    <template #footer><el-button @click="close">取消</el-button><el-button type="primary" :loading="submitting" :disabled="!ready" @click="submit">确认</el-button></template>
  </el-dialog>
</template>

<style scoped>
.create-alert { margin-bottom: 18px; }
</style>
