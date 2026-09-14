<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, setToken } from '../../api/client'
import { usePermissionStore } from '../../stores/permission'

const router = useRouter()
const permissionStore = usePermissionStore()
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const rememberMe = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await post<any>('/auth/login', {
      username: username.value,
      password: password.value
    })
    if (res && res.token) {
      setToken(res.token)

      const userInfo = {
        token: res.token,
        userId: res.userId,
        username: res.username,
        displayName: res.displayName,
        roles: res.roles || [],
        permissions: res.permissions || []
      }
      localStorage.setItem('mrp_user_info', JSON.stringify(userInfo))

      permissionStore.setPermissions(res.permissions || [])
      permissionStore.setRoles(res.roles || [])

      ElMessage.success('登录成功')
      router.push('/planning')
    } else {
      ElMessage.error('登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-content">
        <img src="../../assets/logo.png" alt="Logo" class="brand-logo" />
        <h1 class="brand-title">MRP 排产系统</h1>
      </div>
    </div>
    <div class="login-form-area">
      <div class="form-wrapper">
        <div class="form-header">
          <h2 class="form-title">登录</h2>
        </div>
        <el-form class="login-form" @submit.prevent="handleLogin">
          <el-form-item>
            <el-input
              v-model="username"
              placeholder="用户名"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>
          <el-form-item class="remember-row">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="handleLogin"
              :loading="loading"
              size="large"
              class="login-btn"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="form-footer">
          <span>© 2026 MRP 排产系统</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

.login-brand {
  flex: 0 0 55%;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-content {
  text-align: center;
}

.brand-logo {
  width: 220px;
  height: auto;
  margin-bottom: 32px;
}

.brand-title {
  margin: 0;
  font-size: 28px;
  font-weight: 650;
  color: #172033;
  letter-spacing: 2px;
}

.login-form-area {
  flex: 0 0 45%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.form-wrapper {
  width: 100%;
  max-width: 380px;
}

.form-header {
  margin-bottom: 36px;
}

.form-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #172033;
}

.login-form {
  width: 100%;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 6px;
  box-shadow: 0 0 0 1px #dfe4ea inset;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c7d0dc inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #2563eb inset;
}

.login-form :deep(.el-input__inner) {
  height: 40px;
  line-height: 40px;
}

.remember-row {
  margin-bottom: 20px;
}

.remember-row :deep(.el-form-item__content) {
  justify-content: flex-start;
}

.login-btn {
  width: 100%;
  height: 40px;
  font-size: 15px;
  border-radius: 6px;
  background: #2563eb;
  border-color: #2563eb;
}

.login-btn:hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.form-footer {
  margin-top: 48px;
  text-align: center;
  font-size: 12px;
  color: #748096;
}

@media (max-width: 1023px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    flex: 1;
  }
}
</style>
