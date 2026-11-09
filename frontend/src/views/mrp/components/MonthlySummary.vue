<script setup lang="ts">
import RollingPlanningGrid from './RollingPlanningGrid.vue'
defineProps<{ batchId: number; ruleVersion?: string }>()
</script>
<template><RollingPlanningGrid :batch-id="batchId" mode="month" read-only /></template>
