<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post, setToken } from '../../api/client'

const router = useRouter()
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)

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
    if (res.success && res.data?.token) {
      setToken(res.data.token)
      ElMessage.success('登录成功')
      router.push('/planning')
    } else {
      ElMessage.error(res.error?.message || '登录失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div style="text-align: center">
          <h2 style="margin: 0">MRP 排产系统</h2>
          <p style="color: #999; margin: 8px 0 0; font-size: 14px">请登录</p>
        </div>
      </template>
      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="loading" size="large" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

h2 {
  color: #303133;
}
</style>
