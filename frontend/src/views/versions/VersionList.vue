<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { get } from '../../api/client'
import type { PlanVersion } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const versions = ref<PlanVersion[]>([])
const loading = ref(false)
const factoryCode = ref('YH')

async function loadVersions() {
  loading.value = true
  try {
    const res = await get<any>(`/plans?factoryCode=${factoryCode.value}`)
    versions.value = res.data || []
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
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
      <el-select v-model="factoryCode" style="width: 140px" @change="loadVersions">
        <el-option label="永惠" value="YH" />
        <el-option label="APK" value="APK" />
      </el-select>
    </div>

    <!-- 版本列表 -->
    <div class="table-card" v-loading="loading">
      <el-table :data="versions" size="default" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="versionNo" label="版本号" width="100" />
        <el-table-column prop="factoryCode" label="工厂" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === 'PUBLISHED' ? '已发布' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleVersion" label="规则版本" width="100" />
        <el-table-column prop="currentWeekStart" label="当前周" width="120" />
        <el-table-column prop="createdAt" label="创建时间" />
      </el-table>
      <el-empty v-if="!loading && versions.length === 0" description="暂无排产版本" />
    </div>
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

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}
</style>
