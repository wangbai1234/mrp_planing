<script setup lang="ts">
import RollingPlanningGrid from './RollingPlanningGrid.vue'
withDefaults(defineProps<{ batchId: number; readOnly?: boolean; ruleVersion?: string }>(), { readOnly: false })
defineEmits<{ changed: [] }>()
</script>
<template><RollingPlanningGrid :batch-id="batchId" :read-only="readOnly || !['v4-rolling-business-weeks', 'v5-shifted-business-weeks'].includes(ruleVersion || '')" mode="day" @changed="$emit('changed')" /></template>
