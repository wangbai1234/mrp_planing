<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '../../api/client'
import { Search, Refresh, Close, Share, Download } from '@element-plus/icons-vue'
import { usePermission } from '../../composables/usePermission'

const { hasPermission } = usePermission()

interface BomSyncTask {
  id: number
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  phase: string | null
  totalRows: number | null
  syncedRows: number | null
  parentCount: number | null
  zeroQty: number | null
  selfReference: number | null
  dataValid: boolean | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

interface BomMaterial {
  invCode: string
  invName: string
  version: string
  unit: string
  orgName: string
  bodyCode: string
  childCount: number
}

interface BomDetail {
  childCode: string
  childName: string
  materialSpec: string
  makeFactory: string
  childQty: number
  childNote: string
  replacePriority: string
  altCode: string
  altName: string
  altSpec: string
  altFactory: string
  altQty: number
  isDeliver: string
  isDefault: string
  mainStatus: string
  altStatus: string
  mainRhosStatus: string
  altRhosStatus: string
  admitNote: string
  materialCategoryId: number | null
  materialCategoryName: string | null
}

interface BomTreeNode {
  code: string
  name: string
  spec: string
  qty: number
  version: string
  isDefault?: string
  hasChildren: boolean
  isLeaf?: boolean
  children?: BomTreeNode[]
}

interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

const materials = ref<BomMaterial[]>([])
const materialTotal = ref(0)
const materialPage = ref(1)
const materialPageSize = ref(20)
const materialLoading = ref(false)
const selectedMaterial = ref<BomMaterial | null>(null)

const details = ref<BomDetail[]>([])
const detailLoading = ref(false)

const searchKeyword = ref('')

// Category map for display
const categoryMap = ref<Record<number, string>>({})

async function loadCategoryMap() {
  try {
    const flat = await get<any[]>('/material-categories')
    if (flat) {
      for (const c of flat) {
        categoryMap.value[c.id] = `${c.name}（${c.code}）`
      }
    }
  } catch (e: any) {
    // Silently fail
  }
}

const splitRatio = ref(100)
const isDetailExpanded = ref(false)
const isDragging = ref(false)

const drawerVisible = ref(false)
const treeData = ref<BomTreeNode[]>([])
const treeLoading = ref(false)
const treeRef = ref<any>(null)

const syncDialogVisible = ref(false)
const syncTask = ref<BomSyncTask | null>(null)
const syncProgressRetrying = ref(false)
const syncing = computed(() => ['PENDING', 'RUNNING'].includes(syncTask.value?.status || ''))
const syncPercent = computed(() => syncTask.value?.status === 'SUCCESS' ? 100 :
  syncTask.value?.totalRows ? Math.min(99, Math.round((syncTask.value.syncedRows || 0) * 100 / syncTask.value.totalRows)) : 0)
let syncTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false

async function pollSync(taskId: number) {
  try {
    const task = await get<BomSyncTask>(`/bom/sync/${taskId}`)
    if (disposed || syncTask.value?.id !== taskId) return
    syncTask.value = task
    syncProgressRetrying.value = false
    if (task.status === 'SUCCESS') await loadMaterials()
  } catch (error: unknown) {
    if (disposed) return
    syncProgressRetrying.value = true
    console.warn('读取BOM同步进度失败，将自动重试', error)
  } finally {
    if (!disposed && syncTask.value?.id === taskId && (syncing.value || syncProgressRetrying.value)) {
      syncTimer = setTimeout(() => pollSync(taskId), 2000)
    }
  }
}

async function restoreSync() {
  try {
    const task = await get<BomSyncTask | null>('/bom/sync/latest')
    if (disposed || !task) return
    syncTask.value = task
    if (['PENDING', 'RUNNING'].includes(task.status)) {
      syncDialogVisible.value = true
      pollSync(task.id)
    }
  } catch (error: unknown) {
    console.warn('读取最近BOM同步任务失败', error)
  }
}

const treeProps = {
  children: 'children',
  label: 'code',
  isLeaf: 'isLeaf',
}

async function loadMaterials() {
  materialLoading.value = true
  try {
    const params = new URLSearchParams()
    params.append('page', materialPage.value.toString())
    params.append('pageSize', materialPageSize.value.toString())
    if (searchKeyword.value) params.append('keyword', searchKeyword.value)

    const res = await get<PageResult<BomMaterial>>(`/bom/materials?${params.toString()}`)
    materials.value = res?.items || []
    materialTotal.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error('加载物料列表失败: ' + e.message)
  } finally {
    materialLoading.value = false
  }
}

async function handleSync() {
  if (syncing.value && syncTask.value) {
    syncDialogVisible.value = true
    return
  }
  try {
    await ElMessageBox.confirm('从NC同步BOM数据可能需要几分钟，确定继续？', '同步确认', {
      confirmButtonText: '开始同步',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    const task = await post<BomSyncTask>('/bom/sync')
    syncTask.value = task
    syncDialogVisible.value = true
    pollSync(task.id)
  } catch (error: unknown) {
    ElMessage.error('创建同步任务失败: ' + (error instanceof Error ? error.message : '未知错误'))
  }
}

async function loadDetail(invCode: string) {
  detailLoading.value = true
  try {
    const res = await get<BomDetail[]>(`/bom/detail/${invCode}`)
    details.value = res || []
  } catch (e: any) {
    ElMessage.error('加载BOM明细失败: ' + e.message)
  } finally {
    detailLoading.value = false
  }
}

async function handleMaterialClick(row: BomMaterial) {
  selectedMaterial.value = row
  isDetailExpanded.value = true
  splitRatio.value = 50
  await loadDetail(row.invCode)
}

function handleDetailClose() {
  isDetailExpanded.value = false
  splitRatio.value = 100
  selectedMaterial.value = null
  details.value = []
}

function handleDragStart(e: MouseEvent) {
  isDragging.value = true
  const startY = e.clientY
  const startRatio = splitRatio.value
  const containerHeight = (e.target as HTMLElement).closest('.split-container')?.clientHeight || 600

  const onMouseMove = (moveEvent: MouseEvent) => {
    if (!isDragging.value) return
    const deltaY = moveEvent.clientY - startY
    const deltaRatio = (deltaY / containerHeight) * 100
    const newRatio = Math.max(20, Math.min(80, startRatio + deltaRatio))
    splitRatio.value = newRatio
  }

  const onMouseUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function handleSearch() {
  materialPage.value = 1
  loadMaterials()
}

function handleRefresh() {
  searchKeyword.value = ''
  handleSearch()
}

function handlePageChange(page: number) {
  materialPage.value = page
  loadMaterials()
}

function handleSizeChange(size: number) {
  materialPageSize.value = size
  materialPage.value = 1
  loadMaterials()
}

async function openBomTree() {
  if (!selectedMaterial.value) {
    ElMessage.warning('请先选择一个物料')
    return
  }
  drawerVisible.value = true
  await loadTreeRoot(selectedMaterial.value.invCode)
}

async function loadTreeRoot(invCode: string) {
  treeLoading.value = true
  try {
    const material = await get<BomMaterial>(`/bom/materials/${invCode}`)

    const children = await get<BomTreeNode[]>(`/bom/tree/${invCode}`) || []

    treeData.value = [{
      code: material?.invCode || invCode,
      name: material?.invName || '',
      spec: '',
      qty: 1,
      version: material?.version || '',
      hasChildren: children.length > 0,
      isLeaf: children.length === 0,
      children: children
    }]
  } catch (e: any) {
    ElMessage.error('加载BOM树失败: ' + e.message)
  } finally {
    treeLoading.value = false
  }
}

async function loadNode(node: any, resolve: (data: BomTreeNode[]) => void) {
  if (node.level === 0) {
    resolve(treeData.value)
    return
  }
  try {
    const res = await get<BomTreeNode[]>(`/bom/tree/${node.data.code}`)
    const children = (res || []).map(child => ({
      ...child,
      isLeaf: !child.hasChildren
    }))
    resolve(children)
  } catch (e: any) {
    ElMessage.error('加载子节点失败: ' + e.message)
    resolve([])
  }
}

function expandAll() {
  if (!treeRef.value) return
  // In lazy mode, we can only expand loaded nodes
  const allNodes = getAllNodeKeys(treeData.value)
  allNodes.forEach(key => {
    treeRef.value.store.nodesMap[key]?.expand()
  })
}

function collapseAll() {
  if (!treeRef.value) return
  const allNodes = getAllNodeKeys(treeData.value)
  allNodes.forEach(key => {
    treeRef.value.store.nodesMap[key]?.collapse()
  })
}

function getAllNodeKeys(nodes: BomTreeNode[]): string[] {
  const keys: string[] = []
  const traverse = (list: BomTreeNode[]) => {
    for (const node of list) {
      keys.push(node.code)
      if (node.children) {
        traverse(node.children)
      }
    }
  }
  traverse(nodes)
  return keys
}

function handleExport() {
  const headers = ['物料编码', '物料名称', '版本', '单位', '组织', '机型号', '子项数']
  const rows = materials.value.map(m => [
    m.invCode, m.invName, m.version, m.unit, m.orgName, m.bodyCode, String(m.childCount)
  ])
  const csvContent = [headers, ...rows].map(r => r.join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `BOM视图_${new Date().toLocaleDateString('zh-CN')}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

const tableRowClassName = ({ row }: { row: BomMaterial }) => {
  if (selectedMaterial.value && row.invCode === selectedMaterial.value.invCode) {
    return 'selected-row'
  }
  return ''
}

onMounted(() => {
  loadCategoryMap()
  loadMaterials()
  restoreSync()
})
onBeforeUnmount(() => {
  disposed = true
  if (syncTimer) clearTimeout(syncTimer)
})
</script>

<template>
  <div class="bom-page">
    <div class="page-header">
      <h1 class="page-title">BOM管理</h1>
      <div class="page-actions">
        <el-button v-if="hasPermission('bom:manage')" type="primary" @click="handleSync">
          <el-icon><Download /></el-icon>
          {{ syncing ? '查看同步进度' : '从NC同步' }}
        </el-button>
        <el-button type="primary" @click="openBomTree" :disabled="!selectedMaterial">
          <el-icon><Share /></el-icon>
          BOM树展开
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索物料编码/名称"
          style="width: 220px"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div class="filter-right">
        <el-button @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="split-container" :style="{ '--split-ratio': splitRatio + '%' }">
      <div class="split-top" :style="{ height: isDetailExpanded ? splitRatio + '%' : '100%' }">
        <el-table
          :data="materials"
          :loading="materialLoading"
          :height="'100%'"
          border
          stripe
          highlight-current-row
          :row-class-name="tableRowClassName"
          @row-click="handleMaterialClick"
        >
          <el-table-column prop="invCode" label="编码" width="150" fixed />
          <el-table-column prop="invName" label="名称" min-width="200" />
          <el-table-column prop="version" label="版本" width="100">
            <template #default="{ row }">
              <el-tag size="small" type="info">V{{ row.version }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="orgName" label="组织" width="120" />
          <el-table-column prop="childCount" label="子项数" width="100" align="right" />
          <el-table-column prop="bodyCode" label="库存组织编码" width="130" />
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="materialPage"
            v-model:page-size="materialPageSize"
            :total="materialTotal"
            :page-sizes="[20, 50, 100, 200]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <div v-if="isDetailExpanded" class="split-dragger" @mousedown="handleDragStart">
        <div class="drag-handle"></div>
      </div>

      <div class="split-bottom" v-if="isDetailExpanded" :style="{ height: (100 - splitRatio) + '%' }">
        <div class="detail-header">
          <div class="detail-info">
            <div class="detail-title">BOM结构</div>
            <div class="detail-material">
              <span class="material-code">{{ selectedMaterial?.invCode }}</span>
              <span class="material-name">{{ selectedMaterial?.invName }}</span>
              <el-tag size="small" type="success" class="material-version">V{{ selectedMaterial?.version }}</el-tag>
            </div>
          </div>
          <div class="detail-actions">
            <el-button size="small" @click="loadDetail(selectedMaterial!.invCode)">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="primary" size="small" @click="handleExport">导出</el-button>
            <el-button size="small" @click="handleDetailClose">
              <el-icon><Close /></el-icon>
              关闭
            </el-button>
          </div>
        </div>

        <el-table
          :data="details"
          :loading="detailLoading"
          border
          stripe
          size="small"
        >
          <el-table-column prop="childCode" label="子项编码" width="140" fixed />
          <el-table-column prop="childName" label="子项名称" width="160" />
          <el-table-column label="物料分类" width="160" align="center">
            <template #default="{ row }">
              <span v-if="row.materialCategoryName">{{ row.materialCategoryName }}</span>
              <span v-else style="color: #86909C">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="materialSpec" label="规格（主料）" width="140" />
          <el-table-column prop="makeFactory" label="生产厂家" width="140" />
          <el-table-column prop="childQty" label="数量" width="80" align="right" />
          <el-table-column prop="childNote" label="备注" width="120" />
          <el-table-column prop="replacePriority" label="替代优先级" width="110" />
          <el-table-column prop="altCode" label="替代料编码" width="140" />
          <el-table-column prop="altName" label="替代料名称" width="160" />
          <el-table-column prop="altSpec" label="替代规格" width="120" />
          <el-table-column prop="altFactory" label="替代厂家" width="120" />
          <el-table-column prop="isDeliver" label="是否发料" width="100" />
          <el-table-column prop="isDefault" label="是否默认" width="100" />
          <el-table-column prop="mainStatus" label="主承认状态" width="120" />
          <el-table-column prop="mainRhosStatus" label="RHOS状态" width="120" />
          <el-table-column prop="admitNote" label="承认备注" width="140" />
        </el-table>
      </div>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :size="780"
      direction="rtl"
      :show-close="false"
      class="bom-tree-drawer"
    >
      <template #header>
        <div class="drawer-header">
          <div class="drawer-title-section">
            <div class="drawer-title">BOM多层结构</div>
            <div class="drawer-subtitle" v-if="selectedMaterial">
              根物料：<span class="root-code">{{ selectedMaterial.invCode }}</span> ·
              <span class="root-version">V{{ selectedMaterial.version }}</span>
            </div>
          </div>
          <div class="drawer-actions">
            <el-button size="small" @click="expandAll" class="toolbar-btn">
              全部展开
            </el-button>
            <el-button size="small" @click="collapseAll" class="toolbar-btn">
              全部收起
            </el-button>
            <el-button size="small" @click="drawerVisible = false" class="close-btn">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="treeLoading" class="tree-container">
          <el-alert title="此树展示当前同步的全部BOM关系。排产沿这些下级逐层计算；只有物料配置为停止下钻时才在该节点终止。" type="info" :closable="false" style="margin-bottom: 12px" />
        <el-tree
          ref="treeRef"
          v-if="treeData.length > 0"
          :data="treeData"
          node-key="code"
          lazy
          :load="loadNode"
          :expand-on-click-node="false"
          :props="treeProps"
          class="bom-tree"
        >
          <template #default="{ data }">
            <div class="tree-node-content">
              <span class="node-code">{{ data.code }}</span>
              <span class="node-version" v-if="data.version">V{{ data.version }}</span>
              <span class="node-name">{{ data.name }}</span>
              <span class="node-qty">× {{ data.qty }}</span>
            </div>
          </template>
        </el-tree>
        <el-empty v-else description="暂无BOM树数据" />
      </div>
    </el-drawer>

    <el-dialog
      v-model="syncDialogVisible"
      title="同步BOM数据"
      :close-on-click-modal="false"
      width="420"
    >
      <div v-if="syncTask" class="sync-progress" aria-live="polite">
        <div class="sync-progress-row">
          <el-progress class="progress-bar" :percentage="syncPercent" :indeterminate="syncing && !syncTask.totalRows"
            :stroke-width="8" :show-text="false" :status="syncTask.status === 'FAILED' ? 'exception' : syncTask.status === 'SUCCESS' ? 'success' : undefined" />
          <span v-if="syncTask.totalRows || syncTask.status === 'SUCCESS'" class="progress-percent">{{ syncPercent }}%</span>
          <span>{{ syncTask.phase || '等待同步' }}<span v-if="syncProgressRetrying">（进度更新重试中）</span></span>
        </div>
        <p v-if="syncTask.totalRows" class="sync-count">已拉取 {{ syncTask.syncedRows || 0 }} / {{ syncTask.totalRows }} 条</p>
        <el-alert v-if="syncTask.status === 'FAILED'" type="error" :title="syncTask.errorMessage || '同步失败'" :closable="false" />
        <el-descriptions v-if="syncTask.status === 'SUCCESS'" :column="1" border size="small">
          <el-descriptions-item label="父项数量">{{ syncTask.parentCount }}</el-descriptions-item>
          <el-descriptions-item label="子项数量">{{ syncTask.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="数据质量">
            <el-tag :type="syncTask.dataValid ? 'success' : 'warning'">{{ syncTask.dataValid ? '正常' : '有问题' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="syncTask.zeroQty" label="数量为0的记录">{{ syncTask.zeroQty }} 条</el-descriptions-item>
          <el-descriptions-item v-if="syncTask.selfReference" label="自引用记录">{{ syncTask.selfReference }} 条</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="syncDialogVisible = false">关闭</el-button>
        <el-button v-if="syncTask?.status === 'SUCCESS'" type="primary" @click="syncDialogVisible = false; loadMaterials()">
          刷新列表
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bom-page {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sync-progress { padding: 16px 0; color: #4E5969; font-size: 13px; }
.sync-progress-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.progress-bar { width: 180px; max-width: 100%; flex-shrink: 0; }
.progress-percent { font-variant-numeric: tabular-nums; }
.sync-count { margin: 12px 0; color: #86909C; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
  margin: 0;
}

.page-actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.filter-left {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.split-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.split-top {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  transition: height 300ms ease;
  overflow: hidden;
}

.split-top :deep(.el-table) {
  flex: 1;
  overflow: auto;
}

.split-dragger {
  height: 8px;
  cursor: row-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 4px;
  margin: 4px 0;
  user-select: none;
}

.split-dragger:hover {
  background: #e8e8e8;
}

.drag-handle {
  width: 40px;
  height: 3px;
  background: #c0c0c0;
  border-radius: 2px;
}

.split-bottom {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: slideUp 300ms ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.pagination-bar {
  padding: 12px 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #ebeef5;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #1F2329;
}

.detail-material {
  display: flex;
  align-items: center;
  gap: 12px;
}

.material-code {
  font-weight: 600;
  color: #1677FF;
  font-size: 14px;
}

.material-name {
  color: #4E5969;
  font-size: 14px;
}

.material-version {
  font-size: 12px;
}

.detail-actions {
  display: flex;
  gap: 8px;
}

.split-bottom :deep(.el-table) {
  flex: 1;
  overflow: auto;
}

:deep(.selected-row) {
  background-color: #e6f4ff !important;
}

:deep(.selected-row:hover td) {
  background-color: #bae0ff !important;
}

/* Tree 样式 */
.tree-container {
  height: 100%;
  overflow: auto;
  padding: 16px;
}

.bom-tree {
  font-size: 13px;
}

.tree-node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.node-code {
  font-weight: 500;
  color: #101828;
}

.node-version {
  color: #667085;
  font-size: 12px;
}

.node-name {
  color: #667085;
  font-size: 12px;
  margin-left: 4px;
}

.node-qty {
  color: #1677FF;
  font-size: 12px;
  font-weight: 500;
  margin-left: 4px;
}

/* Drawer 样式 */
:deep(.bom-tree-drawer) {
  .el-drawer__header {
    margin-bottom: 0;
    padding: 16px 20px;
    border-bottom: 1px solid #e4e7ed;
  }

  .el-drawer__body {
    padding: 0;
  }
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.drawer-title-section {
  flex: 1;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #101828;
  margin-bottom: 4px;
}

.drawer-subtitle {
  font-size: 13px;
  color: #667085;
}

.root-code {
  font-weight: 600;
  color: #101828;
}

.root-version {
  color: #1677FF;
}

.drawer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-btn {
  border-color: #d0d5dd;
  color: #344054;
}

.toolbar-btn:hover {
  border-color: #1677FF;
  color: #1677FF;
}

.close-btn {
  border: none;
  color: #667085;
}

.close-btn:hover {
  color: #101828;
}

:deep(.el-tree-node__content) {
  height: auto;
  padding: 4px 0;
}
</style>
