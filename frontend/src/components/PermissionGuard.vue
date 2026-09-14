<script setup lang="ts">
import { computed } from 'vue'
import { usePermission } from '../composables/usePermission'

const props = defineProps<{
  permission: string | string[]
}>()

const { hasPermission, hasAnyPermission } = usePermission()

const allowed = computed(() => {
  if (typeof props.permission === 'string') {
    return hasPermission(props.permission)
  }
  return hasAnyPermission(props.permission)
})
</script>

<template>
  <slot v-if="allowed" />
</template>
