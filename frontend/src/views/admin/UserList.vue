<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, RefreshRight } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import PermissionButton from '../../components/PermissionButton.vue'
import { getUsers, createUser, updateUser, deleteUser, updateUserStatus, assignUserRoles, resetUserPassword } from '../../api/user'
import { getActiveRoles } from '../../api/role'
import type { User, Role } from '../../types/permission'

const loading = ref(false)
const users = ref<User[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const isActiveFilter = ref<boolean | undefined>(undefined)
const roleFilter = ref<number | undefined>(undefined)
const roles = ref<Role[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const editingUser = ref<User | null>(null)
const formData = ref({
  username: '',
  displayName: '',
  department: '',
  email: '',
  phone: '',
  roleIds: [] as number[]
})

const roleDialogVisible = ref(false)
const roleDialogUser = ref<User | null>(null)
const selectedRoleIds = ref<number[]>([])

async function loadUsers() {
  loading.value = true
  try {
    const res = await getUsers({
      keyword: keyword.value || undefined,
      isActive: isActiveFilter.value,
      roleId: roleFilter.value,
      page: page.value,
      pageSize: pageSize.value
    })
    users.value = res.items
    total.value = res.total
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roles.value = await getActiveRoles()
  } catch (e) {
    console.error('Failed to load roles', e)
  }
}

onMounted(() => {
  loadUsers()
  loadRoles()
})

function handleSearch() {
  page.value = 1
  loadUsers()
}

function handlePageChange(p: number) {
  page.value = p
  loadUsers()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadUsers()
}

function openCreateDialog() {
  dialogTitle.value = '新增用户'
  editingUser.value = null
  formData.value = { username: '', displayName: '', department: '', email: '', phone: '', roleIds: [] }
  dialogVisible.value = true
}

function openEditDialog(user: User) {
  dialogTitle.value = '编辑用户'
  editingUser.value = user
  formData.value = {
    username: user.username,
    displayName: user.displayName || '',
    department: user.department || '',
    email: user.email || '',
    phone: user.phone || '',
    roleIds: user.roles?.map(r => r.id) || []
  }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (editingUser.value) {
      await updateUser(editingUser.value.id, {
        displayName: formData.value.displayName,
        department: formData.value.department,
        email: formData.value.email,
        phone: formData.value.phone
      })
      ElMessage.success('更新成功')
    } else {
      await createUser(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

async function handleDelete(user: User) {
  try {
    await ElMessageBox.confirm(`确定删除用户 "${user.displayName || user.username}" 吗？`, '确认删除', { type: 'warning' })
    await deleteUser(user.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

async function handleToggleStatus(user: User) {
  try {
    const newStatus = !user.isActive
    await updateUserStatus(user.id, newStatus)
    ElMessage.success(newStatus ? '已启用' : '已禁用')
    loadUsers()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

async function handleResetPassword(user: User) {
  try {
    await ElMessageBox.confirm(`确定重置 "${user.displayName || user.username}" 的密码吗？`, '确认重置', { type: 'warning' })
    await resetUserPassword(user.id)
    ElMessage.success('密码已重置为默认密码')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('重置失败: ' + e.message)
    }
  }
}

function openRoleDialog(user: User) {
  roleDialogUser.value = user
  selectedRoleIds.value = user.roles?.map(r => r.id) || []
  roleDialogVisible.value = true
}

async function handleSaveRoles() {
  if (!roleDialogUser.value) return
  try {
    await assignUserRoles(roleDialogUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error('分配失败: ' + e.message)
  }
}

function getRoleNames(user: User): string {
  return user.roles?.map(r => r.name).join(', ') || '-'
}
</script>

<template>
  <AdminLayout>
    <div class="user-list">
      <div class="page-header">
        <h2>用户管理</h2>
        <PermissionButton permission="user:create" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新增用户
        </PermissionButton>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名/账号/部门"
          style="width: 220px"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="isActiveFilter" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
        <el-select v-model="roleFilter" placeholder="角色" clearable style="width: 140px" @change="handleSearch">
          <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
        </el-select>
        <el-button @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="users" v-loading="loading" style="width: 100%">
        <el-table-column prop="displayName" label="用户姓名" min-width="120" />
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column label="所属角色" min-width="150">
          <template #default="{ row }">
            {{ getRoleNames(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="department" label="所属部门" min-width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" min-width="160">
          <template #default="{ row }">
            {{ row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <PermissionButton permission="user:update" link @click="openEditDialog(row)">编辑</PermissionButton>
            <PermissionButton permission="user:assign_role" link @click="openRoleDialog(row)">分配角色</PermissionButton>
            <PermissionButton permission="user:update" link @click="handleToggleStatus(row)">
              {{ row.isActive ? '禁用' : '启用' }}
            </PermissionButton>
            <PermissionButton permission="user:update" link @click="handleResetPassword(row)">
              <el-icon><RefreshRight /></el-icon>重置密码
            </PermissionButton>
            <PermissionButton permission="user:delete" link type="danger" @click="handleDelete(row)">删除</PermissionButton>
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

      <!-- Create/Edit Dialog -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
        <el-form :model="formData" label-width="100px">
          <el-form-item label="登录账号" required>
            <el-input v-model="formData.username" :disabled="!!editingUser" placeholder="请输入登录账号" />
          </el-form-item>
          <el-form-item label="用户姓名">
            <el-input v-model="formData.displayName" placeholder="请输入用户姓名" />
          </el-form-item>
          <el-form-item label="所属部门">
            <el-input v-model="formData.department" placeholder="请输入所属部门" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="formData.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="formData.phone" placeholder="请输入手机号" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">确认</el-button>
        </template>
      </el-dialog>

      <!-- Role Assignment Dialog -->
      <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px" destroy-on-close>
        <p style="margin-bottom: 12px; color: #666">
          为 "{{ roleDialogUser?.displayName || roleDialogUser?.username }}" 分配角色
        </p>
        <el-checkbox-group v-model="selectedRoleIds">
          <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">
            {{ role.name }}
          </el-checkbox>
        </el-checkbox-group>
        <template #footer>
          <el-button @click="roleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveRoles">确认</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<style scoped>
.user-list {
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

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
