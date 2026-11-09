<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PolicyMaterialPicker from './components/PolicyMaterialPicker.vue'
import ConfigImportDialog from './components/ConfigImportDialog.vue'
import { mrpApi, type StockPolicyDetail, type StockPolicyWarehouse } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = () => permissionStore.hasPermission('mrp_stock_policy:manage')
const loading = ref(false)
const loadError = ref('')
const rows = ref<StockPolicyDetail[]>([])
const warehouses = ref<StockPolicyWarehouse[]>([])
const keyword = ref('')
const editing = ref<StockPolicyDetail | null>(null)
const editVisible = ref(false)
const saving = ref(false)
const importVisible = ref(false)
const logVisible = ref(false)
const logTarget = ref<StockPolicyDetail | null>(null)

const page = ref(1); const pageSize = ref(20); const total = ref(0); const warehouseFilter = ref('')
function search() { page.value = 1; load() }
function time(value?: string) { return value ? value.replace('T', ' ').substring(0, 19) : '-' }

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [policies, mapped] = await Promise.all([mrpApi.getStockPolicyPage(keyword.value, warehouseFilter.value, page.value, pageSize.value), mrpApi.getStockPolicyWarehouses()])
    rows.value = policies.items
    total.value = policies.total
    warehouses.value = mapped
  } catch (error) { loadError.value = error instanceof Error ? error.message : '加载失败' }
  finally { loading.value = false }
}

function add() {
  editing.value = { warehouseCode: '', materialCode: '', safetyStockQty: 0, warningStockQty: 0 }
  editVisible.value = true
}
function edit(row: StockPolicyDetail) {
  editing.value = { ...row }
  editVisible.value = true
}

async function save() {
  if (!editing.value) return
  if (!editing.value.warehouseCode || !editing.value.materialCode) { ElMessage.warning('请选择仓库和物料'); return }
  saving.value = true
  try {
    if (editing.value.id) await mrpApi.updateStockPolicy(editing.value.id, editing.value)
    else await mrpApi.createStockPolicy(editing.value)
    editVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}

async function remove(row: StockPolicyDetail) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.warehouseCode} / ${row.materialCode}？删除后可在日志中查看。`, '删除库存策略', { type: 'warning' })
    await mrpApi.deleteStockPolicy(row.id!)
    ElMessage.success('已删除')
    await load()
  } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}



function showLogs(row: StockPolicyDetail) { logTarget.value = row; logVisible.value = true }

onMounted(load)
</script>

<template>
  <div class="page-header"><h1 class="page-title">库存控制策略</h1>
    <div class="page-actions" v-if="canManage()"><el-button type="primary" @click="importVisible = true">导入</el-button><el-button type="primary" @click="add">新增</el-button></div>
  </div>
  <el-alert v-if="loadError" :title="`加载失败：${loadError}`" type="error" :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
  <div class="filter-bar"><el-input v-model="keyword" placeholder="搜索仓库或物料" clearable style="width:220px" @keyup.enter="search" @clear="search" />
    <el-select v-model="warehouseFilter" value-on-clear="" placeholder="仓库" clearable style="width:140px" @change="search"><el-option v-for="item in warehouses" :key="item.code" :label="item.name" :value="item.code" /></el-select></div>
  <div class="card" v-loading="loading">
    <div class="helper">仓库取自仓库管理里已启用的仓库，物料料号取自物料表。安全库存作为保底，不参与库存扣减；预警库存仅用于风险提示。0 ≤ 安全库存 ≤ 预警库存。导入按仓库 + 物料新增或更新，不删除文件里没有的行。</div>
    <el-table :data="rows" border>
      <el-table-column label="仓库" prop="warehouseCode" min-width="120" />
      <el-table-column label="物料料号" prop="materialCode" min-width="160" />
      <el-table-column label="安全库存" prop="safetyStockQty" min-width="110" />
      <el-table-column label="预警库存" prop="warningStockQty" min-width="110" />
      <el-table-column label="创建人" min-width="110"><template #default="{ row }">{{ row.createdByName || '-' }}</template></el-table-column>
      <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column>
      <el-table-column label="更新人" min-width="110"><template #default="{ row }">{{ row.updatedByName || '-' }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="showLogs(row)">查看日志</el-button><el-button v-if="canManage()" type="primary" link @click="edit(row)">编辑</el-button><el-button v-if="canManage()" type="danger" link @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无库存控制策略" />
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </div>

  <el-dialog v-model="editVisible" destroy-on-close :title="editing?.id ? '编辑库存策略' : '新增库存策略'" width="480px">
    <el-form v-if="editing" label-width="90px">
      <el-form-item label="仓库" required>
        <el-select v-model="editing.warehouseCode" :disabled="!!editing.id" filterable clearable placeholder="搜索仓库编码或名称" style="width:100%">
          <el-option v-for="item in warehouses" :key="item.code" :label="`${item.name}（${item.code}）`" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="物料料号" required>
        <PolicyMaterialPicker :key="editing.id || 'new'" v-model="editing.materialCode" kind="stock" :disabled="!!editing.id" />
      </el-form-item>
      <el-form-item label="安全库存"><el-input-number v-model="editing.safetyStockQty" :min="0" /></el-form-item>
      <el-form-item label="预警库存"><el-input-number v-model="editing.warningStockQty" :min="0" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认</el-button></template>
  </el-dialog>

  <ConfigImportDialog v-model="importVisible" title="库存控制策略批量导入"
    :rules="['仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件', '所有必填项不能为空，否则将无法完成导入', '不能存在合并单元格', '按唯一键新增或更新，文件里没有的行不会被删除', '有错误时整份不导入，请下载错误数据修正后重新上传', '0 ≤ 安全库存 ≤ 预警库存', '仓库必须是仓库管理里已启用的仓库，物料料号必须是物料表里的有效物料']"
    :columns="['仓库编码', '物料料号', '安全库存', '预警库存']"
    :preview="mrpApi.previewStockPolicyImport" :commit="mrpApi.importStockPolicies"
    :template="mrpApi.downloadStockPolicyTemplate" :errors="mrpApi.downloadStockPolicyErrors" @imported="load" />

  <ResourceAuditDialog v-model="logVisible" resource-type="stock_policy" :resource-id="logTarget ? `${logTarget.warehouseCode}|${logTarget.materialCode}` : ''" :target="logTarget?.materialCode" />
</template>

<style scoped>
.page-header,.page-actions{display:flex;align-items:center}.page-header{justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-actions{gap:8px}.page-alert{margin-bottom:16px}
.filter-bar{display:flex;gap:12px;align-items:center;margin-bottom:16px;padding:16px 20px;background:#fff;border-radius:8px}
.card{background:#fff;border-radius:8px;padding:16px}.helper{color:#86909C;font-size:13px;margin-bottom:12px}
.steps{margin-bottom:18px}.import-body{display:flex;flex-direction:column;gap:12px}.upload-text{color:#4E5969}.diff{white-space:pre-wrap;word-break:break-all;font-size:12px}
</style>
