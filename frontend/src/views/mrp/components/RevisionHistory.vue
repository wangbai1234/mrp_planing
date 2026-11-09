<template>
  <div class="revision-history">
    <div class="history-container" v-loading="loading">
      <el-table :data="revisions" border style="width: 100%">
        <el-table-column prop="id" label="批次ID" width="80" />
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="batchStatusDisplay(row.status).type">{{ batchStatusDisplay(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="revisionNo" label="修订版本" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="180">
          <template #default="{ row }">
            {{ row.publishedAt ? formatTime(row.publishedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">查看详情</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" type="primary" link @click="createRevision(row)">生成修订版</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { mrpApi, type MrpCalcBatch } from '../../../api/mrp'
import { batchStatusDisplay } from '../../../utils/mrpDisplay'

const props = defineProps<{
  batchId: number
}>()

const loading = ref(false)
const revisions = ref<MrpCalcBatch[]>([])
const router = useRouter()

function formatTime(ts: string) {
  if (!ts) return '-'
  return ts.replace('T', ' ').substring(0, 19)
}

function viewDetail(batch: MrpCalcBatch) {
  router.push(`/mrp/batches/${batch.id}`)
}

async function createRevision(batch: MrpCalcBatch) {
  try {
    await ElMessageBox.confirm('确认生成修订版？', '确认')
    await mrpApi.reviseBatch(batch.id)
    ElMessage.success('修订版已创建')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('创建修订版失败: ' + e.message)
    }
  }
}

async function loadData() {
  loading.value = true
  try {
    // Load all batches (revisions) for this factory
    const allBatches = await mrpApi.getBatches()
    revisions.value = allBatches.filter(b => b.id === props.batchId || b.revisionOfBatchId === props.batchId)
  } catch (e: any) {
    ElMessage.error('加载版本历史失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.revision-history {
  padding: 16px 0;
}

.history-container {
  margin-bottom: 16px;
}
</style>
