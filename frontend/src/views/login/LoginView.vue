<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, get, setToken } from '../../api/client'
import { usePermissionStore } from '../../stores/permission'
import { defaultPage } from '../../config/menu'

const router = useRouter()
const permissionStore = usePermissionStore()
const email = ref('')
const password = ref('')
const loading = ref(false)
const feishuLoading = ref(false)
const rememberMe = ref(false)
const showChangePassword = ref(false)
const newPassword = ref('')
const confirmPassword = ref('')
const pendingLogin = ref<any>(null)
const changingPassword = ref(false)

function finishLogin(res: any) {
  setToken(res.token)
  localStorage.setItem('mrp_user_info', JSON.stringify({
    token: res.token,
    userId: res.userId,
    username: res.username,
    displayName: res.displayName,
    roles: res.roles || [],
    permissions: res.permissions || [],
    needChangePassword: false
  }))
  permissionStore.setPermissions(res.permissions || [])
  permissionStore.setRoles(res.roles || [])
  ElMessage.success('登录成功')
  router.push(defaultPage(res.permissions || []))
}

function cancelPasswordChange() {
  showChangePassword.value = false
  pendingLogin.value = null
  newPassword.value = ''
  confirmPassword.value = ''
  setToken(null)
}

async function changePassword() {
  if (newPassword.value.length < 8 || !/[a-zA-Z]/.test(newPassword.value) || !/\d/.test(newPassword.value)) {
    ElMessage.warning('密码至少8位，且包含字母和数字')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次密码不一致')
    return
  }
  changingPassword.value = true
  try {
    const changed = await post<{ token: string }>('/auth/change-password', { password: newPassword.value })
    const login = pendingLogin.value
    login.token = changed.token
    showChangePassword.value = false
    newPassword.value = ''
    confirmPassword.value = ''
    pendingLogin.value = null
    finishLogin(login)
  } catch (e: any) {
    ElMessage.error(e.message || '修改密码失败')
  } finally {
    changingPassword.value = false
  }
}

async function handleLogin() {
  if (!email.value || !password.value) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }
  loading.value = true
  try {
    const res = await post<any>('/auth/login', {
      username: email.value,
      password: password.value
    })
    if (res && res.token) {
      setToken(res.token)
      if (res.needChangePassword) {
        pendingLogin.value = res
        showChangePassword.value = true
      } else {
        finishLogin(res)
      }
    } else {
      ElMessage.error('登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleFeishuLogin() {
  feishuLoading.value = true
  try {
    const res = await get<{ authorizeUrl: string; state: string }>('/auth/feishu/authorize')
    if (res && res.authorizeUrl) {
      // 保存state用于回调验证
      localStorage.setItem('feishu_state', res.state)
      // 跳转到飞书授权页
      window.location.href = res.authorizeUrl
    } else {
      ElMessage.error('获取飞书授权链接失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '飞书登录失败')
  } finally {
    feishuLoading.value = false
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
              v-model="email"
              placeholder="邮箱"
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

        <div class="divider-wrapper">
          <el-divider>
            <span class="divider-text">或</span>
          </el-divider>
        </div>

        <el-button
          @click="handleFeishuLogin"
          :loading="feishuLoading"
          size="large"
          class="feishu-btn"
        >
          <svg class="feishu-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M4.5 3C3.67 3 3 3.67 3 4.5V19.5C3 20.33 3.67 21 4.5 21H19.5C20.33 21 21 20.33 21 19.5V4.5C21 3.67 20.33 3 19.5 3H4.5ZM12 6.5C14.49 6.5 16.5 8.51 16.5 11V12H18V14H16.5V17.5C16.5 18.33 15.83 19 15 19H9C8.17 19 7.5 18.33 7.5 17.5V14H6V12H7.5V11C7.5 8.51 9.51 6.5 12 6.5ZM12 8.5C10.62 8.5 9.5 9.62 9.5 11V17.5H14.5V11C14.5 9.62 13.38 8.5 12 8.5Z" fill="#3370FF"/>
          </svg>
          飞书登录
        </el-button>

        <div class="form-footer">
          <span>© 2026 MRP 排产系统</span>
        </div>
      </div>
    </div>
    <el-dialog v-model="showChangePassword" title="首次登录修改密码" width="440px" :close-on-click-modal="false" @close="cancelPasswordChange">
      <el-form @submit.prevent="changePassword">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelPasswordChange">取消</el-button>
        <el-button type="primary" :loading="changingPassword" @click="changePassword">确认</el-button>
      </template>
    </el-dialog>
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

.divider-wrapper {
  margin: 0 0 20px 0;
}

.divider-wrapper :deep(.el-divider) {
  margin: 0;
}

.divider-text {
  font-size: 13px;
  color: #909399;
  padding: 0 12px;
}

.feishu-btn {
  width: 100%;
  height: 40px;
  font-size: 15px;
  border-radius: 6px;
  background: #3370FF;
  border-color: #3370FF;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.feishu-btn:hover {
  background: #2860E1;
  border-color: #2860E1;
  color: #ffffff;
}

.feishu-icon {
  width: 20px;
  height: 20px;
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
