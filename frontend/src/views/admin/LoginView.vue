<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 管理端登录页。
 *
 * 不套 AdminLayout：未登录时侧边栏点了也没用，反而显得乱。
 * 登录成功后优先跳回 query.redirect（用户被守卫拦下来之前的地址），
 * 没有就进管理端首页。
 */

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/**
 * 提交登录。
 *
 * 先跑表单校验，再发请求。
 * 请求失败时不做任何提示——用户名密码错误这类业务异常
 * 已经在 request 拦截器里统一弹过了，这里再弹一次会重复。
 */
async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')

    // redirect 可能被用户手动改成站外地址，这里只接受站内路径
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/admin'
    await router.replace(target)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <h1>管理端登录</h1>
        <p>登录后可管理展示页上的工具</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <div class="login-foot">
        <router-link to="/">← 返回展示页</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8eef8 100%);
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 36px 32px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06);
}

.login-head {
  margin-bottom: 24px;
  text-align: center;
}

.login-head h1 {
  margin: 0 0 6px;
  font-size: 20px;
}

.login-head p {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.login-btn {
  width: 100%;
}

.login-foot {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
}

.login-foot a {
  color: var(--app-text-secondary);
}

.login-foot a:hover {
  color: var(--app-primary);
}

/*
 * 手机适配。
 * align-items 从 center 改成 stretch 并把卡片上移到 12vh 附近，
 * 原因是软键盘弹起时可视高度变小，居中的卡片会被顶到屏幕外，
 * 用户点输入框的瞬间整个表单就"跳"走了。
 */
@media (max-width: 767px) {
  .login-page {
    align-items: flex-start;
    padding: 12vh 16px 24px;
  }

  .login-card {
    padding: 28px 20px 20px;
    border-radius: 10px;
  }

  .login-head h1 {
    font-size: 18px;
  }
}
</style>
