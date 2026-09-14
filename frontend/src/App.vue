<script setup lang="ts">
import { ref, provide, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { setToken } from './api/client'
import { usePermissionStore } from './stores/permission'

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()

const crumbGroup = ref('计划管理')
const crumbPage = ref('12 周滚动排产')

provide('setCrumb', (group: string, page: string) => {
  crumbGroup.value = group
  crumbPage.value = page
})

function navigateTo(path: string) {
  router.push(path)
}

function handleLogout() {
  setToken(null)
  localStorage.removeItem('mrp_user_info')
  permissionStore.clear()
  router.push('/login')
}

// Get user info
const userInfo = ref<any>(null)

function loadUserInfo() {
  const info = localStorage.getItem('mrp_user_info')
  if (info) {
    userInfo.value = JSON.parse(info)
  }
}

onMounted(loadUserInfo)

// Reload user info when route changes (e.g., after login)
watch(() => route.path, () => {
  loadUserInfo()
})

// Dynamic menu based on permissions
const menuItems = computed(() => {
  const items = []

  // 计划 group
  if (permissionStore.hasAnyPermission(['schedule:view'])) {
    items.push({
      group: '计划',
      items: [
        { path: '/dashboard', label: '看板', icon: 'DataBoard', permission: 'schedule:view' },
        { path: '/planning', label: '排产计划', icon: 'Calendar', permission: 'schedule:view' }
      ].filter(item => !item.permission || permissionStore.hasPermission(item.permission))
    })
  }

  // 数据 group
  const dataItems = []
  if (permissionStore.hasPermission('forecast:view')) {
    dataItems.push({ path: '/forecast', label: '经营计划', icon: 'Document' })
  }
  if (permissionStore.hasPermission('inventory:view')) {
    dataItems.push({ path: '/inventory', label: '库存快照', icon: 'Box' })
  }
  dataItems.push({ path: '/material', label: '物料管理', icon: 'Files' })
  dataItems.push({ path: '/bom', label: 'BOM管理', icon: 'Share' })
  if (dataItems.length > 0) {
    items.push({ group: '数据', items: dataItems })
  }

  // 配置 group
  const configItems = []
  if (permissionStore.hasPermission('capacity:view')) {
    configItems.push({ path: '/capacity', label: '产能配置', icon: 'Setting' })
  }
  if (configItems.length > 0) {
    items.push({ group: '配置', items: configItems })
  }

  // 系统管理 group
  if (permissionStore.hasAnyPermission(['user:view', 'role:view', 'audit:view'])) {
    items.push({
      group: '系统',
      items: [
        { path: '/admin/users', label: '用户与权限', icon: 'UserFilled' }
      ]
    })
  }

  return items
})
</script>

<template>
  <el-container style="height: 100vh" v-if="route.name !== 'login'">
    <el-aside width="184px" class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">MRP</div>
        <span class="brand-text">计划协同</span>
      </div>
      <el-menu
        :default-active="route.path"
        background-color="#172238"
        text-color="#c9d2e2"
        active-text-color="#ffffff"
        @select="navigateTo"
        :collapse="false"
      >
        <template v-for="group in menuItems" :key="group.group">
          <div class="menu-group-label">{{ group.group }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-topbar" height="48px">
        <div class="crumb">
          <span>{{ crumbGroup }}</span>
          <span style="margin: 0 4px">/</span>
          <strong>{{ crumbPage }}</strong>
        </div>
        <div class="top-meta">
          <span><span class="sync-dot"></span>数据已同步</span>
          <el-dropdown @command="handleLogout" trigger="click">
            <span class="user-info">
              <el-avatar :size="28" style="background: #385077; font-size: 12px">
                {{ userInfo?.displayName?.[0] || 'U' }}
              </el-avatar>
              <span style="font-size: 12px; margin-left: 6px">{{ userInfo?.displayName || userInfo?.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main style="background: #f3f5f8; padding: 14px 16px; overflow: auto">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
  <RouterView v-else />
</template>

<style scoped>
.app-sidebar {
  background: #172238;
  overflow-y: auto;
  border-right: none;
}
.brand {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255,255,255,.09);
}
.brand-mark {
  width: 28px;
  height: 28px;
  border: 1px solid #5c75a4;
  display: grid;
  place-items: center;
  border-radius: 5px;
  color: #fff;
  font-weight: 700;
  font-size: 11px;
}
.brand-text {
  font-size: 14px;
  color: #fff;
  font-weight: 600;
}
.menu-group-label {
  padding: 12px 20px 4px;
  color: #8291a8;
  font-size: 11px;
  letter-spacing: .08em;
}
.app-topbar {
  background: #fff;
  border-bottom: 1px solid #dfe4ea;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #748096;
  font-size: 13px;
}
.crumb strong {
  color: #172033;
  font-weight: 600;
}
.top-meta {
  display: flex;
  gap: 14px;
  align-items: center;
  color: #4b5870;
  font-size: 12px;
}
.sync-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #17803d;
  display: inline-block;
  margin-right: 5px;
}
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 1px solid #dfe4ea;
}
:deep(.el-menu) {
  border-right: none;
}
:deep(.el-menu-item) {
  height: 34px;
  line-height: 34px;
  margin: 2px 8px;
  border-radius: 5px;
}
:deep(.el-menu-item.is-active) {
  background: #2b5fc3 !important;
}
:deep(.el-menu-item:hover) {
  background: rgba(255,255,255,.06) !important;
}
</style>
