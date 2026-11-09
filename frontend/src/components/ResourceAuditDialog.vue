<script setup lang="ts">
import ResourceAuditViewer from './ResourceAuditViewer.vue'

defineProps<{ modelValue: boolean; resourceType: string; resourceId: string; target?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <el-dialog :model-value="modelValue" :title="target ? `查看日志 · ${target}` : '查看日志'" width="min(760px, 96vw)"
    destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <ResourceAuditViewer v-if="modelValue" :resource-type="resourceType" :resource-id="resourceId" />
  </el-dialog>
</template>
