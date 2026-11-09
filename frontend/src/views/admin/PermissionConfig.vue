<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminLayout from './AdminLayout.vue'
import PermissionTreeNode from '../../components/PermissionTreeNode.vue'
import { getPermissionTree } from '../../api/permission'
import { getRole, getRolePermissions, getRoleDataScope, getRoleFactoryOptions, updateRoleConfiguration } from '../../api/role'
import type { RoleDataScope } from '../../api/role'
import { buildPermissionTree, filterPermissionTree, togglePermissionNode } from '../../utils/permissionTree'
import type { Permission } from '../../types/permission'

const props = defineProps<{
  roleId: number
}>()

const loading = ref(false)
const saving = ref(false)
const catalogLoaded = ref(false)
const selectionLoaded = ref(false)
const scopeLoaded = ref(false)
const roleLoaded = ref(false)
const permissions = ref<Record<number, Permission[]>>({})
const selectedIds = ref<Set<number>>(new Set())
const originalIds = ref<Set<number>>(new Set())
const factoryOptions = ref<string[]>([])
const dataScope = ref<RoleDataScope>({ scopeType: 'NONE', factoryCodes: [] })
const originalScope = ref<RoleDataScope>({ scopeType: 'NONE', factoryCodes: [] })
const searchKeyword = ref('')
const appliedKeyword = ref('')
const showOnlySelected = ref(false)
const expandedKeys = ref<Set<number>>(new Set())

const roleName = ref('')
const roleCode = ref('')

const hasChanges = computed(() => {
  if (dataScope.value.scopeType !== originalScope.value.scopeType) return true
  if (dataScope.value.scopeType === 'FACTORY' &&
      (dataScope.value.factoryCodes.length !== originalScope.value.factoryCodes.length ||
       dataScope.value.factoryCodes.some(code => !originalScope.value.factoryCodes.includes(code)))) return true
  if (selectedIds.value.size !== originalIds.value.size) return true
  for (const id of selectedIds.value) {
    if (!originalIds.value.has(id)) return true
  }
  return false
})

const selectedCount = computed(() => selectedIds.value.size)
const canSave = computed(() => catalogLoaded.value && selectionLoaded.value && scopeLoaded.value && roleLoaded.value && hasChanges.value &&
  (dataScope.value.scopeType !== 'FACTORY' || dataScope.value.factoryCodes.length > 0))

const allPermissions = computed(() => {
  const all: Permission[] = []
  for (const perms of Object.values(permissions.value)) {
    all.push(...perms)
  }
  return all
})

const fullTree = computed(() => buildPermissionTree(allPermissions.value, selectedIds.value))
const treeData = computed(() => filterPermissionTree(fullTree.value, appliedKeyword.value, showOnlySelected.value, selectedIds.value))

function togglePermission(id: number) {
  selectedIds.value = togglePermissionNode(fullTree.value, id, selectedIds.value)
}

function toggleExpand(id: number) {
  if (expandedKeys.value.has(id)) {
    expandedKeys.value.delete(id)
  } else {
    expandedKeys.value.add(id)
  }
}

function expandAll() {
  fullTree.value.forEach(section => expandedKeys.value.add(section.id))
  allPermissions.value.forEach(p => expandedKeys.value.add(p.id))
}

function collapseAll() {
  expandedKeys.value.clear()
}

async function loadPermissions() {
  loading.value = true
  try {
    permissions.value = await getPermissionTree()
    catalogLoaded.value = true
    expandedKeys.value = new Set([...Object.keys(permissions.value).map(Number), ...fullTree.value.map(section => section.id)])
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
    selectionLoaded.value = true
  } catch (e: any) {
    ElMessage.error('加载角色权限失败: ' + e.message)
  }
}

async function loadRole() {
  try {
    const role = await getRole(props.roleId)
    roleName.value = role.name
    roleCode.value = role.code
    roleLoaded.value = true
  } catch (e: any) {
    ElMessage.error('加载角色信息失败: ' + e.message)
  }
}

async function loadDataScope() {
  try {
    const [options, scope] = await Promise.all([getRoleFactoryOptions(), getRoleDataScope(props.roleId)])
    factoryOptions.value = options
    dataScope.value = { scopeType: scope.scopeType, factoryCodes: [...scope.factoryCodes] }
    originalScope.value = { scopeType: scope.scopeType, factoryCodes: [...scope.factoryCodes] }
    scopeLoaded.value = true
  } catch (e: any) {
    ElMessage.error('加载工厂范围失败: ' + e.message)
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateRoleConfiguration(props.roleId, Array.from(selectedIds.value), {
      scopeType: dataScope.value.scopeType,
      factoryCodes: dataScope.value.scopeType === 'FACTORY' ? dataScope.value.factoryCodes : []
    })
    originalIds.value = new Set(selectedIds.value)
    originalScope.value = { scopeType: dataScope.value.scopeType, factoryCodes: [...dataScope.value.factoryCodes] }
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  selectedIds.value = new Set(originalIds.value)
  dataScope.value = { scopeType: originalScope.value.scopeType, factoryCodes: [...originalScope.value.factoryCodes] }
}

onMounted(async () => {
  await Promise.all([loadPermissions(), loadRolePermissions(), loadRole(), loadDataScope()])
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
          <el-button type="primary" @click="handleSave" :loading="saving" :disabled="!canSave">
            保存
          </el-button>
        </div>
      </div>

      <section class="scope-section">
        <h3>工厂可见范围</h3>
        <el-radio-group v-model="dataScope.scopeType" :disabled="roleCode === 'ADMIN'">
          <el-radio value="ALL">全部工厂</el-radio>
          <el-radio value="FACTORY">指定工厂</el-radio>
          <el-radio value="NONE">无工厂权限</el-radio>
        </el-radio-group>
        <el-checkbox-group v-if="dataScope.scopeType === 'FACTORY'" v-model="dataScope.factoryCodes" class="factory-options">
          <el-checkbox v-for="code in factoryOptions" :key="code" :value="code">{{ code }}</el-checkbox>
        </el-checkbox-group>
        <el-empty v-if="dataScope.scopeType === 'FACTORY' && !factoryOptions.length" description="暂无可选工厂" />
      </section>

      <h3 class="permission-heading">功能权限</h3>

      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索权限"
          style="width: 220px"
          clearable
          @input="!searchKeyword && (appliedKeyword = '')"
          @keyup.enter="appliedKeyword = searchKeyword.trim()"
          @clear="appliedKeyword = ''"
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
        <el-empty v-if="!loading && !treeData.length" description="暂无匹配权限" />
        <PermissionTreeNode
          v-else
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

.scope-section {
  padding: 12px 0 16px;
  border-bottom: 1px solid #e8e9eb;
  margin-bottom: 16px;
}

.scope-section h3, .permission-heading {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.factory-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0 16px;
  margin-top: 8px;
}

.tree-container {
  border: 1px solid #e8e9eb;
  border-radius: 8px;
  padding: 12px;
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}
</style>
