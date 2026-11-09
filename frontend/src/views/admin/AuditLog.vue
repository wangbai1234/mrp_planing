<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import { get } from '../../api/client'
import AuditLogViewer from '../../components/AuditLogViewer.vue'
import type { AuditLogRecord } from '../../api/audit'
import { auditAction, auditResource } from '../../utils/auditPresentation'

const loading = ref(false)
const logs = ref<AuditLogRecord[]>([])
const selectedLog = ref<AuditLogRecord | null>(null)
const detailVisible = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = ref({
  keyword: '',
  action: '',
  resourceType: '',
  startDate: '',
  endDate: ''
})

async function loadLogs() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.set('page', String(page.value))
    params.set('pageSize', String(pageSize.value))
    if (filters.value.keyword) params.set('keyword', filters.value.keyword)
    if (filters.value.action) params.set('action', filters.value.action)
    if (filters.value.resourceType) params.set('resourceType', filters.value.resourceType)
    if (filters.value.startDate) params.set('startDate', filters.value.startDate)
    if (filters.value.endDate) params.set('endDate', filters.value.endDate)

    const res = await get<{ items: AuditLogRecord[]; total: number }>(`/audit-logs?${params.toString()}`)
    logs.value = res.items
    total.value = res.total
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)

function handleSearch() {
  page.value = 1
  loadLogs()
}

function handlePageChange(p: number) {
  page.value = p
  loadLogs()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadLogs()
}

</script>

<template>
  <AdminLayout>
    <div class="audit-log">
      <div class="page-header">
        <h2>操作日志</h2>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索操作人/对象"
          style="width: 220px"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="filters.action" placeholder="操作类型" clearable style="width: 140px" @change="handleSearch">
          <el-option label="创建" value="CREATE" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
          <el-option label="启用" value="ENABLE" />
          <el-option label="禁用" value="DISABLE" />
          <el-option label="分配角色" value="ASSIGN_ROLE" />
          <el-option label="更新权限" value="UPDATE_PERMISSIONS" />
        </el-select>
        <el-select v-model="filters.resourceType" placeholder="对象类型" clearable style="width: 120px" @change="handleSearch">
          <el-option label="用户" value="USER" />
          <el-option label="角色" value="ROLE" />
          <el-option label="权限" value="PERMISSION" />
        </el-select>
        <el-date-picker
          v-model="filters.startDate"
          type="date"
          placeholder="开始日期"
          style="width: 140px"
          @change="handleSearch"
        />
        <el-date-picker
          v-model="filters.endDate"
          type="date"
          placeholder="结束日期"
          style="width: 140px"
          @change="handleSearch"
        />
      </div>

      <el-table :data="logs" v-loading="loading" style="width: 100%">
        <el-table-column prop="created_at" label="操作时间" min-width="160">
          <template #default="{ row }">
            {{ row.created_at ? new Date(row.created_at + 'Z').toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="operator_id" label="操作人ID" width="100" />
        <el-table-column prop="action" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ auditAction(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource_type" label="对象类型" width="100">
          <template #default="{ row }">
            {{ auditResource(row.resource_type) }}
          </template>
        </el-table-column>
        <el-table-column prop="resource_id" label="对象ID" width="100" />
        <el-table-column prop="ip_address" label="IP地址" width="140" />
        <el-table-column prop="trace_id" label="TraceID" width="120" />
        <el-table-column label="详情" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="selectedLog = row; detailVisible = true">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !logs.length" description="暂无操作日志" />

      <el-dialog v-model="detailVisible" title="查看日志" width="min(760px, 96vw)">
        <AuditLogViewer :logs="selectedLog ? [selectedLog] : []" />
      </el-dialog>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.audit-log {
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
