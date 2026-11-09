<template>
  <div class="decision-center">
    <div class="decision-toolbar">
      <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 140px" @change="changeSize">
        <el-option label="全部" value="" />
        <el-option label="待处理" value="OPEN" />
        <el-option label="已解决" value="RESOLVED" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="搜索料号或问题类型" style="width: 220px" @keyup.enter="changeSize" @clear="changeSize" />
      <span class="toolbar-hint">缺库存记录指所选库存版本中对应工厂的仓库没有该料号的行；数量为0但有行不算缺记录。确认按0仅对本批次生效。</span>
      <el-button v-if="canManage" type="primary" :disabled="!canConfirmMissing || selectedInventoryIssues.length === 0 || loading" @click="openBatchConfirm">批量确认按零库存（{{ selectedInventoryIssues.length }}）</el-button>
    </div>
    <el-alert v-if="batchStatus === 'REANALYZING'" title="分配已调整，请先点击重新分析，再确认缺库存记录。" type="warning" :closable="false" class="status-hint" />

    <div class="decision-container" v-loading="loading">
      <el-table :data="issues" border style="width: 100%" @selection-change="onSelectionChange">
        <el-table-column v-if="canManage" type="selection" width="55" :selectable="selectableInventoryIssue" />
        <el-table-column prop="scopeKey" label="涉及对象" width="150">
          <template #default="{ row }">{{ row.scopeKey || '当前批次' }}</template>
        </el-table-column>
        <el-table-column prop="issueType" label="需要业务确认的事项" min-width="220">
          <template #default="{ row }">{{ issueTypeLabel(row.issueType) }}</template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType(row.severity)">{{ businessLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'warning' : 'success'">{{ businessLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN' && canManage" type="primary" link :disabled="!canResolve" @click="resolveIssue(row)">解决</el-button>
            <el-button type="primary" link @click="viewDetail(row)">查看信息</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @size-change="changeSize" @current-change="loadIssues" />
    </div>

    <el-dialog v-model="showResolveDialog" title="解决决策问题" width="500px">
      <el-form label-width="100px">
        <el-form-item label="问题类型">
          <el-input :value="issueTypeLabel(currentIssue?.issueType)" disabled />
        </el-form-item>
        <el-form-item label="业务决定">
          <el-input v-model="resolution" type="textarea" :rows="3" placeholder="请记录本批次采用的处理决定和原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResolveDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmResolve">确认处理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBatchDialog" title="批量确认按零库存计算" width="500px">
      <el-alert type="warning" :closable="false" :title="`将本页选中的 ${selectedInventoryIssues.length} 条缺库存记录按零库存计算；不会向库存快照补入记录。`" />
      <el-input v-model="batchReason" type="textarea" :rows="3" placeholder="请填写本批次按零库存计算的原因" style="margin-top: 16px" />
      <template #footer>
        <el-button @click="showBatchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="confirmBatch">确认</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showDetailDrawer" title="决策问题信息" size="520px">
      <el-descriptions v-if="currentIssue" :column="1" border>
        <el-descriptions-item label="问题类型">{{ issueTypeLabel(currentIssue.issueType) }}</el-descriptions-item>
        <el-descriptions-item label="涉及对象">{{ currentIssue.scopeKey || '当前批次' }}</el-descriptions-item>
        <el-descriptions-item label="严重程度">{{ businessLabel(currentIssue.severity) }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">{{ businessLabel(currentIssue.status) }}</el-descriptions-item>
        <el-descriptions-item label="问题标识">{{ currentIssue.issueKey }}</el-descriptions-item>
        <el-descriptions-item label="附加信息"><pre class="payload">{{ formatPayload(currentIssue.payloadJson) }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { mrpApi, type MrpDecisionIssue } from '../../../api/mrp'
import { businessLabel, issueTypeLabel } from '../../../utils/mrpDisplay'
import { usePermissionStore } from '../../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = computed(() => permissionStore.hasPermission('mrp_decision:manage'))
const canConfirmMissing = computed(() => ['WAITING_DECISION','MATERIAL_REVIEW'].includes(props.batchStatus))
const canResolve = computed(() => ['WAITING_DECISION', 'MATERIAL_REVIEW','READY'].includes(props.batchStatus))

const props = defineProps<{
  batchId: number
  batchStatus: string
}>()

const loading = ref(false)
const issues = ref<MrpDecisionIssue[]>([])
const statusFilter = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const showResolveDialog = ref(false)
const showDetailDrawer = ref(false)
const currentIssue = ref<MrpDecisionIssue | null>(null)
const resolution = ref('')
const selectedInventoryIssues = ref<MrpDecisionIssue[]>([])
const showBatchDialog = ref(false)
const batchReason = ref('')
const batchSubmitting = ref(false)

function selectableInventoryIssue(row: MrpDecisionIssue) {
  return canConfirmMissing.value && row.status === 'OPEN' && row.issueType === 'INVENTORY_MISSING'
}

function onSelectionChange(rows: MrpDecisionIssue[]) {
  selectedInventoryIssues.value = rows.filter(selectableInventoryIssue)
}

function openBatchConfirm() {
  batchReason.value = ''
  showBatchDialog.value = true
}

async function confirmBatch() {
  if (!batchReason.value.trim()) {
    ElMessage.warning('请填写确认原因')
    return
  }
  batchSubmitting.value = true
  try {
    await mrpApi.resolveMissingInventory(props.batchId, selectedInventoryIssues.value.map(issue => issue.id), batchReason.value.trim())
    ElMessage.success('选中的缺库存问题已确认')
    showBatchDialog.value = false
    await loadIssues()
    emit('changed')
  } catch (e: unknown) {
    ElMessage.error('批量确认失败: ' + (e instanceof Error ? e.message : String(e)))
  } finally {
    batchSubmitting.value = false
  }
}

function severityType(severity: string) {
  const map: Record<string, string> = {
    BLOCKING: 'danger',
    ERROR: 'danger',
    WARNING: 'warning',
    INFO: 'info'
  }
  return map[severity] || 'info'
}

function formatTime(ts: string) {
  if (!ts) return '-'
  return ts.replace('T', ' ').substring(0, 19)
}

function resolveIssue(issue: MrpDecisionIssue) {
  currentIssue.value = issue
  resolution.value = ''
  showResolveDialog.value = true
}

function viewDetail(issue: MrpDecisionIssue) {
  currentIssue.value = issue
  showDetailDrawer.value = true
}

function formatPayload(payload: string | null) {
  if (!payload) return '无附加信息'
  try { return JSON.stringify(JSON.parse(payload), null, 2) } catch { return payload }
}

async function confirmResolve() {
  if (!currentIssue.value) return

  try {
    if (!resolution.value.trim()) {
      ElMessage.warning('请输入解决方案')
      return
    }
    await mrpApi.resolveIssue(currentIssue.value.id, 'MANUAL_CONFIRM', resolution.value, currentIssue.value.revision)
    ElMessage.success('问题已解决')
    showResolveDialog.value = false
    await loadIssues()
    emit('changed')
  } catch (e: any) {
    ElMessage.error('解决失败: ' + e.message)
  }
}

async function loadIssues() {
  loading.value = true
  try {
    const result = await mrpApi.getIssuesPage(props.batchId, page.value, pageSize.value, statusFilter.value || undefined, keyword.value.trim() || undefined)
    issues.value = result.items || []; total.value = result.total || 0
  } catch (e: unknown) {
    ElMessage.error('加载决策问题失败: ' + (e instanceof Error ? e.message : String(e)))
  } finally {
    loading.value = false
  }
}

function changeSize() { page.value = 1; void loadIssues() }

onMounted(loadIssues)

const emit = defineEmits<{ changed: [] }>()
</script>

<style scoped>
.decision-center {
  padding: 16px 0;
}

.decision-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar-hint { color: #86909C; font-size: 13px; }
.status-hint { margin-bottom: 12px; }
.payload { margin: 0; white-space: pre-wrap; word-break: break-all; font-family: inherit; }

.decision-container {
  margin-bottom: 16px;
}
</style>
