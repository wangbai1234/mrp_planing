<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigImportDialog from './components/ConfigImportDialog.vue'
import { mrpApi, type BusinessWeekDetail } from '../../api/mrp'
import ResourceAuditDialog from '../../components/ResourceAuditDialog.vue'
import { usePermissionStore } from '../../stores/permission'

const permissionStore = usePermissionStore()
const canManage = () => permissionStore.hasPermission('mrp_business_week:manage')
const loading = ref(false)
const loadError = ref('')
const weeks = ref<BusinessWeekDetail[]>([])
const editingMonth = ref('')
const editing = ref<BusinessWeekDetail[]>([])
const editVisible = ref(false)
const saving = ref(false)
const logVisible = ref(false)
const logTarget = ref('')
const importVisible = ref(false)
const months = computed(() => [...new Set(weeks.value.map(item => item.businessMonth || '').filter(Boolean))].sort().reverse())
function time(value?: string) { return value ? value.replace('T', ' ').substring(0, 19) : '-' }

async function load() { loading.value = true; loadError.value = ''; try { weeks.value = await mrpApi.getBusinessWeeks() } catch (error) { loadError.value = error instanceof Error ? error.message : '加载失败' } finally { loading.value = false } }
function rowsOf(month: string) { return weeks.value.filter(item => item.businessMonth === month).map(item => ({ ...item })) }
function openEditor(month: string, rows: BusinessWeekDetail[]) {
  editingMonth.value = month
  editing.value = rows
  editVisible.value = true
}
function openMonth(value: string) { openEditor(value, rowsOf(value).length ? rowsOf(value) : [{ weekCode: 'W1', startDate: '', endDate: '' }]) }
function createMonth() { openEditor('', [{ weekCode: 'W1', startDate: '', endDate: '' }]) }
function addWeek() { editing.value.push({ weekCode: `W${editing.value.length + 1}`, startDate: '', endDate: '' }) }
function removeWeek(index: number) { editing.value.splice(index, 1); editing.value.forEach((row, position) => { row.weekCode = `W${position + 1}` }) }
async function save() {
  if (!editingMonth.value) { ElMessage.error('请选择业务月份'); return }
  saving.value = true
  try { await mrpApi.saveBusinessWeeks(editingMonth.value, editing.value); editVisible.value = false; ElMessage.success('已保存'); await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function remove(value: string) { try { await ElMessageBox.confirm(`确认删除 ${value} 的全部业务周？`, '删除业务周', { type: 'warning' }); await mrpApi.deleteBusinessMonth(value); ElMessage.success('已删除'); await load() } catch (error) { if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '删除失败') } }
function showLogs(value: string) { logTarget.value = value; logVisible.value = true }
onMounted(load)
</script>

<template>
  <div class="page-header"><h1 class="page-title">业务周期间</h1><div class="page-actions" v-if="canManage()"><el-button type="primary" @click="importVisible = true">导入</el-button><el-button type="primary" @click="createMonth">新增月份</el-button></div></div>
  <el-alert v-if="loadError" :title="`加载失败：${loadError}`" type="error" :closable="false" class="page-alert"><template #default><el-button type="primary" link @click="load">重新加载</el-button></template></el-alert>
  <div class="card" v-loading="loading">
    <div class="helper">按月维护。同一月的各周必须连续、不重叠，并覆盖整个业务月，可以跨自然月。保存按月记一条日志。</div>
    <div v-for="item in months" :key="item" class="month-block">
      <div class="month-head"><strong>{{ item }}</strong><div><el-button type="primary" link @click="showLogs(item)">查看日志</el-button><el-button v-if="canManage()" type="primary" link @click="openMonth(item)">编辑</el-button><el-button v-if="canManage()" type="danger" link @click="remove(item)">删除</el-button></div></div>
      <el-table :data="weeks.filter(row => row.businessMonth === item)" border>
        <el-table-column label="周" prop="weekCode" width="100" /><el-table-column label="开始日期" prop="startDate" min-width="140" /><el-table-column label="结束日期" prop="endDate" min-width="140" />
        <el-table-column label="更新人" min-width="110"><template #default="{ row }">{{ row.updatedByName || '-' }}</template></el-table-column>
        <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ time(row.updatedAt) }}</template></el-table-column>
      </el-table>
    </div>
    <el-empty v-if="!loading && !months.length" description="尚未维护业务周期间" />
  </div>
  <el-dialog v-model="editVisible" :title="editingMonth && months.includes(editingMonth) ? `维护 ${editingMonth}` : '新增月份'" width="640px">
    <el-form label-width="80px"><el-form-item label="业务月份"><el-date-picker v-model="editingMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" :disabled="months.includes(editingMonth)" /></el-form-item></el-form>
    <el-table :data="editing" border><el-table-column label="周" width="120"><template #default="{ $index }"><span>W{{ $index + 1 }}</span></template></el-table-column><el-table-column label="开始日期" min-width="180"><template #default="{ row }"><el-date-picker v-model="row.startDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column><el-table-column label="结束日期" min-width="180"><template #default="{ row }"><el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" /></template></el-table-column><el-table-column label="操作" width="80"><template #default="{ $index }"><el-button type="danger" link @click="removeWeek($index)">删除</el-button></template></el-table-column></el-table>
    <el-button class="add-week" @click="addWeek">新增一周</el-button>
    <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
  <ConfigImportDialog v-model="importVisible" title="业务周期间批量导入"
    :rules="['仅支持上传小于 30 MB 且不超过 50,000 条记录的 .xlsx 文件', '所有必填项不能为空，否则将无法完成导入', '不能存在合并单元格', '按唯一键新增或更新，文件里没有的行不会被删除', '有错误时整份不导入，请下载错误数据修正后重新上传', '文件里出现的月份必须连续、不重叠并覆盖整月，没出现的月份不动']"
    :columns="['业务月份', '周编码', '开始日期', '结束日期']"
    :preview="mrpApi.previewBusinessWeekImport" :commit="mrpApi.importBusinessWeeks"
    :template="mrpApi.downloadBusinessWeekTemplate" :errors="mrpApi.downloadBusinessWeekErrors" @imported="load" />
  <ResourceAuditDialog v-model="logVisible" resource-type="business_week" :resource-id="`${logTarget}-01`" :target="logTarget" />
</template>

<style scoped>
.page-header,.page-actions,.month-head{display:flex;align-items:center}.page-header{justify-content:space-between;margin-bottom:16px}.page-title{margin:0;font-size:20px;font-weight:600;color:#1F2329}.page-actions{gap:8px}.page-alert{margin-bottom:16px}
.card{background:#fff;border-radius:8px;padding:16px}.helper{color:#86909C;font-size:13px;margin-bottom:12px}.month-block{margin-bottom:18px}.month-head{justify-content:space-between;margin-bottom:8px}.add-week{margin-top:12px}.diff{white-space:pre-wrap;word-break:break-all;font-size:12px}
</style>
