<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import { get } from '../../api/client'

interface AuditLog {
  id: number
  operatorId: number
  operatorRole: string
  factoryScope: string
  action: string
  resourceType: string
  resourceId: string
  beforeValue: string
  afterValue: string
  traceId: string
  ipAddress: string
  createdAt: string
}

const loading = ref(false)
const logs = ref<AuditLog[]>([])
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

    const res = await get<{ items: AuditLog[]; total: number }>(`/audit-logs?${params.toString()}`)
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

function getActionLabel(action: string): string {
  const labels: Record<string, string> = {
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    ENABLE: '启用',
    DISABLE: '禁用',
    ASSIGN_ROLE: '分配角色',
    UPDATE_PERMISSIONS: '更新权限',
    RESET_PASSWORD: '重置密码',
    COPY_ROLE: '复制角色',
    LOGIN: '登录'
  }
  return labels[action] || action
}

function getResourceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    USER: '用户',
    ROLE: '角色',
    PERMISSION: '权限',
    FORECAST: '经营计划',
    INVENTORY: '库存',
    PLAN: '排产'
  }
  return labels[type] || type
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
          style="width: 180px"
          clearable
          @keyup.enter="handleSearch"
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
        <el-button @click="handleSearch">查询</el-button>
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
            <el-tag size="small">{{ getActionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource_type" label="对象类型" width="100">
          <template #default="{ row }">
            {{ getResourceTypeLabel(row.resource_type) }}
          </template>
        </el-table-column>
        <el-table-column prop="resource_id" label="对象ID" width="100" />
        <el-table-column prop="ip_address" label="IP地址" width="140" />
        <el-table-column prop="trace_id" label="TraceID" width="120" />
        <el-table-column label="详情" width="80">
          <template #default="{ row }">
            <el-tooltip v-if="row.before_value || row.after_value" placement="left">
              <template #content>
                <div style="max-width: 400px; white-space: pre-wrap;">
                  <div v-if="row.before_value"><strong>修改前:</strong> {{ row.before_value }}</div>
                  <div v-if="row.after_value"><strong>修改后:</strong> {{ row.after_value }}</div>
                </div>
              </template>
              <el-button link>查看</el-button>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

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
