<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { post } from '../../api/client'

const planVersionId = ref<number | null>(null)
const fields = ref([
  { label: '整机料号', selected: true },
  { label: '机型', selected: true },
  { label: '机头料号', selected: false },
  { label: '备注', selected: false },
  { label: '合计', selected: true }
])
const includePriority = ref(false)
const includeActual = ref(false)
const loading = ref(false)

async function handleExport() {
  if (!planVersionId.value) {
    ElMessage.warning('请先加载排产计划')
    return
  }
  loading.value = true
  try {
    const selectedFields = fields.value.filter(f => f.selected).map(f => f.label)
    await post<any>(`/plans/${planVersionId.value}/exports`, {
      fields: selectedFields,
      includePriority: includePriority.value,
      includeActual: includeActual.value
    })
    ElMessage.success('导出任务已创建')
  } catch (e: any) {
    ElMessage.error('导出失败: ' + e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <template #header>排产导出</template>
    <el-form label-width="100px">
      <el-form-item label="选择字段">
        <el-checkbox v-for="f in fields" :key="f.label" v-model="f.selected">{{ f.label }}</el-checkbox>
      </el-form-item>
      <el-form-item label="包含优先级">
        <el-switch v-model="includePriority" />
      </el-form-item>
      <el-form-item label="包含实际完成">
        <el-switch v-model="includeActual" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleExport" :loading="loading">导出 Excel</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
