<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get } from '../../api/client'
import type { PlanVersion } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const versions = ref<PlanVersion[]>([])
const loading = ref(false)
const factoryCode = ref('永惠')
const compareVisible = ref(false)
const compareBaseId = ref<number | null>(null)
const compareCurrentId = ref<number | null>(null)
const compareData = ref<any>(null)

async function loadVersions() {
  loading.value = true
  try {
    const res = await get<any>(`/plans?factoryCode=${factoryCode.value}`)
    versions.value = (res as unknown as PlanVersion[]) || []
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function openCompare(version: PlanVersion) {
  compareBaseId.value = version.id
  compareCurrentId.value = versions.value[0]?.id || null
  compareData.value = null
  compareVisible.value = true
}

async function doCompare() {
  if (!compareBaseId.value || !compareCurrentId.value) return
  try {
    const res = await get<any>(`/plans/compare?baseId=${compareBaseId.value}&currentId=${compareCurrentId.value}`)
    compareData.value = res
  } catch (e: any) {
    ElMessage.error('对比失败: ' + e.message)
  }
}

onMounted(() => {
  setCrumb('计划管理', '版本记录')
  loadVersions()
})
</script>

<template>
  <div class="version-page">
    <!-- Page Head -->
    <div class="page-header">
      <h1 class="page-title">版本记录</h1>
      <div style="display: flex; gap: 8px; align-items: center">
        <el-select v-model="factoryCode" style="width: 140px" @change="loadVersions">
          <el-option label="永惠" value="永惠" />
          <el-option label="爱培科" value="爱培科" />
        </el-select>
        <el-button type="primary" @click="$router.push('/planning')">
          <el-icon><Calendar /></el-icon>返回当前排产
        </el-button>
      </div>
    </div>

    <!-- 版本列表 -->
    <el-card shadow="never" body-style="padding: 0" v-loading="loading">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600">排产版本</span>
          <el-tag type="warning" size="small" effect="plain">禁止回滚</el-tag>
        </div>
      </template>
      <el-table :data="versions" size="default" border>
        <el-table-column prop="id" label="排产版本" width="120">
          <template #default="{ row, $index }">
            <span style="font-family: monospace">V{{ row.versionNo }}</span>
            <el-tag v-if="$index === 0" type="primary" size="small" style="margin-left: 4px">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="160" />
        <el-table-column label="触发原因" min-width="180">
          <template #default="{ row }">
            <span>{{ row.status === 'PUBLISHED' ? '计划员手动重算' : '系统自动重算' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="经营计划版本" width="140">
          <template #default="{ row }">
            <span style="font-family: monospace">FC-V{{ row.forecastVersionId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存快照" width="120">
          <template #default="{ row }">
            <span>{{ row.inventorySnapshotId ? 'ID:' + row.inventorySnapshotId : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 'PUBLISHED' ? '已发布' : row.status === 'READY' ? '草稿' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="人工覆盖" width="100">
          <template #default>
            <span>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openCompare(row)">与当前对比</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && versions.length === 0" description="暂无排产版本" />
    </el-card>

    <!-- 版本对比对话框 -->
    <el-dialog v-model="compareVisible" title="排产版本对比" width="700px">
      <el-row :gutter="12" style="margin-bottom: 16px">
        <el-col :span="12">
          <div style="margin-bottom: 4px; font-size: 12px; color: #748096">基线版本</div>
          <el-select v-model="compareBaseId" style="width: 100%">
            <el-option v-for="v in versions" :key="v.id" :label="'V' + v.versionNo + ' - ' + v.createdAt" :value="v.id" />
          </el-select>
        </el-col>
        <el-col :span="12">
          <div style="margin-bottom: 4px; font-size: 12px; color: #748096">当前版本</div>
          <el-select v-model="compareCurrentId" style="width: 100%">
            <el-option v-for="v in versions" :key="v.id" :label="'V' + v.versionNo + ' - ' + v.createdAt" :value="v.id" />
          </el-select>
        </el-col>
      </el-row>
      <el-button type="primary" @click="doCompare" :disabled="!compareBaseId || !compareCurrentId || compareBaseId === compareCurrentId">开始对比</el-button>
      <el-table v-if="compareData" :data="compareData.differences || []" border size="small" style="margin-top: 12px">
        <el-table-column prop="materialId" label="整机料号" width="140" />
        <el-table-column prop="weekStartDate" label="周" width="120" />
        <el-table-column prop="baseQty" label="基线数量" width="100" />
        <el-table-column prop="currentQty" label="当前数量" width="100" />
        <el-table-column prop="diff" label="差异" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.diff > 0 ? '#00B42A' : row.diff < 0 ? '#F53F3F' : '#1F2329' }">
              {{ row.diff > 0 ? '+' : '' }}{{ row.diff }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="compareData && (!compareData.differences || compareData.differences.length === 0)" description="两个版本无差异" :image-size="40" />
      <template #footer>
        <el-button @click="compareVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.version-page {
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}
</style>
