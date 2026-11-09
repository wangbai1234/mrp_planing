<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBreadcrumbStore } from '../stores/breadcrumb'

const route = useRoute()
const router = useRouter()
const breadcrumbStore = useBreadcrumbStore()

// Keep the trail on one line: show at most the 4 most recent pages and fold the rest.
const VISIBLE_COUNT = 4

const items = computed(() => {
  const history = breadcrumbStore.history
  const hiddenCount = Math.max(0, history.length - VISIBLE_COUNT)
  return history.slice(hiddenCount).map(entry => ({
    ...entry,
    isCurrent: entry.path === route.path
  }))
})

const hiddenItems = computed(() => {
  const history = breadcrumbStore.history
  const hiddenCount = Math.max(0, history.length - VISIBLE_COUNT)
  return history.slice(0, hiddenCount)
})

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="app-breadcrumb" v-if="items.length > 0">
    <el-dropdown v-if="hiddenItems.length > 0" trigger="click" @command="navigate">
      <span class="breadcrumb-link breadcrumb-more">…</span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="item in hiddenItems" :key="item.path" :command="item.path">
            {{ item.label }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <span v-if="hiddenItems.length > 0" class="breadcrumb-separator">/</span>
    <template v-for="(item, idx) in items" :key="item.path">
      <span v-if="idx > 0" class="breadcrumb-separator">/</span>
      <span
        v-if="item.isCurrent"
        class="breadcrumb-current"
      >{{ item.label }}</span>
      <span
        v-else
        class="breadcrumb-link"
        @click="navigate(item.path)"
      >{{ item.label }}</span>
    </template>
  </div>
</template>

<style scoped>
.app-breadcrumb {
  display: flex;
  align-items: center;
  font-size: 13px;
  line-height: 48px;
}
.breadcrumb-current {
  color: #1f1f1f;
  font-weight: 600;
  font-size: 14px;
}
.breadcrumb-link {
  color: #8c8c8c;
  cursor: pointer;
  font-size: 13px;
  transition: color 0.2s;
}
.breadcrumb-link:hover {
  color: #262626;
}
.breadcrumb-more {
  font-size: 16px;
  letter-spacing: 1px;
  outline: none;
}
.breadcrumb-separator {
  color: #d9d9d9;
  margin: 0 4px;
  font-size: 12px;
}
</style>
