<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, setToken } from '../../api/client'
import { usePermissionStore } from '../../stores/permission'
import { defaultPage } from '../../config/menu'

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()
const loading = ref(true)
const error = ref('')

// 密码修改相关
const showChangePassword = ref(false)
const tempToken = ref('')
const tempUserInfo = ref<any>(null)
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

// 密码强度检测
const passwordStrength = computed(() => {
  const pwd = newPassword.value
  if (!pwd) return { level: '', color: '', text: '' }
  
  let score = 0
  if (pwd.length >= 8 && /\d/.test(pwd) && /[a-zA-Z]/.test(pwd)) score++
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pwd)) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (pwd.length >= 12) score++
  
  if (score <= 1) return { level: 'weak', color: '#F56C6C', text: '弱' }
  if (score <= 2) return { level: 'medium', color: '#E6A23C', text: '中' }
  return { level: 'strong', color: '#67C23A', text: '强' }
})

// 密码验证
const passwordError = computed(() => {
  const pwd = newPassword.value
  if (!pwd) return ''
  
  let count = 0
  if (pwd.length >= 8 && /\d/.test(pwd) && /[a-zA-Z]/.test(pwd)) count++
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pwd)) count++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) count++
  
  if (count < 2) return '密码需至少满足以下2项：8位+数字字母、特殊符号、大小写'
  return ''
})

const confirmPasswordError = computed(() => {
  if (!confirmPassword.value) return ''
  if (newPassword.value !== confirmPassword.value) return '两次密码不一致'
  return ''
})

const canSubmit = computed(() => {
  return newPassword.value && 
         confirmPassword.value && 
         !passwordError.value && 
         !confirmPasswordError.value &&
         newPassword.value === confirmPassword.value
})

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  const errorParam = route.query.error as string

  // 检查飞书授权是否被拒绝
  if (errorParam) {
    error.value = '飞书授权被拒绝，请重试'
    loading.value = false
    setTimeout(() => router.push('/login'), 2000)
    return
  }

  // 检查授权码
  if (!code) {
    error.value = '未收到授权码，请重试'
    loading.value = false
    setTimeout(() => router.push('/login'), 2000)
    return
  }

  // 验证state参数（防止CSRF攻击）
  const savedState = localStorage.getItem('feishu_state')
  localStorage.removeItem('feishu_state')
  if (savedState && state !== savedState) {
    error.value = '安全验证失败，请重试'
    loading.value = false
    setTimeout(() => router.push('/login'), 2000)
    return
  }

  try {
    // 调用后端飞书登录接口
    const res = await post<any>('/auth/feishu/callback', { code })

    if (res && res.token) {
      if (res.needChangePassword) {
        // 新用户需要修改密码
        tempToken.value = res.token
        tempUserInfo.value = res
        showChangePassword.value = true
        loading.value = false
      } else {
        // 老用户正常登录
        completeLogin(res)
      }
    } else {
      error.value = '登录失败，请重试'
      loading.value = false
      setTimeout(() => router.push('/login'), 2000)
    }
  } catch (e: any) {
    error.value = e.message || '飞书登录失败，请重试'
    loading.value = false
    setTimeout(() => router.push('/login'), 3000)
  }
})

function completeLogin(res: any) {
  setToken(res.token)
  
  const userInfo = {
    token: res.token,
    userId: res.userId,
    username: res.username,
    displayName: res.displayName,
    roles: res.roles || [],
    permissions: res.permissions || [],
    needChangePassword: false
  }
  localStorage.setItem('mrp_user_info', JSON.stringify(userInfo))
  
  permissionStore.setPermissions(res.permissions || [])
  permissionStore.setRoles(res.roles || [])
  
  ElMessage.success('飞书登录成功')
  router.push(defaultPage(res.permissions || []))
}

async function handleChangePassword() {
  if (!canSubmit.value) return
  
  changingPassword.value = true
  try {
    // 使用暂存的token调用修改密码接口
    const response = await fetch('/api/v1/auth/change-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${tempToken.value}`
      },
      body: JSON.stringify({ password: newPassword.value })
    })
    
    const result = await response.json()
    
    if (result.success && result.data?.token) {
      ElMessage.success('密码修改成功')
      showChangePassword.value = false
      tempToken.value = result.data.token
      
      // 重新获取用户信息（因为needChangePassword已更新）
      const userInfo = {
        token: tempToken.value,
        userId: tempUserInfo.value.userId,
        username: tempUserInfo.value.username,
        displayName: tempUserInfo.value.displayName,
        roles: tempUserInfo.value.roles || [],
        permissions: tempUserInfo.value.permissions || [],
        needChangePassword: false
      }
      setToken(tempToken.value)
      localStorage.setItem('mrp_user_info', JSON.stringify(userInfo))
      
      permissionStore.setPermissions(tempUserInfo.value.permissions || [])
      permissionStore.setRoles(tempUserInfo.value.roles || [])
      
      ElMessage.info('请联系管理员分配权限后使用系统')
      router.push(defaultPage(tempUserInfo.value.permissions || []))
    } else {
      ElMessage.error(result.error?.message || '修改密码失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '修改密码失败')
  } finally {
    changingPassword.value = false
  }
}

