<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigImportDialog from './components/ConfigImportDialog.vue'
import { mrpApi, type FactoryWarehouseMappingDetail } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = () => permissionStore.hasPermission('mrp_warehouse_mapping:manage')
const loading = ref(false)
const loadError = ref('')
const rows = ref<FactoryWarehouseMappingDetail[]>([])
const factories = ref<string[]>([])
const keyword = ref('')
const quality = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
function search() { page.value = 1; load() }
const editing = ref<FactoryWarehouseMappingDetail | null>(null)
const editVisible = ref(false)
const saving = ref(false)
const logVisible = ref(false)
const logTarget = ref<FactoryWarehouseMappingDetail | null>(null)
const importVisible = ref(false)
function time(value?: string) { return value ? value.replace('T', ' ').substring(0, 19) : '-' }

async function load() {
  loading.value = true; loadError.value = ''
  try { const [mappings, lines] = await Promise.all([mrpApi.getFactoryWarehouseMappingPage(keyword.value, quality.value, page.value, pageSize.value), mrpApi.getFactories()]); rows.value = mappings.items; total.value = mappings.total; factories.value = lines }
  catch (error) { loadError.value = error instanceof Error ? error.message : '加载失败' }
  finally { loading.value = false }
}
function add() { editing.value = { factoryCode: '', factoryName: '', warehouseCode: '', warehouseName: '', enabled: true, goodWarehouse: true, warehouseAttribute: '自有仓', priorityNo: null }; editVisible.value = true }
function edit(row: FactoryWarehouseMappingDetail) { editing.value = { ...row }; editVisible.value = true }
async function save() {
  if (!editing.value) return
  if (!editing.value.factoryCode || !editing.value.warehouseCode.trim() || !editing.value.warehouseName.trim()) {
    ElMessage.warning('请填写工厂、仓库编码和仓库名称'); return
  }
  saving.value = true
  try {
    editing.value.factoryName = editing.value.factoryCode
    if (editing.value.id) await mrpApi.updateFactoryWarehouseMapping(editing.value.id, editing.value)
    else await mrpApi.createFactoryWarehouseMapping(editing.value)
    editVisible.value = false; ElMessage.success('已保存'); await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function remove(row: FactoryWarehouseMappingDetail) {
  try { await ElMessageBox.confirm(`确认删除仓库 ${row.warehouseName}？`, '删除仓库', { type: 'warning' }); await mrpApi.deleteFactoryWarehouseMapping(row.id!); ElMessage.success('已删除'); await load() }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
function showLogs(row: FactoryWarehouseMappingDetail) { logTarget.value = row; logVisible.value = true }
onMounted(load)
</script>

<template>
  <div class="page-header"><h1 class="page-title">仓库管理</h1><div class="page-actions" v-if="canManage()"><el-button type="primary" @click="importVisible = true">导入</el-button><el-button type="primary" @click="add">新增</el-button></div></div>
  <el-alert v-if="loadError" :title="`加载失败：${loadError}`" type="error" :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
  <div class="filter-bar"><el-input v-model="keyword" placeholder="搜索工厂或仓库" clearable style="width:220px" @keyup.enter="search" @clear="search" /><el-select v-model="quality" style="width:140px" @change="search"><el-option label="全部品质" value="" /><el-option label="良品仓" value="GOOD" /><el-option label="不良品仓" value="DEFECTIVE" /><el-option label="待确认" value="UNCONFIGURED" /></el-select></div>
  <div class="card" v-loading="loading">
    <div class="helper">同一工厂可维护多个仓库。良品仓按数字优先级升序扣减；未填优先级的按首次维护顺序扣减。</div>
    <el-table :data="rows" border>
      <el-table-column label="工厂" prop="factoryCode" min-width="120" /><el-table-column label="仓库编码" prop="warehouseCode" min-width="120" /><el-table-column label="仓库名称" prop="warehouseName" min-width="140" /><el-table-column label="仓库管理员" prop="warehouseManager" min-width="120" /><el-table-column label="对应供应商" prop="supplierName" min-width="120" /><el-table-column label="良品仓" width="90"><template #default="{ row }">{{ row.qualityConfigured ? (row.goodWarehouse ? '是' : '否') : '待确认' }}</template></el-table-column><el-table-column label="仓库属性" prop="warehouseAttribute" min-width="140" /><el-table-column label="优先级" prop="priorityNo" width="90" />
      <el-table-column label="启用" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="创建人" min-width="110"><template #default="{ row }">{{ row.createdByName || '-' }}</template></el-table-column>
      <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column>
      <el-table-column label="更新人" min-width="110"><template #default="{ row }">{{ row.updatedByName || '-' }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="showLogs(row)">查看日志</el-button><el-button v-if="canManage()" type="primary" link @click="edit(row)">编辑</el-button><el-button v-if="canManage()" type="danger" link @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无仓库" />
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </div>
  <el-dialog v-model="editVisible" :title="editing?.id ? '编辑仓库' : '新增仓库'" width="480px">
    <el-form v-if="editing" label-width="100px">
      <el-form-item label="工厂" required><el-select v-model="editing.factoryCode" :disabled="!!editing.id" style="width:100%"><el-option v-for="item in factories" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item label="仓库编码" required><el-input v-model="editing.warehouseCode" :disabled="!!editing.id" /></el-form-item>
      <el-form-item label="仓库名称" required><el-input v-model="editing.warehouseName" /></el-form-item>
      <el-form-item label="仓库管理员"><el-input v-model="editing.warehouseManager" /></el-form-item>
      <el-form-item label="对应供应商"><el-input v-model="editing.supplierName" /></el-form-item>
      <el-form-item label="是否良品仓" required><el-switch v-model="editing.goodWarehouse" active-text="是" inactive-text="否" /></el-form-item>
      <el-form-item label="仓库属性" required><el-select v-model="editing.warehouseAttribute" style="width:100%"><el-option v-for="item in ['自有仓', '加工厂外协仓', 'ODM仓', 'NC-ODM虚拟仓', '海外三方仓', 'OEM仓', '采购仓']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
      <el-form-item label="优先级"><el-input-number v-model="editing.priorityNo" :min="1" :step="1" :precision="0" controls-position="right" /></el-form-item>
      <el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
  <ConfigImportDialog v-model="importVisible" title="仓库批量导入"
    :rules="['仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件', '工厂须先维护；仓库编码、名称、良品属性和仓库属性必填', '同工厂可有多个仓库，仓库编码全局唯一', '优先级选填，正整数升序；未填的按首次维护顺序', '有错误时整份不导入，请下载错误数据修正后重新上传']"
    :columns="['工厂编码', '仓库编码', '仓库名称', '仓库管理员', '对应供应商', '是否良品仓', '仓库属性', '优先级', '启用']"
    :preview="mrpApi.previewFactoryWarehouseImport" :commit="mrpApi.importFactoryWarehouseMappings"
    :template="mrpApi.downloadFactoryWarehouseTemplate" :errors="mrpApi.downloadFactoryWarehouseErrors" @imported="load" />
  <ResourceAuditDialog v-model="logVisible" resource-type="factory_warehouse_mapping" :resource-id="logTarget ? `${logTarget.factoryCode}|${logTarget.warehouseCode}` : ''" :target="logTarget?.warehouseName" />
</template>

<style scoped>
.page-header,.page-actions{display:flex;align-items:center}.page-header{justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-actions{gap:8px}.page-alert{margin-bottom:16px}
.filter-bar{display:flex;gap:12px;align-items:center;margin-bottom:16px;padding:16px 20px;background:#fff;border-radius:8px}
.card{background:#fff;border-radius:8px;padding:16px}.factory-card{margin-bottom:16px}.factory-card h2{font-size:16px;color:#1F2329;margin:0 0 12px}.helper{color:#86909C;font-size:13px;margin-bottom:12px}.diff{white-space:pre-wrap;word-break:break-all;font-size:12px}
</style>
