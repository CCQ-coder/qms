<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { LoginTenant } from '@/types/qms'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { isOidcEnabled, startLogin } from '@/auth/oidc'
import { signInWithPassword } from '@/auth/local-session'
import { qmsApi } from '@/api/qms'

const router = useRouter()
const route = useRoute()
const tenantCode = ref('')
const userName = ref('')
const password = ref('')
const loading = ref(false)
const tenantLoading = ref(false)
const tenants = ref<LoginTenant[]>([])
const oidcEnabled = isOidcEnabled()

async function loadTenants() {
  if (oidcEnabled) return
  tenantLoading.value = true
  try {
    tenants.value = await qmsApi.listLoginTenants()
    if (!tenantCode.value || !tenants.value.some((tenant) => tenant.code === tenantCode.value)) {
      tenantCode.value = tenants.value[0]?.code ?? ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法加载可选组织')
  } finally {
    tenantLoading.value = false
  }
}

onMounted(loadTenants)

async function signIn() {
  loading.value = true
  try {
    if (oidcEnabled) {
      await startLogin()
      return
    }
    const session = await qmsApi.loginWithPassword({ tenantCode: tenantCode.value, userName: userName.value, password: password.value })
    signInWithPassword(session)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-intro">
        <div class="login-brand-mark">Q</div>
        <p class="eyebrow">PCB QUALITY MANAGEMENT SYSTEM</p>
        <h1>欢迎使用 PCB QMS</h1>
        <p>登录后依据账号角色查看相应的质量检测、追溯分析、设备与系统管理功能。</p>
        <div class="login-feature-list"><span>成品测试实时监控</span><span>不良品闭环与质量追溯</span><span>设备维护与系统配置</span></div>
      </div>

      <div class="login-form-wrap">
        <div><p class="login-kicker">账号登录</p><h2>{{ oidcEnabled ? '企业统一身份认证' : 'QMS 账号登录' }}</h2><p>{{ oidcEnabled ? '将跳转至企业身份提供方完成登录。' : '使用管理员创建并授权的 QMS 账号登录。' }}</p></div>
        <el-form v-if="!oidcEnabled" label-position="top" @submit.prevent="signIn">
          <el-form-item label="所属组织">
            <el-select v-model="tenantCode" :loading="tenantLoading" placeholder="请选择组织" style="width: 100%">
              <el-option v-for="tenant in tenants" :key="tenant.code" :label="tenant.name" :value="tenant.code" />
            </el-select>
          </el-form-item>
          <el-form-item label="用户名"><el-input v-model="userName" autocomplete="username" placeholder="请输入用户名" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="signIn" /></el-form-item>
          <el-button class="login-submit" type="primary" :loading="loading" @click="signIn">登录系统</el-button>
        </el-form>
        <el-button v-else class="login-submit" type="primary" :loading="loading" @click="signIn">使用企业账号登录</el-button>
      </div>
    </section>
  </main>
</template>
