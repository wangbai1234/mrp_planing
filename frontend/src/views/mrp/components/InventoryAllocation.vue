<template>
  <div class="inventory-allocation">
    <div class="allocation-container" v-loading="loading">
      <el-table :data="pools" border style="width: 100%">
        <el-table-column prop="materialCode" label="物料编码" min-width="150" />
        <el-table-column prop="factoryCode" label="工厂" width="90" />
        <el-table-column prop="warehouseCode" label="库存池仓库" width="130" />
        <el-table-column prop="inventoryOrg" label="库存组织" width="110" />
        <el-table-column prop="availableQty" label="库存数量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.availableQty) }}</template>
        </el-table-column>
        <el-table-column prop="reservedQty" label="安全库存" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column label="可分配数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(allocatableQty(row)) }}</template>
        </el-table-column>
        <el-table-column prop="allocatedQty" label="已分配数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.allocatedQty) }}</template>
        </el-table-column>
        <el-table-column label="剩余可分配" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(remainingQty(row)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewAllocations(row)">查看分配</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无库存池" /></template>
      </el-table>
      <el-pagination
        v-if="poolTotal"
        v-model:current-page="poolPage"
        v-model:page-size="poolPageSize"
        :page-sizes="[20, 50, 100]"
        :total="poolTotal"
        layout="total, sizes, prev, pager, next"
        @size-change="changePoolSize"
        @current-change="loadPools"
      />
    </div>

    <el-dialog v-model="showAllocationDialog" :title="`库存分配明细 · ${currentPool?.materialCode || ''}`" width="980px">
      <el-alert
        title="系统默认按来源月份和经营计划优先级自动分配。只有业务例外时才需要调整某笔分配，调整会保留原因并触发后续重新分析。"
        type="info"
        :closable="false"
        class="allocation-hint"
      />
      <el-descriptions v-if="currentPool" :column="4" border class="pool-summary">
        <el-descriptions-item label="库存数量">{{ formatNumber(currentPool.availableQty) }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ formatNumber(currentPool.reservedQty) }}</el-descriptions-item>
        <el-descriptions-item label="已分配">{{ formatNumber(currentPool.allocatedQty) }}</el-descriptions-item>
        <el-descriptions-item label="剩余可分配">{{ formatNumber(remainingQty(currentPool)) }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-loading="allocationLoading" :data="allocations" border style="width: 100%; margin-top: 16px">
        <el-table-column prop="sourceMonth" label="来源月份" width="110" />
        <el-table-column prop="rootMaterialCode" label="来源整机" width="145" />
        <el-table-column prop="demandKey" label="需求标识" min-width="210" show-overflow-tooltip />
        <el-table-column prop="grossQty" label="需求数量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.grossQty) }}</template>
        </el-table-column>
        <el-table-column prop="allocatedQty" label="分配数量" width="110" align="right">
          <template #default="{ row }">{{ formatNumber(row.allocatedQty) }}</template>
        </el-table-column>
        <el-table-column prop="allocationType" label="分配类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.allocationType === 'MANUAL_OVERRIDE' ? 'warning' : 'success'">
              {{ row.allocationType === 'MANUAL_OVERRIDE' ? '人工调整' : '自动分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adjustmentReason" label="调整原因" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.adjustmentReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="revision" label="版本" width="70" align="center" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canAllocate" type="primary" link :disabled="!canEditAllocation(row)" @click="openAdjustment(row)">调整</el-button>
            <el-button v-if="canAllocate && row.allocationType === 'MANUAL_OVERRIDE'" type="primary" link :disabled="!canEditAllocation(row) || resetting" :loading="resetting" @click="resetToAutomatic(row)">恢复自动</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无自动分配记录，请先完成批次预检" /></template>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showAdjustmentDialog" title="人工调整库存分配" width="520px">
      <el-form label-width="110px">
        <el-form-item label="来源需求"><el-input :model-value="adjustmentAllocation?.demandKey || ''" disabled /></el-form-item>
        <el-form-item label="原分配数量"><el-input :model-value="formatNumber(adjustmentAllocation?.allocatedQty)" disabled /></el-form-item>
        <el-form-item label="调整后数量">
          <el-input-number v-model="adjustedQty" :min="0" :max="adjustmentMax" :precision="6" controls-position="right" style="width: 220px" />
          <span class="field-hint">上限 {{ formatNumber(adjustmentMax) }}</span>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustmentReason" type="textarea" :rows="3" maxlength="512" show-word-limit placeholder="请填写业务例外原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustmentDialog = false">取消</el-button>
        <el-button type="primary" :loading="adjustmentSubmitting" @click="confirmAdjustment">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mrpApi, type MrpInventoryAllocation, type MrpInventoryPool } from '../../../api/mrp'
import { usePermissionStore } from '../../../stores/permission'

const permissionStore = usePermissionStore()
const canAllocate = computed(() => permissionStore.hasPermission('mrp_inventory:allocate'))
const props = defineProps<{ batchId: number; batchStatus: string }>()
const emit = defineEmits<{ changed: [] }>()
const editableBatch = computed(() => ['DRAFT', 'WAITING_DECISION', 'READY_TO_CALCULATE', 'REANALYZING', 'MATERIAL_REVIEW', 'READY', 'MATERIAL_RECALC_REQUIRED'].includes(props.batchStatus))
const canEditAllocation = (row: MrpInventoryAllocation) => editableBatch.value &&
  (!['MATERIAL_REVIEW', 'READY', 'MATERIAL_RECALC_REQUIRED'].includes(props.batchStatus) ||
    (row.pathHash ? row.pathHash !== row.rootMaterialCode :
      currentPool.value?.materialCode !== row.rootMaterialCode ||
      row.demandKey !== [row.rootMaterialCode, row.rootMaterialCode, row.sourceMonth].join('|')))

const loading = ref(false)
const allocationLoading = ref(false)
const adjustmentSubmitting = ref(false)
const resetting = ref(false)
const pools = ref<MrpInventoryPool[]>([])
const allocations = ref<MrpInventoryAllocation[]>([])
const poolPage = ref(1)
const poolPageSize = ref(20)
const poolTotal = ref(0)
const showAllocationDialog = ref(false)
const showAdjustmentDialog = ref(false)
const currentPool = ref<MrpInventoryPool | null>(null)
const adjustmentAllocation = ref<MrpInventoryAllocation | null>(null)
const adjustedQty = ref(0)
const adjustmentReason = ref('')

const adjustmentMax = computed(() => {
  const allocation = adjustmentAllocation.value
  const pool = currentPool.value
  if (!allocation || !pool) return 0
  return Math.max(0, Number(allocation.allocatedQty || 0) + remainingQty(pool))
})

function numeric(value: number | string | null | undefined): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatNumber(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return '-'
  return numeric(value).toLocaleString(undefined, { maximumFractionDigits: 6 })
}

function allocatableQty(pool: MrpInventoryPool) {
  return Math.max(0, numeric(pool.availableQty) - numeric(pool.reservedQty))
}

function remainingQty(pool: MrpInventoryPool) {
  return Math.max(0, allocatableQty(pool) - numeric(pool.allocatedQty))
}

async function viewAllocations(pool: MrpInventoryPool) {
  currentPool.value = pool
  showAllocationDialog.value = true
  allocationLoading.value = true
  try {
    allocations.value = await mrpApi.getAllocationsByPool(pool.id)
  } catch (error: unknown) {
    allocations.value = []
    ElMessage.error('加载分配明细失败: ' + (error instanceof Error ? error.message : String(error)))
  } finally {
    allocationLoading.value = false
  }
}

function openAdjustment(allocation: MrpInventoryAllocation) {
  adjustmentAllocation.value = allocation
  adjustedQty.value = numeric(allocation.allocatedQty)
  adjustmentReason.value = ''
  showAdjustmentDialog.value = true
}

async function confirmAdjustment() {
  const allocation = adjustmentAllocation.value
  if (!allocation) return
  if (!adjustmentReason.value.trim()) {
    ElMessage.warning('请填写人工调整原因')
    return
  }
  if (adjustedQty.value < 0 || adjustedQty.value > adjustmentMax.value) {
    ElMessage.warning('调整数量超出库存池可分配范围')
    return
  }
  adjustmentSubmitting.value = true
  try {
    await mrpApi.updateAllocation(allocation.id as number, adjustedQty.value, allocation.revision, adjustmentReason.value.trim())
    ElMessage.success('库存分配已调整，请重新计算物料分析')
    showAdjustmentDialog.value = false
    await loadPools()
    if (currentPool.value) {
      currentPool.value = pools.value.find(pool => pool.id === currentPool.value?.id) || currentPool.value
      await viewAllocations(currentPool.value)
    }
    emit('changed')
  } catch (error: unknown) {
    ElMessage.error('调整分配失败: ' + (error instanceof Error ? error.message : String(error)))
  } finally {
    adjustmentSubmitting.value = false
  }
}

async function resetToAutomatic(allocation: MrpInventoryAllocation) {
  if (allocation.id === null) return
  try {
    await ElMessageBox.confirm('恢复后这笔人工覆盖将失效，请重新分析批次以重新生成自动分配。', '恢复自动分配', {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  resetting.value = true
  try {
    await mrpApi.resetAllocation(allocation.id)
    ElMessage.success('已恢复自动分配，请重新计算物料分析')
    await loadPools()
    if (currentPool.value) {
      currentPool.value = pools.value.find(pool => pool.id === currentPool.value?.id) || currentPool.value
      await viewAllocations(currentPool.value)
    }
    emit('changed')
  } catch (error: unknown) {
    ElMessage.error('恢复自动分配失败: ' + (error instanceof Error ? error.message : String(error)))
  } finally {
    resetting.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    await loadPools()
  } catch (error: unknown) {
    ElMessage.error('加载库存数据失败: ' + (error instanceof Error ? error.message : String(error)))
  } finally {
    loading.value = false
  }
}

async function loadPools() {
  const result = await mrpApi.getInventoryPoolsPage(props.batchId, poolPage.value, poolPageSize.value)
  pools.value = result.items || []
  poolTotal.value = result.total || 0
}

function changePoolSize() {
  poolPage.value = 1
  void loadPools()
}

onMounted(loadData)
</script>

<style scoped>
.inventory-allocation { padding: 16px 0; }
.allocation-container { margin-bottom: 16px; }
.allocation-hint { margin-bottom: 16px; }
.pool-summary { margin-bottom: 8px; }
.field-hint { margin-left: 12px; color: #86909c; }
</style>
