<script setup lang="ts">
import { ref, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../../api/client'
import type { CapacityLine } from '../../api/types'

const setCrumb = inject<(g: string, p: string) => void>('setCrumb')!

const lines = ref<CapacityLine[]>([])
const loading = ref(false)
const editDialogVisible = ref(false)
const editingLine = ref<CapacityLine | null>(null)
const isAdding = ref(false)

const emptyLine: CapacityLine = { factoryCode: '永惠', lineCode: '', lineName: '', weeklyCapacity: 0, isActive: true, remark: '' }

async function loadLines() {
  loading.value = true
  try {
    const res = await get<any>('/capacity-lines')
    lines.value = (res as unknown as CapacityLine[]) || []
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

function startAdd() {
  isAdding.value = true
  editingLine.value = { ...emptyLine }
  editDialogVisible.value = true
}

function startEdit(row: CapacityLine) {
  isAdding.value = false
  editingLine.value = { ...row }
  editDialogVisible.value = true
}

async function saveLine() {
  if (!editingLine.value) return
  try {
    if (editingLine.value.id) {
      await put(`/capacity-lines/${editingLine.value.id}`, editingLine.value)
    } else {
      await post('/capacity-lines', editingLine.value)
    }
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    await loadLines()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

async function deleteLine(row: CapacityLine) {
  try {
    await ElMessageBox.confirm('确定要删除该产线吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await del(`/capacity-lines/${row.id}`)
    ElMessage.success('删除成功')
    await loadLines()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message)
    }
  }
}

onMounted(() => {
  setCrumb('配置管理', '产能配置')
  loadLines()
})
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
      <div>
        <h1 style="margin: 0; font-size: 20px; font-weight: 650">产能配置</h1>
        <div style="margin-top: 4px; color: #4b5870; font-size: 13px">按工厂和产线维护周产能。支持新增与整行编辑。</div>
      </div>
      <el-button type="primary" @click="startAdd"><el-icon><Plus /></el-icon>新增产线</el-button>
    </div>
    <el-card shadow="never" v-loading="loading">
      <el-table :data="lines" size="small" border>
        <el-table-column prop="factoryCode" label="工厂" width="80" />
        <el-table-column prop="lineCode" label="产线编码" width="100" />
        <el-table-column prop="lineName" label="产线名称" width="120" />
        <el-table-column prop="weeklyCapacity" label="周产能" width="100" align="right" />
        <el-table-column prop="isActive" label="启用" width="70" align="center">
          <template #default="{ row }"><el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="110" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="startEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="deleteLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && lines.length === 0" description="暂无产能配置" />
    </el-card>

    <el-dialog v-model="editDialogVisible" :title="isAdding ? '新增产线' : '编辑产线'" width="500px">
      <el-form v-if="editingLine" label-width="80px">
        <el-form-item label="工厂"><el-select v-model="editingLine.factoryCode"><el-option label="永惠" value="永惠" /><el-option label="爱培科" value="爱培科" /></el-select></el-form-item>
        <el-form-item label="产线编码"><el-input v-model="editingLine.lineCode" /></el-form-item>
        <el-form-item label="产线名称"><el-input v-model="editingLine.lineName" /></el-form-item>
        <el-form-item label="周产能"><el-input-number v-model="editingLine.weeklyCapacity" :min="1" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editingLine.isActive" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editingLine.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLine">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
