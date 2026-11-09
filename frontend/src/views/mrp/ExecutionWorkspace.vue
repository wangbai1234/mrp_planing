<template>
  <div class="page-header"><h1 class="page-title">生产执行</h1></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button type="primary" link @click="load">重试</el-button></el-alert>
  <div v-loading="loading" class="execution-content">
    <template v-if="batch">
      <div class="version-line"><el-select v-model="selectedId" placeholder="选择已发布版本" style="width:220px" @change="selectBatch"><el-option v-for="item in versions" :key="item.id" :label="`#${item.id} · ${item.factoryCode} · R${item.revisionNo}`" :value="item.id" /></el-select></div>
      <ExecutionConfirm :key="batch.id" :batch-id="batch.id" />
    </template>
    <el-empty v-else-if="!loading && !error" description="暂无可执行排产版本" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mrpApi, type MrpCalcBatch } from '../../api/mrp'
import ExecutionConfirm from './components/ExecutionConfirm.vue'

const batch = ref<MrpCalcBatch | null>(null)
const versions = ref<MrpCalcBatch[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await mrpApi.getBatchesPage(1, 100, 'PUBLISHED')
    versions.value = result.items || []
    selectedId.value = versions.value[0]?.id || null
    batch.value = versions.value[0] || null
  } catch (cause: unknown) {
    error.value = '加载生产执行失败：' + (cause instanceof Error ? cause.message : String(cause))
    ElMessage.error(error.value)
  } finally { loading.value = false }
}
function selectBatch(id: number) { batch.value = versions.value.find(item => item.id === id) || null }
onMounted(load)
</script>

<style scoped>
.page-header { display: flex; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; color: #1F2329; }
.execution-content { background: #fff; border-radius: 8px; padding: 16px; min-height: 280px; }
.version-line { color: #86909C; font-size: 13px; margin-bottom: 16px; }
</style>
