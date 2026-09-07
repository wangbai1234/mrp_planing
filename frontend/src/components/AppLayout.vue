<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/planning', label: '排产管理', icon: 'Grid' },
  { path: '/forecast', label: '经营计划', icon: 'Document' },
  { path: '/inventory', label: '库存快照', icon: 'Box' },
  { path: '/capacity', label: '产能配置', icon: 'Setting' },
  { path: '/material', label: '物料管理', icon: 'Goods' },
  { path: '/bom', label: 'BOM管理', icon: 'Connection' },
  { path: '/versions', label: '版本记录', icon: 'List' },
  { path: '/export', label: '导出', icon: 'Download' }
]

const activeMenu = ref(route.path)

function handleMenuSelect(path: string) {
  activeMenu.value = path
  router.push(path)
}
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background-color: #001529">
      <div style="height: 48px; display: flex; align-items: center; justify-content: center; color: white; font-size: 16px; font-weight: bold">
        MRP 排产系统
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="height: 48px; display: flex; align-items: center; border-bottom: 1px solid #e8e8e8; background: white">
        <span style="font-size: 14px; color: #666">{{ route.name === 'planning' ? '滚动排产' : menuItems.find(m => m.path === route.path)?.label }}</span>
      </el-header>
      <el-main style="background: #f5f5f5; padding: 16px">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>
