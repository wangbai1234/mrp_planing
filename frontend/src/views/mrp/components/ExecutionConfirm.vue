<template>
  <div class="execution-confirm">
    <div class="execution-toolbar">
      <el-date-picker
        v-model="selectedDate"
        type="date"
        placeholder="选择日期"
        format="YYYY-MM-DD"
        value-format="YYYY-MM-DD"
        @change="refreshFromFirstPage"
      />
      <el-button v-if="canConfirm && selectedDate" :loading="saving" @click="lockDay">锁定日期</el-button>
      <el-button v-if="canConfirm && selectedDate" :loading="saving" @click="unlockDay">解除锁定</el-button>
      <el-input v-model="keyword" class="execution-search" placeholder="搜索整机料号/工厂" clearable style="width:220px"
        @keyup.enter="search" @input="onKeywordInput" />
    </div>

    <div class="execution-container" v-loading="loading">
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"><el-button type="primary" link @click="loadData">重试</el-button></el-alert>
      <el-table :data="executions" border style="width: 100%">
        <el-table-column prop="factoryCode" label="工厂" width="120" />
        <el-table-column prop="rootMaterialCode" label="整机料号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="scheduleDate" label="排产日期" width="100" />
        <el-table-column prop="plannedQuantity" label="计划数量" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.plannedQuantity) }}
          </template>
        </el-table-column>
        <el-table-column prop="actualQuantity" label="实际数量" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.actualQuantity) }}
          </template>
        </el-table-column>
        <el-table-column prop="executionStatus" label="执行状态" width="100">
          <template #default="{ row }">
            <el-tag :type="executionStatusType(row.executionStatus)">{{ businessLabel(row.executionStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.executionStatus !== 'EXECUTED' && canConfirm" type="primary" link @click="confirmExecution(row)">确认执行</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !executions.length" :description="selectedDate ? '该日期暂无整机日排产' : '暂无整机日排产'" />
      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20, 50, 100]"
          :total="total" layout="total, sizes, prev, pager, next"
          @current-change="loadData" @size-change="refreshFromFirstPage" />
      </div>
    </div>

    <el-dialog v-model="showConfirmDialog" title="确认执行" width="500px">
      <el-form label-width="100px">
        <el-form-item label="行标识">
          <el-input :value="currentExecution?.rootMaterialCode" disabled />
        </el-form-item>
        <el-form-item label="排产日期">
          <el-input :value="currentExecution?.scheduleDate" disabled />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :value="formatNumber(currentExecution?.plannedQuantity)" disabled />
        </el-form-item>
        <el-form-item label="实际数量">
          <el-input-number v-model="actualQty" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConfirmDialog = false">取消</el-button>
        <el-button type="primary" @click="submitExecution">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { post, get } from '../../../api/client'
import { businessLabel } from '../../../utils/mrpDisplay'
import { usePermissionStore } from '../../../stores/permission'

const permissionStore = usePermissionStore()
const canConfirm = computed(() => permissionStore.hasPermission('mrp_execution:confirm'))

interface MrpDailyExecution {
  id: number
  batchId: number
  rowKey: string
  factoryCode: string
  rootMaterialCode: string
  scheduleDate: string
  plannedQuantity: number | null
  actualQuantity: number | null
  executionStatus: string
  confirmedBy: number | null
  confirmedAt: string | null
  remark: string | null
}

const props = defineProps<{
  batchId: number
}>()

const loading = ref(false)
const executions = ref<MrpDailyExecution[]>([])
const loadError = ref('')
const selectedDate = ref('')
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const saving = ref(false)
let requestSequence = 0
const showConfirmDialog = ref(false)
const currentExecution = ref<MrpDailyExecution | null>(null)
const actualQty = ref(0)
const remark = ref('')

function formatNumber(num: number | null | undefined) {
  if (num === null || num === undefined) return '-'
  return Number(num).toLocaleString()
}

function executionStatusType(status: string) {
  const map: Record<string, string> = {
    PLANNED: 'info',
    LOCKED: 'warning',
    EXECUTED: 'success'
  }
  return map[status] || 'info'
}

function confirmExecution(execution: MrpDailyExecution) {
  currentExecution.value = execution
  actualQty.value = execution.plannedQuantity || 0
  remark.value = ''
  showConfirmDialog.value = true
}

async function submitExecution() {
  if (!currentExecution.value) return

  try {
    const params = new URLSearchParams()
    params.append('rowKey', currentExecution.value.rowKey)
    params.append('actualQty', actualQty.value.toString())
    if (remark.value) params.append('remark', remark.value)

    await post(`/mrp/batches/${props.batchId}/execution/confirm?${params.toString()}`)
    ElMessage.success('执行确认成功')
    showConfirmDialog.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error('确认失败: ' + (e instanceof Error ? e.message : String(e)))
  }
}

async function lockDay() {
  if (!selectedDate.value) return
  saving.value = true
  try {
    await post(`/mrp/batches/${props.batchId}/execution/lock?${new URLSearchParams({scheduleDate: selectedDate.value})}`)
    ElMessage.success('日期已锁定')
    await loadData()
  } catch (e: unknown) { ElMessage.error('锁定失败: ' + (e instanceof Error ? e.message : String(e))) }
  finally { saving.value = false }
}

async function unlockDay() {
  if (!selectedDate.value) return
  saving.value = true
  try {
    await post(`/mrp/batches/${props.batchId}/execution/unlock?${new URLSearchParams({scheduleDate: selectedDate.value})}`)
    ElMessage.success('日期已解锁')
    await loadData()
  } catch (e: unknown) { ElMessage.error('解除锁定失败: ' + (e instanceof Error ? e.message : String(e))) }
  finally { saving.value = false }
}

async function loadData() {
  const request = ++requestSequence
  loading.value = true
  loadError.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), pageSize: String(pageSize.value) })
    if (appliedKeyword.value) params.set('keyword', appliedKeyword.value)
    if (selectedDate.value) params.set('scheduleDate', selectedDate.value)
    const result = await get<{items: MrpDailyExecution[]; total: number}>(`/mrp/batches/${props.batchId}/execution/page?${params}`)
    if (request !== requestSequence) return
    executions.value = result?.items || []
    total.value = result?.total || 0
  } catch (e: unknown) {
    if (request !== requestSequence) return
    loadError.value = '加载执行数据失败: ' + (e instanceof Error ? e.message : String(e))
    executions.value = []
    total.value = 0
    ElMessage.error(loadError.value)
  } finally {
    if (request === requestSequence) loading.value = false
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  refreshFromFirstPage()
}
function refreshFromFirstPage() {
  page.value = 1
  void loadData()
}
function onKeywordInput(value: string) {
  if (!value && appliedKeyword.value) search()
}
onMounted(loadData)
onBeforeUnmount(() => { ++requestSequence })
</script>

<style scoped>
.execution-confirm {
  padding: 16px 0;
}

.execution-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.execution-search { margin-left: auto; }

.execution-container {
  margin-bottom: 16px;
}

.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
