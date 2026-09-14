<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import PermissionTreeNode from '../../components/PermissionTreeNode.vue'
import { getPermissionTree } from '../../api/permission'
import { getRolePermissions, updateRolePermissions } from '../../api/role'
import type { Permission } from '../../types/permission'

const props = defineProps<{
  roleId: number
}>()

const loading = ref(false)
const saving = ref(false)
const permissions = ref<Record<number, Permission[]>>({})
const selectedIds = ref<Set<number>>(new Set())
const originalIds = ref<Set<number>>(new Set())
const searchKeyword = ref('')
const showOnlySelected = ref(false)
const expandedKeys = ref<Set<number>>(new Set())

const roleName = ref('')
const roleCode = ref('')

const hasChanges = computed(() => {
  if (selectedIds.value.size !== originalIds.value.size) return true
  for (const id of selectedIds.value) {
    if (!originalIds.value.has(id)) return true
  }
  return false
})

const selectedCount = computed(() => selectedIds.value.size)

const allPermissions = computed(() => {
  const all: Permission[] = []
  for (const perms of Object.values(permissions.value)) {
    all.push(...perms)
  }
  return all
})

const treeData = computed(() => {
  const root = permissions.value[null as any] || permissions.value[0] || []
  return buildTree(root)
})

function buildTree(parents: Permission[]): TreeNode[] {
  return parents
    .filter(p => {
      if (showOnlySelected.value && !isInSelectionTree(p.id)) return false
      if (searchKeyword.value) {
        const kw = searchKeyword.value.toLowerCase()
        return p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw) || hasMatchingChild(p.id, kw)
      }
      return true
    })
    .map(p => ({
      ...p,
      children: permissions.value[p.id] ? buildTree(permissions.value[p.id]) : [],
      checked: isSelected(p.id),
      indeterminate: isIndeterminate(p.id)
    }))
}

interface TreeNode extends Permission {
  children: TreeNode[]
  checked: boolean
  indeterminate: boolean
}

function isInSelectionTree(id: number): boolean {
  if (selectedIds.value.has(id)) return true
  const children = permissions.value[id] || []
  return children.some(c => isInSelectionTree(c.id))
}

function hasMatchingChild(parentId: number, keyword: string): boolean {
  const children = permissions.value[parentId] || []
  return children.some(c =>
    c.name.toLowerCase().includes(keyword) ||
    c.code.toLowerCase().includes(keyword) ||
    hasMatchingChild(c.id, keyword)
  )
}

function isSelected(id: number): boolean {
  return selectedIds.value.has(id)
}

function isIndeterminate(id: number): boolean {
  const children = permissions.value[id] || []
  if (children.length === 0) return false
  const selectedCount = children.filter(c => selectedIds.value.has(c.id)).length
  return selectedCount > 0 && selectedCount < children.length
}

function togglePermission(id: number) {
  if (selectedIds.value.has(id)) {
    deselectRecursive(id)
  } else {
    selectRecursive(id)
  }
}

function selectRecursive(id: number) {
  selectedIds.value.add(id)
  const children = permissions.value[id] || []
  children.forEach(c => selectRecursive(c.id))
}

function deselectRecursive(id: number) {
  selectedIds.value.delete(id)
  const children = permissions.value[id] || []
  children.forEach(c => deselectRecursive(c.id))
}

function toggleExpand(id: number) {
  if (expandedKeys.value.has(id)) {
    expandedKeys.value.delete(id)
  } else {
    expandedKeys.value.add(id)
  }
}

function expandAll() {
  allPermissions.value.forEach(p => expandedKeys.value.add(p.id))
}

function collapseAll() {
  expandedKeys.value.clear()
}

async function loadPermissions() {
  loading.value = true
  try {
    permissions.value = await getPermissionTree()
    expandedKeys.value = new Set(Object.keys(permissions.value).map(Number))
  } catch (e: any) {
    ElMessage.error('加载权限树失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadRolePermissions() {
  try {
    const ids = await getRolePermissions(props.roleId)
    selectedIds.value = new Set(ids)
    originalIds.value = new Set(ids)
  } catch (e: any) {
    ElMessage.error('加载角色权限失败: ' + e.message)
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateRolePermissions(props.roleId, Array.from(selectedIds.value))
    originalIds.value = new Set(selectedIds.value)
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  selectedIds.value = new Set(originalIds.value)
}

onMounted(async () => {
  await loadPermissions()
  await loadRolePermissions()
})
</script>

<template>
  <AdminLayout>
    <div class="permission-config">
      <div class="page-header">
        <div>
          <h2>权限配置</h2>
          <div class="role-info">
            <span>角色: {{ roleName || roleCode }}</span>
            <span class="separator">|</span>
            <span>已选权限: {{ selectedCount }}</span>
            <span v-if="hasChanges" class="unsaved">（未保存）</span>
          </div>
        </div>
        <div class="actions">
          <el-button @click="handleCancel" :disabled="!hasChanges">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saving" :disabled="!hasChanges">
            保存
          </el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索权限"
          style="width: 200px"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-checkbox v-model="showOnlySelected">只显示已选</el-checkbox>
        <el-button link @click="expandAll">全部展开</el-button>
        <el-button link @click="collapseAll">全部收起</el-button>
      </div>

      <div class="tree-container" v-loading="loading">
        <PermissionTreeNode
          :nodes="treeData"
          :expanded-keys="expandedKeys"
          @toggle="togglePermission"
          @expand="toggleExpand"
        />
      </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
.permission-config {
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.role-info {
  font-size: 14px;
  color: #666;
}

.separator {
  margin: 0 8px;
  color: #ddd;
}

.unsaved {
  color: #ff6b6b;
  font-weight: 500;
}

.actions {
  display: flex;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 8px;
}

.tree-container {
  border: 1px solid #e8e9eb;
  border-radius: 8px;
  padding: 12px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}
</style>
