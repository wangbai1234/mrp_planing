<script setup lang="ts">
import { useRouter } from 'vue-router'
import { setToken } from '../../api/client'
import { usePermissionStore } from '../../stores/permission'
import { useBreadcrumbStore } from '../../stores/breadcrumb'

const router = useRouter()
const permissionStore = usePermissionStore()
const breadcrumbStore = useBreadcrumbStore()

function handleLogout() {
  setToken(null)
  localStorage.removeItem('mrp_user_info')
  permissionStore.clear()
  breadcrumbStore.clear()
  router.push('/login')
}
</script>

<template>
  <div class="waiting-page">
    <div class="waiting-card">
      <div class="icon-wrapper">
        <el-icon :size="64" color="#1677FF"><Lock /></el-icon>
      </div>
      <h1 class="title">等待权限分配</h1>
      <p class="desc">请联系管理员分配权限后使用系统</p>
      <el-button type="primary" size="large" @click="handleLogout" class="logout-btn">
        退出登录
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.waiting-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.waiting-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 60px 48px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  max-width: 420px;
  width: 90%;
}

.icon-wrapper {
  margin-bottom: 24px;
}

.title {
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 600;
  color: #1F2329;
}

.desc {
  margin: 0 0 36px;
  font-size: 15px;
  color: #646A73;
  line-height: 1.6;
}

.logout-btn {
  min-width: 160px;
}
</style>
