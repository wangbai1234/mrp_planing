<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PolicyMaterialPicker from './components/PolicyMaterialPicker.vue'
import ConfigImportDialog from './components/ConfigImportDialog.vue'
import { mrpApi, type MaterialPolicyDetail, type MaterialPolicyWrite, type SupplyMode, type ExplosionPolicy } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = () => permissionStore.hasPermission('mrp_config:manage')
const loading = ref(false)
const loadError = ref('')
const rows = ref<MaterialPolicyDetail[]>([])
const keyword = ref('')
const editing = ref<{ id?: number; materialCode: string; supplyMode: SupplyMode | ''; explosionPolicy: ExplosionPolicy | '' } | null>(null)
const editVisible = ref(false)
const saving = ref(false)
const logVisible = ref(false)
const logTarget = ref<MaterialPolicyDetail | null>(null)
const importVisible = ref(false)
const supplyOptions = [{ label: '自制', value: 'SELF_MADE' }, { label: '外购', value: 'PURCHASED' }, { label: '委外', value: 'OUTSOURCED' }]
const explosionOptions = [{ label: '继续下钻', value: 'EXPLODE' }, { label: '停止下钻', value: 'STOP' }]
const label = (options: Array<{ label: string; value: string }>, value?: string) => options.find(item => item.value === value)?.label || '待补充'
const page = ref(1); const pageSize = ref(20); const total = ref(0)
const supplyFilter = ref(''); const explosionFilter = ref('')
function search() { page.value = 1; load() }
function time(value?: string) { return value ? value.replace('T', ' ').substring(0, 19) : '-' }

async function load() { loading.value = true; loadError.value = ''; try { const result = await mrpApi.getMaterialPolicyPage(keyword.value, supplyFilter.value, explosionFilter.value, page.value, pageSize.value); rows.value = result.items; total.value = result.total } catch (error) { loadError.value = error instanceof Error ? error.message : '加载失败' } finally { loading.value = false } }
function add() { editing.value = { materialCode: '', supplyMode: '', explosionPolicy: '' }; editVisible.value = true }
function edit(row: MaterialPolicyDetail) { editing.value = { id: row.id, materialCode: row.materialCode, supplyMode: ['SELF_MADE','PURCHASED','OUTSOURCED'].includes(row.supplyMode) ? row.supplyMode as SupplyMode : '', explosionPolicy: ['EXPLODE','STOP'].includes(row.explosionPolicy) ? row.explosionPolicy as ExplosionPolicy : '' }; editVisible.value = true }
async function save() {
  if (!editing.value) return
  if (!editing.value.materialCode || !editing.value.supplyMode || !editing.value.explosionPolicy) { ElMessage.warning('请选择物料、供应方式和下钻策略'); return }
  const payload: MaterialPolicyWrite = { ...editing.value, supplyMode: editing.value.supplyMode, explosionPolicy: editing.value.explosionPolicy }
  saving.value = true
  try { if (editing.value.id) await mrpApi.updateMaterialPolicy(editing.value.id, payload); else await mrpApi.createMaterialPolicy(payload); editVisible.value = false; ElMessage.success('已保存'); await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function remove(row: MaterialPolicyDetail) { try { await ElMessageBox.confirm(`确认删除 ${row.materialCode} 的物料策略？`, '删除物料策略', { type: 'warning' }); await mrpApi.deleteMaterialPolicy(row.id!); ElMessage.success('已删除'); await load() } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') } }
function showLogs(row: MaterialPolicyDetail) { logTarget.value = row; logVisible.value = true }
onMounted(load)
</script>

<template>
  <div class="page-header"><h1 class="page-title">物料策略</h1><div class="page-actions" v-if="canManage()"><el-button type="primary" @click="importVisible = true">导入</el-button><el-button type="primary" @click="add">新增</el-button></div></div>
  <el-alert v-if="loadError" :title="`加载失败：${loadError}`" type="error" :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
  <div class="filter-bar">
    <el-input v-model="keyword" placeholder="搜索编码、名称或规格" clearable style="width:220px" @keyup.enter="search" @clear="search" />
    <el-select v-model="supplyFilter" value-on-clear="" clearable placeholder="供应方式" style="width:140px" @change="search"><el-option v-for="item in supplyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
    <el-select v-model="explosionFilter" value-on-clear="" clearable placeholder="下钻策略" style="width:140px" @change="search"><el-option v-for="item in explosionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
  </div>
  <div class="card" v-loading="loading">
    <el-table :data="rows" border>
      <el-table-column label="物料料号" prop="materialCode" min-width="180" />
      <el-table-column label="供应方式" min-width="120"><template #default="{ row }">{{ label(supplyOptions, row.supplyMode) }}</template></el-table-column>
      <el-table-column label="下钻策略" min-width="120"><template #default="{ row }">{{ label(explosionOptions, row.explosionPolicy) }}</template></el-table-column>
      <el-table-column label="创建人" min-width="110"><template #default="{ row }">{{ row.createdByName || '-' }}</template></el-table-column>
      <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column>
      <el-table-column label="更新人" min-width="110"><template #default="{ row }">{{ row.updatedByName || '-' }}</template></el-table-column>
      <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><el-button type="primary" link @click="showLogs(row)">查看日志</el-button><el-button v-if="canManage()" type="primary" link @click="edit(row)">编辑</el-button><el-button v-if="canManage()" type="danger" link @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无物料策略" />
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </div>
  <el-dialog v-model="editVisible" destroy-on-close :title="editing?.id ? '编辑物料策略' : '新增物料策略'" width="480px">
    <el-form v-if="editing" label-width="90px">
      <el-form-item label="物料料号" required><PolicyMaterialPicker :key="editing.id || 'new'" v-model="editing.materialCode" kind="material" :disabled="!!editing.id" /></el-form-item>
      <el-form-item label="供应方式" required><el-select v-model="editing.supplyMode" style="width:100%"><el-option v-for="item in supplyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="下钻策略" required><el-select v-model="editing.explosionPolicy" style="width:100%"><el-option v-for="item in explosionOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">确认</el-button></template>
  </el-dialog>
  <ConfigImportDialog v-model="importVisible" title="物料策略批量导入"
    :rules="['仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件', '所有必填项不能为空，否则将无法完成导入', '不能存在合并单元格', '按唯一键新增或更新，文件里没有的行不会被删除', '有错误时整份不导入，请下载错误数据修正后重新上传', '物料必须是物料管理中的有效物料', '供应方式填自制、外购或委外；下钻策略填继续下钻或停止下钻']"
    :columns="['物料料号', '供应方式', '下钻策略']"
    :preview="mrpApi.previewMaterialPolicyImport" :commit="mrpApi.importMaterialPolicies"
    :template="mrpApi.downloadMaterialPolicyTemplate" :errors="mrpApi.downloadMaterialPolicyErrors" @imported="load" />
  <ResourceAuditDialog v-model="logVisible" resource-type="material_policy" :resource-id="logTarget?.materialCode || ''" :target="logTarget?.materialCode" />
</template>

<style scoped>
.page-header,.page-actions{display:flex;align-items:center}.page-header{justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-actions{gap:8px}.page-alert{margin-bottom:16px}
.filter-bar{display:flex;gap:12px;align-items:center;margin-bottom:16px;padding:16px 20px;background:#fff;border-radius:8px}
.card{background:#fff;border-radius:8px;padding:16px}.diff{white-space:pre-wrap;word-break:break-all;font-size:12px}
</style>
