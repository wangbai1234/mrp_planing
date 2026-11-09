<script setup lang="ts">
import { useRouter } from 'vue-router'
import { usePermissionStore } from '../../stores/permission'
import { defaultPage } from '../../config/menu'

const router = useRouter()
const permissions = usePermissionStore()
</script>

<template>
  <div class="forbidden-card">
    <el-result icon="warning" title="暂无访问权限" sub-title="当前账号未分配此页面所需权限，请联系系统管理员在角色权限中配置。">
      <template #extra>
        <el-button type="primary" @click="router.push(defaultPage(permissions.permissions))">返回工作台</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden-card { max-width: 720px; margin: 80px auto; background: #fff; border-radius: 8px; padding: 28px; }
</style>
