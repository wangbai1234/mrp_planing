<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const activeMenu = ref(route.path)

watch(() => route.path, (path) => {
  activeMenu.value = path
})

const menuItems = [
  { path: '/admin/users', label: '用户', icon: 'User' },
  { path: '/admin/roles', label: '角色', icon: 'UserFilled' },
  { path: '/admin/audit-logs', label: '操作日志', icon: 'Document' }
]

function handleMenuSelect(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="admin-layout">
    <div class="admin-sidebar">
      <div class="sidebar-title">用户与权限</div>
      <el-menu
        :default-active="activeMenu"
        @select="handleMenuSelect"
        background-color="#f7f8fa"
        text-color="#1f2329"
        active-text-color="#1677ff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </div>
    <div class="admin-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: calc(100vh - 48px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.admin-sidebar {
  width: 200px;
  border-right: 1px solid #e8e9eb;
  background: #f7f8fa;
  flex-shrink: 0;
}

.sidebar-title {
  height: 52px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  border-bottom: 1px solid #e8e9eb;
}

.admin-content {
  flex: 1;
  overflow: auto;
  padding: 20px;
}

:deep(.el-menu) {
  border-right: none;
  padding: 8px;
}

:deep(.el-menu-item) {
  height: 36px;
  line-height: 36px;
  margin: 2px 0;
  border-radius: 6px;
}

:deep(.el-menu-item.is-active) {
  background: #e8f0fe !important;
  color: #1677ff !important;
}

:deep(.el-menu-item:hover) {
  background: #eff0f1 !important;
}
</style>
