<script setup lang="ts">
import RollingPlanningGrid from './RollingPlanningGrid.vue'
defineProps<{ batchId: number; ruleVersion?: string }>()
</script>
<template><RollingPlanningGrid :batch-id="batchId" mode="week" read-only /></template>
