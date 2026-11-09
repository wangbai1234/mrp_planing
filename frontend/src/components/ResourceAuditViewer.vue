<script setup lang="ts">
import { ref, watch } from 'vue'
import { getAuditLogs, type AuditLogRecord } from '../api/audit'
import AuditLogViewer from './AuditLogViewer.vue'

const props = defineProps<{ resourceType: string; resourceId: string }>()
const logs = ref<AuditLogRecord[]>([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const page = ref(1)
let requestId = 0

async function load() {
  if (!props.resourceId) return
  const current = ++requestId
  loading.value = true; error.value = ''
  try {
    const result = await getAuditLogs({ resourceType: props.resourceType, resourceId: props.resourceId, page: page.value, pageSize: 20 })
    if (current === requestId) { logs.value = result.items; total.value = result.total }
  } catch (cause) {
    if (current === requestId) { logs.value = []; total.value = 0; error.value = cause instanceof Error ? cause.message : '加载失败' }
  } finally { if (current === requestId) loading.value = false }
}

watch(() => [props.resourceType, props.resourceId], () => { page.value = 1; logs.value = []; void load() }, { immediate: true })
function changePage(value: number) { page.value = value; void load() }
</script>

<template>
  <AuditLogViewer :logs="logs" :loading="loading" :error="error" :total="total" :page="page"
    @page-change="changePage" @retry="load" />
</template>
