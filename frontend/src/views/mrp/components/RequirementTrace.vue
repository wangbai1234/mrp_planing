<template>
  <div v-loading="loading">
    <el-alert title="从整机经营计划开始，按 BOM 层级依次扣减良品库存；标记为末级的净需求进入日排产。" type="info" :closable="false" show-icon class="trace-alert" />
    <el-table v-if="rows.length" :data="rows" border max-height="620">
      <el-table-column prop="rootMaterialCode" label="整机料号" width="150" fixed="left" />
      <el-table-column prop="levelNo" label="层级" width="70" align="center" />
      <el-table-column prop="currentMaterialCode" label="当前物料料号" width="160" />
      <el-table-column prop="parentMaterialCode" label="上级物料" width="150" />
      <el-table-column prop="categoryRole" label="物料类型" width="110"><template #default="{ row }">{{ businessLabel(row.categoryRole) }}</template></el-table-column>
      <el-table-column label="供应 / 下钻" width="130"><template #default="{ row }"><div>{{ supplyModeLabel(row.supplyMode) }}</div><div class="sub-text">{{ row.effectiveExplosionPolicy ? businessLabel(row.effectiveExplosionPolicy) + (row.policySource === 'DEFAULT' ? '（默认）' : '') : businessLabel(row.explosionPolicy) }}</div></template></el-table-column>
      <el-table-column prop="grossQty" label="毛需求" width="100" align="right" />
      <el-table-column prop="inventoryBeforeQty" label="扣减前库存" width="110" align="right" />
      <el-table-column prop="inventoryUsedQty" label="本层扣库存" width="110" align="right" />
      <el-table-column prop="inventoryAfterQty" label="扣减后库存" width="110" align="right" />
      <el-table-column prop="netQty" label="净需求" width="100" align="right" />
      <el-table-column label="排产终点" width="150"><template #default="{ row }"><el-tag v-if="row.terminalFlag" type="success" size="small">是</el-tag><span v-else>-</span><div v-if="row.terminalReason" class="sub-text">{{ terminalReasonLabel(row.terminalReason) }}</div></template></el-table-column>
      <el-table-column prop="pathText" label="BOM 路径" min-width="260" show-overflow-tooltip />
    </el-table>
    <el-empty v-else-if="!loading" description="暂无需求追溯数据" />
    <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="handleSizeChange" @current-change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mrpApi, type MrpRequirementDetail } from '../../../api/mrp'
import { supplyModeLabel, businessLabel } from '../../../utils/mrpDisplay'

const props = defineProps<{ batchId: number }>()
const loading = ref(false)
const rows = ref<MrpRequirementDetail[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const terminalLabels: Record<string, string> = { POLICY_STOP: '策略停止下钻', EXPLODE_WITHOUT_BOM: '下钻但无 BOM', BOM_CYCLE: 'BOM 循环', MATERIAL_MISSING: '物料主数据缺失', ROLE_MISSING: '分类角色缺失', SUPPLY_MODE_MISSING: '物料策略缺失', INVENTORY_MISSING: '库存记录缺失' }
function terminalReasonLabel(value: string) { return terminalLabels[value] || value }
async function loadData() { loading.value = true; try { const result = await mrpApi.getRequirementsPage(props.batchId, page.value, pageSize.value); rows.value = result.items || []; total.value = result.total || 0 } catch (e: unknown) { ElMessage.error('加载需求追溯失败: ' + (e instanceof Error ? e.message : String(e))) } finally { loading.value = false } }
function handleSizeChange() { page.value = 1; void loadData() }
onMounted(loadData)
</script>

<style scoped>
.trace-alert { margin-bottom: 12px; }
.sub-text { margin-top: 3px; color: #86909C; font-size: 11px; }
</style>
