<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mrpApi, type MaterialPolicyNotice } from '../../../api/mrp'
import { usePermissionStore } from '../../../stores/permission'
const props = defineProps<{ batchId: number }>()
const emit = defineEmits<{ changed: [] }>()
const permission = usePermissionStore()
const rows = ref<MaterialPolicyNotice[]>([]); const total = ref(0); const page = ref(1); const pageSize = ref(20)
const keyword = ref(''); const missingType = ref(''); const loading = ref(false); const error = ref('')
const pendingDefaultCount = ref(0); const confirming = ref(false)
const missingLabels: Record<string, string> = { BOTH: '供应方式、下钻策略', SUPPLY: '供应方式', EXPLOSION: '下钻策略' }
async function load() {
  loading.value = true; error.value = ''
  try {
    const [result, pending] = await Promise.all([
      mrpApi.getMaterialPolicyNotices(props.batchId, keyword.value, missingType.value, page.value, pageSize.value),
      mrpApi.getPendingDefaultExplosionCount(props.batchId)
    ])
    rows.value = result.items; total.value = result.total; pendingDefaultCount.value = pending
  }
  catch (cause: unknown) { error.value = cause instanceof Error ? cause.message : '加载失败' }
  finally { loading.value = false }
}
function search() { page.value = 1; load() }
async function confirmDefault() {
  try {
    await ElMessageBox.confirm(`本次排产有 ${pendingDefaultCount.value} 条缺下钻策略提示。确认已按默认规则继续下钻到结构叶子？这只记录本次确认，不写入物料策略或供应方式。`, '批量确认默认继续下钻', { confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch { return }
  confirming.value = true
  try {
    const changed = await mrpApi.confirmDefaultExplosion(props.batchId)
    ElMessage.success(`已确认 ${changed} 条默认继续下钻提示`)
    await load(); emit('changed')
  } catch (cause: unknown) { ElMessage.error('确认失败: ' + (cause instanceof Error ? cause.message : String(cause))) }
  finally { confirming.value = false }
}
interface Source { rootMaterialCode: string; sourceMonth: string; pathText: string }
function sources(row: MaterialPolicyNotice): Source[] { return JSON.parse(row.sourcesJson) as Source[] }
onMounted(load)
</script>
<template>
  <el-alert title="该清单随本次排产结果保存。缺下钻策略时，本次结果已默认继续下钻至结构叶子；有效停止策略仍生效。确认只消除本次待处理提醒，不补写供应方式或物料策略。" type="info" :closable="false" show-icon />
  <div class="filters">
    <el-input v-model="keyword" placeholder="搜索物料编码或名称" clearable style="width:220px" @keyup.enter="search" @clear="search" />
    <el-select v-model="missingType" value-on-clear="" placeholder="缺失项" clearable style="width:140px" @change="search"><el-option v-for="(label, value) in missingLabels" :key="value" :label="label" :value="value" /></el-select>
    <el-button v-if="permission.hasPermission('mrp_decision:manage') && pendingDefaultCount > 0" type="primary" :loading="confirming" @click="confirmDefault">批量确认默认继续下钻（{{ pendingDefaultCount }}）</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button type="primary" link @click="load">重新加载</el-button></el-alert>
  <div v-loading="loading">
    <el-table :data="rows" border>
      <el-table-column type="expand"><template #default="{ row }"><el-table :data="sources(row)" border><el-table-column prop="rootMaterialCode" label="来源整机" width="180" /><el-table-column prop="sourceMonth" label="来源月份" width="140" /><el-table-column prop="pathText" label="BOM 路径" /></el-table></template></el-table-column>
      <el-table-column prop="materialCode" label="物料料号" min-width="160" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" />
      <el-table-column prop="factoryCode" label="工厂" width="110" />
      <el-table-column label="缺失配置" min-width="160"><template #default="{ row }">{{ missingLabels[row.missingType] }}</template></el-table-column>
      <el-table-column label="实际下钻策略" min-width="180"><template #default="{ row }">{{ row.effectiveExplosionPolicy === 'STOP' ? '停止下钻（已配置）' : row.missingType === 'SUPPLY' ? '继续下钻（已配置）' : '继续下钻（默认）' }}</template></el-table-column>
      <el-table-column label="默认下钻确认" width="140"><template #default="{ row }">{{ row.missingType === 'SUPPLY' ? '无需确认' : row.defaultExplosionConfirmedAt ? '本次已确认' : '待确认' }}</template></el-table-column>
    </el-table>
    <el-empty v-if="!loading && !error && !rows.length" description="暂无匹配的缺失记录；历史计算可能未记录此清单" />
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </div>
</template>
<style scoped>.filters{display:flex;gap:12px;margin:12px 0;align-items:center}</style>
