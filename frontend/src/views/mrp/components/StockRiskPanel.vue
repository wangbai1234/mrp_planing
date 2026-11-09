<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mrpApi, type StockPolicyRisk } from '../../../api/mrp'
import { usePermissionStore } from '../../../stores/permission'

const props = defineProps<{ batchId: number; runId: number | null }>()
const emit = defineEmits<{ changed: [] }>()
const permission = usePermissionStore()
const loading = ref(false)
const risks = ref<StockPolicyRisk[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const error = ref('')
const resolving = ref(false)

const breaches = computed(() => risks.value.filter(item => item.riskType !== 'STOCK_POLICY_MISSING'))
const missing = computed(() => risks.value.filter(item => item.riskType === 'STOCK_POLICY_MISSING'))

async function load() {
  loading.value = true
  error.value = ''
  try { if (props.runId == null) { risks.value = []; total.value = 0; return }; const result = await mrpApi.getStockRisksPage(props.batchId, page.value, pageSize.value, props.runId); risks.value = result.items || []; total.value = result.total || 0 }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '加载失败' }
  finally { loading.value = false }
}

function changeSize() { page.value = 1; void load() }

async function applyZeroThresholds() {
  if (props.runId == null) return
  try {
    await ElMessageBox.confirm('将本次排产中所有库存风险按安全库存 0、预警线 0 处理。此操作只作用于当前计算结果，不修改库存策略或库存数据。', '本次排产按零阈值处理', { confirmButtonText: '确认处理', cancelButtonText: '取消' })
  } catch { return }
  resolving.value = true
  try {
    const count = await mrpApi.applyZeroStockThresholds(props.batchId, props.runId)
    ElMessage.success(`已处理 ${count} 条本次排产库存风险`)
    page.value = 1
    await load()
    emit('changed')
  } catch (cause) { ElMessage.error('处理库存风险失败: ' + (cause instanceof Error ? cause.message : String(cause))) }
  finally { resolving.value = false }
}

function level(risk: StockPolicyRisk) {
  return risk.riskType === 'SAFETY_STOCK_BREACH'
    ? { label: '低于安全库存', type: 'danger' as const }
    : { label: '低于预警库存', type: 'warning' as const }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="risk-actions"><p class="note">安全库存为红色，预警库存为黄色。按零阈值处理仅影响本次排产，不修改库存策略。</p><el-button v-if="total && permission.hasPermission('mrp_inventory:allocate')" type="primary" :loading="resolving" @click="applyZeroThresholds">本次排产安全库存与预警线设为 0</el-button></div>
    <el-table :data="breaches" border>
      <el-table-column label="风险" width="130"><template #default="{ row }"><el-tag :type="level(row).type">{{ level(row).label }}</el-tag></template></el-table-column>
      <el-table-column prop="warehouseCode" label="仓库" width="120" />
      <el-table-column prop="materialCode" label="物料料号" min-width="160" />
      <el-table-column prop="availableQty" label="良品" width="100" align="right" />
      <el-table-column prop="usedQty" label="已分配" width="100" align="right" />
      <el-table-column prop="remainingQty" label="剩余" width="100" align="right" />
      <el-table-column prop="safetyStockQty" label="安全库存" width="110" align="right" />
      <el-table-column prop="warningStockQty" label="预警库存" width="110" align="right" />
      <el-table-column prop="rootMaterialCodes" label="涉及整机" min-width="180" />
    </el-table>
    <el-empty v-if="!loading && !breaches.length" description="没有低于安全库存或预警库存的物料" />

    <el-collapse class="missing">
      <el-collapse-item :title="`未维护安全库存与预警线（本页 ${missing.length}）`" name="missing">
        <el-table :data="missing" border max-height="360">
          <el-table-column prop="materialCode" label="物料料号" min-width="180" />
          <el-table-column prop="rootMaterialCodes" label="出现在整机" min-width="240" />
        </el-table>
      </el-collapse-item>
    </el-collapse>
    <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="changeSize" @current-change="load" />
  </div>
</template>

<style scoped>
.note{color:#86909C;font-size:13px}.missing{margin-top:16px}
.risk-actions{display:flex;justify-content:space-between;align-items:center;gap:16px;margin:12px 0}
</style>
