<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mrpApi, type MonthlyPlanSummary, type DailyPlanView } from '../../../api/mrp'
import { quantityText } from '../../../utils/dailyDecimal'

const props = defineProps<{ batchId: number; ruleVersion?: string }>()
const machineCapacity = computed(() => props.ruleVersion === 'v3-whole-machine-capacity')
const selectedView = ref<DailyPlanView>(machineCapacity.value ? 'MACHINE_OUTPUT' : 'TERMINAL')
const loading = ref(false)
const rows = ref<MonthlyPlanSummary[]>([])
let sequence = 0
async function loadData() {
  const request = ++sequence
  loading.value = true
  try { const result = await mrpApi.getMonthlySummary(props.batchId, selectedView.value); if (request === sequence) rows.value = result }
  catch (error) { if (request === sequence) { rows.value = []; ElMessage.error('加载月汇总失败: ' + (error instanceof Error ? error.message : String(error))) } }
  finally { if (request === sequence) loading.value = false }
}
function changeView(value: string | number | boolean | undefined) {
  if (value !== 'MACHINE_OUTPUT' && value !== 'TERMINAL') return
  selectedView.value = value; void loadData()
}
onMounted(loadData)
</script>

<template>
  <div class="summary-container" v-loading="loading">
    <el-radio-group v-if="machineCapacity" :model-value="selectedView" @update:model-value="changeView" class="summary-switch"><el-radio-button value="MACHINE_OUTPUT">整机产出</el-radio-button><el-radio-button value="TERMINAL">末级备料</el-radio-button></el-radio-group>
    <el-alert :title="selectedView === 'MACHINE_OUTPUT' ? '整机产出按台汇总，独立于末级备料；汇总只读。' : '末级备料按单位分别汇总，未知单位按物料独立列示；汇总只读。'" type="info" :closable="false" class="summary-alert" />
    <el-table v-if="rows.length" :data="rows" border>
      <el-table-column prop="monthKey" label="业务月" min-width="180" />
      <el-table-column prop="planCount" label="日排产记录" width="140" align="right" />
      <el-table-column :label="selectedView === 'MACHINE_OUTPUT' ? '整机产量（台）' : '末级备料数量'" width="180" align="right"><template #default="{row}">{{ quantityText(row.totalQuantity) }}</template></el-table-column>
      <el-table-column label="单位 / 物料" min-width="160"><template #default="{row}">{{ row.materialUnit || ('单位未知 · ' + (row.aggregationKey || '按物料独立汇总')) }}</template></el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" description="暂无月汇总数据" />
  </div>
</template>

<style scoped>
.summary-container{padding:16px 0}.summary-alert,.summary-switch{margin-bottom:12px}
</style>
