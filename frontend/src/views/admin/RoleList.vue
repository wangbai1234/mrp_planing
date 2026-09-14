<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Setting, User, CopyDocument } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import PermissionButton from '../../components/PermissionButton.vue'
import { getRoles, createRole, updateRole, deleteRole, copyRole } from '../../api/role'
import type { Role } from '../../types/permission'

const router = useRouter()
const loading = ref(false)
const roles = ref<Role[]>([])
const keyword = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const editingRole = ref<Role | null>(null)
const formData = ref({
  code: '',
  name: '',
  description: ''
})

const copyDialogVisible = ref(false)
const copySourceRole = ref<Role | null>(null)
const copyFormData = ref({
  code: '',
  name: ''
})

async function loadRoles() {
  loading.value = true
  try {
    roles.value = await getRoles()
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(loadRoles)

function filteredRoles() {
  if (!keyword.value) return roles.value
  const kw = keyword.value.toLowerCase()
  return roles.value.filter(r =>
    r.name.toLowerCase().includes(kw) ||
    r.code.toLowerCase().includes(kw)
  )
}

function openCreateDialog() {
  dialogTitle.value = '新增角色'
  editingRole.value = null
  formData.value = { code: '', name: '', description: '' }
  dialogVisible.value = true
}

function openEditDialog(role: Role) {
  dialogTitle.value = '编辑角色'
  editingRole.value = role
  formData.value = {
    code: role.code,
    name: role.name,
    description: role.description || ''
  }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (editingRole.value) {
      await updateRole(editingRole.value.id, {
        name: formData.value.name,
        description: formData.value.description
      })
      ElMessage.success('更新成功')
    } else {
      await createRole(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRoles()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

async function handleDelete(role: Role) {
  try {
    await ElMessageBox.confirm(`确定删除角色 "${role.name}" 吗？`, '确认删除', { type: 'warning' })
    await deleteRole(role.id)
    ElMessage.success('删除成功')
    loadRoles()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

async function handleToggleStatus(role: Role) {
  try {
    const newStatus = role.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    await updateRole(role.id, { status: newStatus })
    ElMessage.success(newStatus === 'ACTIVE' ? '已启用' : '已禁用')
    loadRoles()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

function openCopyDialog(role: Role) {
  copySourceRole.value = role
  copyFormData.value = {
    code: role.code + '_copy',
    name: role.name + ' (副本)'
  }
  copyDialogVisible.value = true
}

async function handleCopy() {
  if (!copySourceRole.value) return
  try {
    await copyRole(copySourceRole.value.id, copyFormData.value)
    ElMessage.success('复制成功')
    copyDialogVisible.value = false
    loadRoles()
  } catch (e: any) {
    ElMessage.error('复制失败: ' + e.message)
  }
}

function navigateToPermission(role: Role) {
  router.push(`/admin/roles/${role.id}/permissions`)
}
</script>

<template>
  <AdminLayout>
    <div class="role-list">
      <div class="page-header">
        <h2>角色管理</h2>
        <PermissionButton permission="role:create" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新增角色
        </PermissionButton>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索角色名称/编码"
          style="width: 220px"
          clearable
        />
      </div>

      <el-table :data="filteredRoles()" v-loading="loading" style="width: 100%">
        <el-table-column prop="name" label="角色名称" min-width="120" />
        <el-table-column prop="code" label="角色编码" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="用户数量" width="100">
          <template #default="{ row }">
            {{ row.userCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="160">
          <template #default="{ row }">
            {{ new Date(row.updatedAt).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="role:update" link @click="openEditDialog(row)">编辑</PermissionButton>
            <PermissionButton permission="role:assign_permission" link @click="navigateToPermission(row)">
              <el-icon><Setting /></el-icon>权限配置
            </PermissionButton>
            <PermissionButton permission="role:view_members" link>
              <el-icon><User /></el-icon>查看成员
            </PermissionButton>
            <PermissionButton permission="role:create" link @click="openCopyDialog(row)">
              <el-icon><CopyDocument /></el-icon>复制
            </PermissionButton>
            <PermissionButton permission="role:update" link @click="handleToggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </PermissionButton>
            <PermissionButton permission="role:delete" link type="danger" @click="handleDelete(row)">删除</PermissionButton>
          </template>
        </el-table-column>
      </el-table>

      <!-- Create/Edit Dialog -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
        <el-form :model="formData" label-width="100px">
          <el-form-item label="角色编码" required>
            <el-input v-model="formData.code" :disabled="!!editingRole" placeholder="请输入角色编码" />
          </el-form-item>
          <el-form-item label="角色名称" required>
            <el-input v-model="formData.name" placeholder="请输入角色名称" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">确认</el-button>
        </template>
      </el-dialog>

      <!-- Copy Dialog -->
      <el-dialog v-model="copyDialogVisible" title="复制角色" width="400px" destroy-on-close>
        <el-form :model="copyFormData" label-width="100px">
          <el-form-item label="源角色">
            <el-input :value="copySourceRole?.name" disabled />
          </el-form-item>
          <el-form-item label="新编码" required>
            <el-input v-model="copyFormData.code" placeholder="请输入新角色编码" />
          </el-form-item>
          <el-form-item label="新名称" required>
            <el-input v-model="copyFormData.name" placeholder="请输入新角色名称" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="copyDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCopy">确认复制</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<style scoped>
.role-list {
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
}
</style>
