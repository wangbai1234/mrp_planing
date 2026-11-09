<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { mrpApi, type MrpUnallocatedQuantity } from '../../../api/mrp'
const props = defineProps<{ batchId: number }>()
const rows = ref<MrpUnallocatedQuantity[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = await mrpApi.getPendingUnallocatedPage(props.batchId, page.value, pageSize.value, keyword.value)
    rows.value = result.items; total.value = result.total
  }
  catch (cause: unknown) { error.value = cause instanceof Error ? cause.message : '加载失败' }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
onMounted(load)
</script>
<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button type="primary" link @click="load">重新加载</el-button></el-alert>
    <el-alert v-if="total" title="这些需求尚未分配到有效生产日。请检查生产日历，并在修订或重新计算后确认待分配数量归零。" type="warning" :closable="false" />
    <div class="filters"><el-input v-model="keyword" placeholder="搜索需求标识或原因代码" clearable style="width:220px" @keyup.enter="search" @clear="search" /></div>
    <el-table :data="rows" border style="margin-top:12px">
      <el-table-column prop="demandKey" label="需求标识" min-width="260" show-overflow-tooltip />
      <el-table-column prop="sourceDate" label="来源日" width="120" />
      <el-table-column prop="attemptedScheduleDate" label="目标排产日" width="130" />
      <el-table-column prop="quantity" label="待分配数量" width="130" align="right" />
      <el-table-column prop="reasonCode" label="原因代码" min-width="170" />
    </el-table>
    <el-empty v-if="!loading && !error && !rows.length" description="暂无待分配需求" />
    <el-pagination v-if="total" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
  </div>
</template>
<style scoped>.filters{display:flex;gap:12px;margin:12px 0;align-items:center}</style>
