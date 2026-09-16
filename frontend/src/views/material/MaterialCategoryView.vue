<script setup lang="ts">
import { ref, inject, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../../api/client'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

interface MaterialCategory {
  id: number
  code: string
  name: string
  parentId: number | null
  level: number
  sort: number
  enabled: boolean
  isDeleted: boolean
  children?: MaterialCategory[]
}

const categories = ref<MaterialCategory[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const expandAll = ref(true)

const formVisible = ref(false)
const formMode = ref<'add' | 'edit'>('add')
const formData = ref<any>({})
const formLoading = ref(false)

const allCategories = ref<MaterialCategory[]>([])

async function loadCategories() {
  loading.value = true
  try {
    const tree = await get<MaterialCategory[]>('/material-categories/tree')
    categories.value = tree || []
    const flat = await get<MaterialCategory[]>('/material-categories')
    allCategories.value = flat || []
  } catch (e: any) {
    ElMessage.error('加载分类失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const filteredCategories = computed(() => {
  if (!searchKeyword.value) return categories.value
  const keyword = searchKeyword.value.toLowerCase()
  return filterTree(categories.value, keyword)
})

function filterTree(nodes: MaterialCategory[], keyword: string): MaterialCategory[] {
  const result: MaterialCategory[] = []
  for (const node of nodes) {
    const nameMatch = node.name.toLowerCase().includes(keyword)
    const codeMatch = node.code.toLowerCase().includes(keyword)
    const filteredChildren = node.children ? filterTree(node.children, keyword) : []
    if (nameMatch || codeMatch || filteredChildren.length > 0) {
      result.push({
        ...node,
        children: filteredChildren.length > 0 ? filteredChildren : (nameMatch || codeMatch ? node.children : [])
      })
    }
  }
  return result
}

function getParentOptions() {
  return allCategories.value.filter(c => c.level === 1 && c.enabled)
}

function openAddDialog(parentId?: number) {
  formMode.value = 'add'
  formData.value = {
    code: '',
    name: '',
    parentId: parentId || null,
    sort: 0,
    enabled: true
  }
  formVisible.value = true
}

function openEditDialog(row: MaterialCategory) {
  formMode.value = 'edit'
  formData.value = { ...row }
  formVisible.value = true
}

async function handleSubmit() {
  if (!formData.value.code || !formData.value.name) {
    ElMessage.warning('编码和名称必填')
    return
  }

  formLoading.value = true
  try {
    if (formMode.value === 'add') {
      await post('/material-categories', formData.value)
      ElMessage.success('新增成功')
    } else {
      await put(`/material-categories/${formData.value.id}`, formData.value)
      ElMessage.success('编辑成功')
    }
    formVisible.value = false
    await loadCategories()
  } catch (e: any) {
    ElMessage.error((formMode.value === 'add' ? '新增' : '编辑') + '失败: ' + e.message)
  } finally {
    formLoading.value = false
  }
}

async function handleDelete(row: MaterialCategory) {
  try {
    await ElMessageBox.confirm('确认删除该分类？', '提示', { type: 'warning' })
    await del(`/material-categories/${row.id}`)
    ElMessage.success('删除成功')
    await loadCategories()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

async function toggleEnabled(row: MaterialCategory) {
  try {
    await post(`/material-categories/${row.id}/enabled`, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await loadCategories()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

onMounted(() => {
  setCrumb('基础数据', '物料分类')
  loadCategories()
})
</script>

<template>
  <div class="category-page">
    <div class="page-header">
      <h1 class="page-title">物料分类</h1>
      <div class="page-actions">
        <el-button type="primary" @click="openAddDialog()">
          <el-icon><Plus /></el-icon>新增
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索分类编码/名称" clearable style="width: 220px">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-switch v-model="expandAll" active-text="展开" inactive-text="收起" style="margin-left: 12px" />
    </div>

    <div class="table-card" v-loading="loading">
      <el-table
        :data="filteredCategories"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll"
        size="default"
        border
        style="width: 100%"
        max-height="calc(100vh - 320px)"
      >
        <el-table-column prop="code" label="分类编码" width="120" fixed />
        <el-table-column prop="name" label="分类名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="level" label="层级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'primary' : 'info'" size="small" effect="plain">
              {{ row.level === 1 ? '一级' : '二级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.level === 1" type="primary" link size="small" @click="openAddDialog(row.id)">添加子分类</el-button>
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button :type="row.enabled ? 'warning' : 'success'" link size="small" @click="toggleEnabled(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filteredCategories.length === 0" description="暂无分类数据" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :width="520" :close-on-click-modal="false" class="form-dialog">
      <template #header>
        <div class="dialog-title">{{ formMode === 'add' ? '新增分类' : '编辑分类' }}</div>
      </template>

      <el-form :model="formData" label-width="100px" label-position="top">
        <el-form-item label="分类编码" required>
          <el-input v-model="formData.code" placeholder="请输入分类编码" :disabled="formMode === 'edit'" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select v-model="formData.parentId" placeholder="无（一级分类）" clearable style="width: 100%">
            <el-option v-for="c in getParentOptions()" :key="c.id" :label="`${c.name}（${c.code}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sort" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="formLoading">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.category-page {
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

.page-actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
}

:deep(.form-dialog) {
  border-radius: 12px;
}

:deep(.form-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin: 0;
}

:deep(.form-dialog .el-dialog__body) {
  padding: 16px 24px;
}

:deep(.form-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid #E5E6EB;
}

.dialog-title {
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
