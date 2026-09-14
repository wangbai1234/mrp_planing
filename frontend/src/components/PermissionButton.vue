<script setup lang="ts">
import { computed } from 'vue'
import { usePermission } from '../composables/usePermission'

const props = defineProps<{
  permission: string | string[]
}>()

const { hasPermission, hasAnyPermission } = usePermission()

const visible = computed(() => {
  if (typeof props.permission === 'string') {
    return hasPermission(props.permission)
  }
  return hasAnyPermission(props.permission)
})
</script>

<template>
  <el-button v-if="visible" v-bind="$attrs">
    <slot />
  </el-button>
</template>
