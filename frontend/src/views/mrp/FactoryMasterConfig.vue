<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mrpApi, type FactoryMaster } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = () => permissionStore.hasPermission('mrp_factory:manage')
const loading = ref(false)
const loadError = ref('')
const rows = ref<FactoryMaster[]>([])
const keyword = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
function search() { page.value = 1; load() }
const editing = ref<Pick<FactoryMaster, 'factoryCode' | 'factoryName' | 'enabled'> | null>(null)
const editVisible = ref(false)
const isNew = ref(false)
const saving = ref(false)
const logVisible = ref(false)
const logTarget = ref<FactoryMaster | null>(null)
async function load() {
  loading.value = true; loadError.value = ''
  try { const result = await mrpApi.getFactoryMasterPage(keyword.value, status.value, page.value, pageSize.value); rows.value = result.items; total.value = result.total }
  catch (error) { loadError.value = error instanceof Error ? error.message : '加载失败' }
  finally { loading.value = false }
}
function add() { isNew.value = true; editing.value = { factoryCode: '', factoryName: '', enabled: true }; editVisible.value = true }
function edit(row: FactoryMaster) { isNew.value = false; editing.value = { factoryCode: row.factoryCode, factoryName: row.factoryName, enabled: row.enabled }; editVisible.value = true }
async function save() {
  if (!editing.value) return
  if (!editing.value.factoryCode.trim() || !editing.value.factoryName.trim()) { ElMessage.warning('请填写工厂编码和名称'); return }
  saving.value = true
  try {
    if (isNew.value) await mrpApi.createFactory(editing.value)
    else await mrpApi.updateFactory(editing.value.factoryCode, editing.value)
    editVisible.value = false; ElMessage.success('已保存'); await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function remove(row: FactoryMaster) {
  try { await ElMessageBox.confirm('确认删除工厂 ' + row.factoryName + '？', '删除工厂', { type: 'warning' }); await mrpApi.deleteFactory(row.factoryCode); ElMessage.success('已删除'); await load() }
  catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
function showLogs(row: FactoryMaster) { logTarget.value = row; logVisible.value = true }
onMounted(load)
</script>

<template>
  <div class="page-header"><h1 class="page-title">工厂管理</h1><el-button v-if="canManage()" type="primary" @click="add">新增</el-button></div>
  <el-alert v-if="loadError" :title="'加载失败：' + loadError" type="error" :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
  <div class="filter-bar"><el-input v-model="keyword" placeholder="搜索工厂编码或名称" clearable style="width:220px" @keyup.enter="search" @clear="search" /><el-select v-model="status" style="width:140px" @change="search"><el-option label="全部状态" value="" /><el-option label="启用" value="ENABLED" /><el-option label="停用" value="DISABLED" /></el-select></div>
  <div class="card" v-loading="loading"><el-table :data="rows" border><el-table-column prop="factoryCode" label="工厂编码" /><el-table-column prop="factoryName" label="工厂名称" /><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="200"><template #default="{ row }"><el-button type="primary" link @click="showLogs(row)">查看日志</el-button><el-button v-if="canManage()" type="primary" link @click="edit(row)">编辑</el-button><el-button v-if="canManage()" type="danger" link @click="remove(row)">删除</el-button></template></el-table-column></el-table><el-empty v-if="!loading && !rows.length" description="暂无工厂" /><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
  <el-dialog v-model="editVisible" :title="isNew ? '新增工厂' : '编辑工厂'" width="480px"><el-form v-if="editing" label-width="100px"><el-form-item label="工厂编码" required><el-input v-model="editing.factoryCode" :disabled="!isNew" /></el-form-item><el-form-item label="工厂名称" required><el-input v-model="editing.factoryName" /></el-form-item><el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item></el-form><template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认</el-button></template></el-dialog>
  <ResourceAuditDialog v-model="logVisible" resource-type="mrp_factory" :resource-id="logTarget?.factoryCode || ''" :target="logTarget?.factoryName" />
</template>

<style scoped>
.page-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-alert{margin-bottom:16px}.filter-bar{display:flex;gap:12px;align-items:center;margin-bottom:16px;padding:16px 20px;background:#fff;border-radius:8px}.card{background:#fff;border-radius:8px;padding:16px}
</style>
