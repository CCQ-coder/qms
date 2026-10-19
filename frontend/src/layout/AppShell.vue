<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  DocumentChecked,
  Operation,
  Setting,
  SetUp
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authenticatedSubject, isOidcEnabled, logout, startLogin } from '@/auth/oidc'
import { localSession, signOutLocal } from '@/auth/local-session'
import { isMenuGroupVisible, isMenuVisible, menuAccess, refreshMenuAccess } from '@/navigation/menu'
import {
  desktopApiBaseUrl,
  isDesktopApplication,
  normalizeDesktopApiBaseUrl,
  saveDesktopConnection
} from '@/desktop/connection'

const route = useRoute()
const router = useRouter()
const asideCollapsed = ref(false)
const oidcEnabled = isOidcEnabled()
const oidcSubject = ref(authenticatedSubject())
const currentLocalSession = ref(localSession())
const signingIn = ref(false)
const desktopApplication = isDesktopApplication()
const desktopConnectionDialogVisible = ref(false)
const desktopApiUrl = ref(desktopApiBaseUrl())
const savingDesktopConnection = ref(false)
const actorLabel = computed(() => currentLocalSession.value?.displayName ?? 'QMS 账号')
const platformAdministrator = computed(() => menuAccess.value?.roleCode === 'PLATFORM_ADMIN'
  || (!menuAccess.value && currentLocalSession.value?.roleCode === 'PLATFORM_ADMIN'))
const tenantAdministrator = computed(() => platformAdministrator.value || menuAccess.value?.roleCode === 'TENANT_ADMIN'
  || (!menuAccess.value && (oidcEnabled || currentLocalSession.value?.roleCode === 'TENANT_ADMIN')))

const pageTitle = computed(() => route.meta.title ?? 'PCB QMS')

async function signIn() {
  signingIn.value = true
  try {
    await startLogin()
  } catch (error) {
    signingIn.value = false
    window.alert(error instanceof Error ? error.message : '无法开始 OIDC 登录')
  }
}

async function signOut() {
  try {
    await logout()
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '退出登录失败')
  }
}

async function signOutLocalSession() {
  signOutLocal()
  currentLocalSession.value = undefined
  await router.replace('/login')
}

function openDesktopConnectionDialog() {
  desktopApiUrl.value = desktopApiBaseUrl()
  desktopConnectionDialogVisible.value = true
}

async function saveDesktopServerAddress() {
  try {
    savingDesktopConnection.value = true
    await saveDesktopConnection(normalizeDesktopApiBaseUrl(desktopApiUrl.value))
    ElMessage.success('服务地址已保存，正在重新连接 QMS。')
    window.setTimeout(() => window.location.reload(), 500)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '服务地址保存失败')
  } finally {
    savingDesktopConnection.value = false
  }
}

onMounted(() => {
  // AppShell is remounted after a local account switch. Always reload instead of
  // reusing the previous account's in-memory menu permissions.
  void refreshMenuAccess()
})
</script>

