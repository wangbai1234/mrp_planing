<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePermissionStore } from '../../stores/permission'

const router = useRouter()
const permissionStore = usePermissionStore()

const entries = computed(() => [
  { title: '库存控制策略', description: '按仓库和物料维护安全库存、预警库存；安全库存作为可用库存硬保底，预警库存用于风险提示。', path: '/mrp/stock-policies', permission: 'mrp_stock_policy:view' },
  { title: '业务周期间', description: '按月维护 W1-Wn 日期范围，允许跨自然月和 W5，周汇总只从日排产聚合。', path: '/mrp/business-weeks', permission: 'mrp_business_week:view' },
  { title: '物料策略', description: '维护自制、外购、委外，以及 EXPLODE / STOP 下钻策略。', path: '/mrp/material-policies', permission: 'mrp_config:view' },
  { title: '生产日历', description: '按已维护工厂设置生产日和整机日产能（台/日），用于整机产出预警。', path: '/mrp/production-calendars', permission: 'mrp_calendar:view' }
].filter(item => permissionStore.hasPermission(item.permission)))
</script>

<template>
  <div class="page-header"><h1 class="page-title">基础规则</h1></div>
  <el-alert title="修改配置立即生效；已创建的排产仍使用创建时锁定的配置。" type="info" :closable="false" class="config-alert" />
  <div v-if="entries.length" class="config-grid">
    <el-card v-for="entry in entries" :key="entry.path" shadow="hover" class="config-card" @click="router.push(entry.path)">
      <div class="config-title">{{ entry.title }}</div>
      <div class="config-description">{{ entry.description }}</div>
      <el-button type="primary" link @click.stop="router.push(entry.path)">进入维护</el-button>
    </el-card>
  </div>
  <el-empty v-else description="当前角色未分配基础规则查看权限" />
</template>

<style scoped>
.page-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}.page-title{margin:0;font-size:20px;color:#1F2329}.config-alert{margin-bottom:16px}.config-grid{display:grid;grid-template-columns:repeat(3,minmax(240px,1fr));gap:16px}.config-card{cursor:pointer}.config-title{font-size:16px;font-weight:600;color:#1F2329}.config-description{min-height:60px;margin:10px 0 14px;color:#4E5969;line-height:1.7}@media(max-width:1100px){.config-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.config-grid{grid-template-columns:1fr}}
</style>