function handleCancel() {
  showChangePassword.value = false
  tempToken.value = ''
  tempUserInfo.value = null
  router.push('/login')
}
</script>

<template>
  <div class="callback-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="callback-card">
      <div class="loading-state">
        <el-icon class="loading-icon" :size="48">
          <Loading />
        </el-icon>
        <h2 class="title">飞书登录中...</h2>
        <p class="desc">正在完成飞书登录，请稍候</p>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="callback-card">
      <div class="error-state">
        <el-icon class="error-icon" :size="48" color="#F56C6C">
          <CircleClose />
        </el-icon>
        <h2 class="title">登录失败</h2>
        <p class="desc">{{ error }}</p>
        <p class="redirect-hint">即将跳转到登录页面...</p>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="showChangePassword"
      title="首次登录 - 请修改密码"
      :width="480"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      :center="true"
    >
      <div class="password-dialog-content">
        <div class="warning-box">
          <el-icon :size="20" color="#E6A23C"><Warning /></el-icon>
          <div class="warning-text">
            <p>欢迎使用MRP系统！首次登录请修改默认密码。</p>
            <p class="sub-hint">修改密码后，请联系管理员分配权限。</p>
          </div>
        </div>

        <el-form :model="{ newPassword, confirmPassword }" label-position="top" class="password-form">
          <el-form-item label="新密码" :error="passwordError">
            <el-input
              v-model="newPassword"
              type="password"
              placeholder="请输入新密码"
              show-password
              size="large"
            />
            <div v-if="newPassword" class="password-strength">
              <span class="strength-label">密码强度：</span>
              <span :style="{ color: passwordStrength.color }">{{ passwordStrength.text }}</span>
              <div class="strength-bar">
                <div 
                  class="strength-fill" 
                  :style="{ 
                    width: passwordStrength.level === 'weak' ? '33%' : 
                           passwordStrength.level === 'medium' ? '66%' : '100%',
                    backgroundColor: passwordStrength.color 
                  }"
                />
              </div>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" :error="confirmPasswordError">
            <el-input
              v-model="confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
              size="large"
            />
          </el-form-item>
        </el-form>

        <div class="password-rules">
          <p class="rules-title">密码规则：</p>
          <ul>
            <li>长度至少8位，包含数字和字母</li>
            <li>包含特殊符号（如 !@#$%^&*）</li>
            <li>同时包含大小写字母</li>
            <li>至少满足以上2项</li>
          </ul>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCancel" size="large">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleChangePassword" 
            :loading="changingPassword"
            :disabled="!canSubmit"
            size="large"
          >
            确认修改
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.callback-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.callback-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 48px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  max-width: 400px;
  width: 90%;
}

.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-icon {
  color: #3370FF;
  animation: spin 1s linear infinite;
}

.error-icon {
  color: #F56C6C;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1F2329;
}

.desc {
  margin: 0;
  font-size: 14px;
  color: #646A73;
}

.redirect-hint {
  margin: 0;
  font-size: 13px;
  color: #8F959E;
}

/* 密码弹窗样式 */
.password-dialog-content {
  padding: 0 8px;
}

.warning-box {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #FFF7E6;
  border-radius: 8px;
  margin-bottom: 24px;
}

.warning-text {
  flex: 1;
}

.warning-text p {
  margin: 0;
  font-size: 14px;
  color: #1F2329;
  line-height: 1.5;
}

.warning-text .sub-hint {
  margin-top: 4px;
  color: #8F959E;
  font-size: 13px;
}

.password-form {
  margin-bottom: 16px;
}

.password-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}

.strength-label {
  color: #8F959E;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: #E5E6EB;
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.password-rules {
  background: #F7F8FA;
  border-radius: 8px;
  padding: 16px;
}

.rules-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 500;
  color: #1F2329;
}

.password-rules ul {
  margin: 0;
  padding-left: 20px;
}

.password-rules li {
  font-size: 13px;
  color: #646A73;
  line-height: 1.8;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

:deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  margin: 0;
  border-bottom: 1px solid #E5E6EB;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1F2329;
}

:deep(.el-dialog__body) {
  padding: 24px;
}

:deep(.el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid #E5E6EB;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #1F2329;
}
</style>