<template>
  <el-container class="app-shell">
    <el-aside :width="asideCollapsed ? '72px' : '244px'" class="app-aside">
      <div class="brand">
        <div class="brand-mark">Q</div>
        <span v-show="!asideCollapsed">PCB QMS</span>
      </div>
      <el-menu :collapse="asideCollapsed" :default-active="route.path" :default-openeds="['overview', 'quality', 'trace', 'equipment', 'system']" router class="main-menu grouped-menu">
        <el-sub-menu v-if="isMenuGroupVisible('OVERVIEW')" index="overview">
          <template #title><el-icon><DataAnalysis /></el-icon><span>总览</span></template>
          <el-menu-item v-if="isMenuVisible('DASHBOARD')" index="/dashboard"><span>质量总览</span></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="isMenuGroupVisible('QUALITY')" index="quality">
          <template #title><el-icon><DocumentChecked /></el-icon><span>质量检测</span></template>
          <el-menu-item v-if="isMenuVisible('WORK_ORDERS')" index="/work-orders"><span>生产指令查询</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('PROCESS_INSPECTION')" index="/process-inspection"><span>工序检验</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('INSPECTION_WORKBENCH')" index="/inspection"><span>成品测试</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('INSPECTION_TEMPLATES')" index="/inspection-templates"><span>检验标准配置</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('INSPECTION_RULES')" index="/inspection-rules"><span>自动派单规则</span></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="isMenuGroupVisible('TRACE')" index="trace">
          <template #title><el-icon><Operation /></el-icon><span>追溯与分析</span></template>
          <el-menu-item v-if="isMenuVisible('NONCONFORMITIES')" index="/nonconformities"><span>不良品处理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('TRACEABILITY')" index="/traceability"><span>质量追溯</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('ANALYTICS')" index="/analytics"><span>报表分析</span></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="isMenuGroupVisible('EQUIPMENT')" index="equipment">
          <template #title><el-icon><SetUp /></el-icon><span>设备管理</span></template>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_LEDGER')" index="/equipment"><span>设备台账</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_INSPECTION')" index="/equipment/inspection"><span>点检管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_MAINTENANCE')" index="/equipment/maintenance"><span>保养管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_REPAIR')" index="/equipment/repair"><span>维修管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_ALERTS')" index="/equipment/alerts"><span>设备告警</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_TEMPLATES')" index="/equipment/templates"><span>项目模板</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_GATEWAY_MAPPINGS')" index="/equipment/gateway-mappings"><span>设备网关映射</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('EQUIPMENT_RUNTIME_MONITOR')" index="/equipment/runtime-monitor"><span>设备数据接入</span></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="tenantAdministrator && isMenuGroupVisible('SYSTEM')" index="system">
          <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
          <el-menu-item v-if="isMenuVisible('SYSTEM_USERS')" index="/system/users"><span>用户管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_ROLES')" index="/system/roles"><span>角色权限</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_MENU_MANAGEMENT')" index="/system/menus"><span>菜单管理</span></el-menu-item>
          <el-menu-item v-if="platformAdministrator && isMenuVisible('SYSTEM_TENANTS')" index="/system/tenants"><span>租户管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_ORGANIZATIONS')" index="/system/organizations"><span>组织机构</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_DEPARTMENTS')" index="/system/departments"><span>部门设置</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_PROCESSES')" index="/system/processes"><span>工序设置</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_SCRAP_REASONS')" index="/system/scrap-reasons"><span>报废原因设置</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_DICTIONARIES')" index="/system/dictionaries"><span>字典管理</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_PARAMETERS')" index="/system-parameters"><span>系统参数</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_INTERFACES')" index="/system/interfaces"><span>接口配置</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_APPROVAL_FLOW')" index="/system/approval-flow"><span>审批流</span></el-menu-item>
          <el-menu-item v-if="isMenuVisible('SYSTEM_OPERATION_LOG')" index="/system/operation-log"><span>操作日志</span></el-menu-item>
        </el-sub-menu>
      </el-menu>
      <button class="collapse-button" @click="asideCollapsed = !asideCollapsed">{{ asideCollapsed ? '›' : '‹ 收起菜单' }}</button>
    </el-aside>

    <el-container class="app-content-shell">
      <el-header class="app-header">
        <div>
          <div class="breadcrumb">PCB 质量管理系统 / {{ pageTitle }}</div>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="header-actions">
          <el-button v-if="desktopApplication" plain @click="openDesktopConnectionDialog">服务地址</el-button>
          <template v-if="oidcEnabled">
            <el-button v-if="!oidcSubject" type="primary" :loading="signingIn" @click="signIn">OIDC 登录</el-button>
            <template v-else>
              <div class="user-chip">{{ oidcSubject }}</div>
              <el-button link type="primary" @click="signOut">退出登录</el-button>
            </template>
          </template>
          <template v-else>
            <div class="user-chip">{{ actorLabel }}</div>
            <el-button link type="primary" @click="signOutLocalSession">退出登录</el-button>
          </template>
        </div>
      </el-header>
      <el-main class="app-main"><router-view /></el-main>
    </el-container>

    <el-dialog v-model="desktopConnectionDialogVisible" title="QMS 服务器地址" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon>
        桌面端只保存服务器地址，所有质量数据仍存放在企业 QMS 服务端。
      </el-alert>
      <el-form label-position="top" style="margin-top: 18px">
        <el-form-item label="后端 API 地址">
          <el-input v-model="desktopApiUrl" placeholder="例如：http://192.168.1.20:8080" autocomplete="url" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="desktopConnectionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDesktopConnection" @click="saveDesktopServerAddress">保存并重连</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>
