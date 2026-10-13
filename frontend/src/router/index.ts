import { createRouter, createWebHistory } from 'vue-router'
import { localSession } from '@/auth/local-session'
import { firstVisibleMenuPath, loadMenuAccess } from '@/navigation/menu'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '质量总览' } },
    { path: '/work-orders', component: () => import('@/views/WorkOrdersView.vue'), meta: { title: '生产指令查询' } },
    { path: '/process-inspection', component: () => import('@/views/ProcessInspectionView.vue'), meta: { title: '工序检验' } },
    { path: '/inspection', component: () => import('@/views/InspectionWorkbenchView.vue'), meta: { title: '成品测试实时监控工作台' } },
    { path: '/inspection-configuration', component: () => import('@/views/InspectionConfigurationView.vue'), meta: { title: '检验标准配置' } },
    { path: '/inspection-templates', component: () => import('@/views/InspectionConfigurationView.vue'), meta: { title: '检验标准配置', configTab: 'templates' } },
    { path: '/inspection-rules', component: () => import('@/views/InspectionConfigurationView.vue'), meta: { title: '自动派单规则', configTab: 'rules' } },
    { path: '/nonconformities', component: () => import('@/views/NonconformityView.vue'), meta: { title: '不良品与 CAPA' } },
    { path: '/equipment', component: () => import('@/views/EquipmentView.vue'), meta: { title: '设备台账', equipmentSection: 'ledger' } },
    { path: '/equipment/inspection', component: () => import('@/views/EquipmentView.vue'), meta: { title: '点检管理', equipmentSection: 'maintenance', maintenanceType: 'INSPECTION' } },
    { path: '/equipment/maintenance', component: () => import('@/views/EquipmentView.vue'), meta: { title: '保养管理', equipmentSection: 'maintenance', maintenanceType: 'MAINTENANCE' } },
    { path: '/equipment/repair', component: () => import('@/views/EquipmentView.vue'), meta: { title: '维修管理', equipmentSection: 'maintenance', maintenanceType: 'REPAIR' } },
    { path: '/equipment/alerts', component: () => import('@/views/EquipmentView.vue'), meta: { title: '设备告警', equipmentSection: 'alerts' } },
    { path: '/equipment/types', component: () => import('@/views/AdministrationView.vue'), meta: { title: '设备类型', adminType: 'EQUIPMENT_TYPE' } },
    { path: '/equipment/templates', component: () => import('@/views/EquipmentMaintenanceTemplateView.vue'), meta: { title: '维护项目与标准值' } },
    { path: '/equipment/gateway-mappings', component: () => import('@/views/EquipmentGatewayMappingView.vue'), meta: { title: '设备网关映射' } },
    { path: '/equipment/runtime-monitor', component: () => import('@/views/EquipmentRuntimeMonitorView.vue'), meta: { title: '设备数据接入' } },
    { path: '/reports', component: () => import('@/views/ReportsView.vue'), meta: { title: '报表分析', reportSection: 'analytics' } },
    { path: '/traceability', component: () => import('@/views/ReportsView.vue'), meta: { title: '质量追溯', reportSection: 'traceability' } },
    { path: '/analytics', component: () => import('@/views/ReportsView.vue'), meta: { title: '报表分析', reportSection: 'analytics' } },
    { path: '/system/users', component: () => import('@/views/InspectionConfigurationView.vue'), meta: { title: '用户管理', configTab: 'users' } },
    { path: '/system/processes', component: () => import('@/views/InspectionConfigurationView.vue'), meta: { title: '工序与产品资料', configTab: 'master-data' } },
    { path: '/system/roles', component: () => import('@/views/AdministrationView.vue'), meta: { title: '角色权限', adminType: 'ROLE' } },
    { path: '/system/menus', component: () => import('@/views/MenuManagementView.vue'), meta: { title: '菜单管理' } },
    { path: '/system/tenants', component: () => import('@/views/TenantManagementView.vue'), meta: { title: '租户管理', platformOnly: true } },
    { path: '/system/organizations', component: () => import('@/views/AdministrationView.vue'), meta: { title: '组织机构', adminType: 'ORGANIZATION' } },
    { path: '/system/departments', component: () => import('@/views/AdministrationView.vue'), meta: { title: '部门设置', adminType: 'DEPARTMENT' } },
    { path: '/system/scrap-reasons', component: () => import('@/views/AdministrationView.vue'), meta: { title: '报废原因设置', adminType: 'SCRAP_REASON' } },
    { path: '/system/dictionaries', component: () => import('@/views/AdministrationView.vue'), meta: { title: '字典管理', adminType: 'DICTIONARY' } },
    { path: '/system-parameters', component: () => import('@/views/SystemParametersView.vue'), meta: { title: '系统参数' } },
    { path: '/system/interfaces', component: () => import('@/views/AdministrationView.vue'), meta: { title: '接口配置', adminType: 'INTERFACE' } },
    { path: '/system/approval-flow', component: () => import('@/views/AdministrationView.vue'), meta: { title: '审批流', adminType: 'APPROVAL_FLOW' } },
    { path: '/system/operation-log', component: () => import('@/views/AdministrationView.vue'), meta: { title: '操作日志', adminType: 'AUDIT_LOG' } }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!localSession()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.platformOnly) {
    // Local accounts can be switched without reloading the module graph. Always reload
    // access here so a prior platform administrator's cached menu cannot grant a stale route.
    const access = await loadMenuAccess(true)
    if (access?.roleCode !== 'PLATFORM_ADMIN') {
      return { path: firstVisibleMenuPath(access) ?? '/dashboard' }
    }
  }
  return true
})

// An open tab can still hold the previous release's router after deployment.
// Its lazy-loaded view URL no longer exists, so reload the requested route
// once to pick up the new index and module graph without losing the login.
const chunkRecoveryKey = 'qms:chunk-recovery-path'
router.onError((error, to) => {
  if (!/failed to fetch dynamically imported module|importing a module script failed|error loading dynamically imported module/i.test(String(error))) return
  try {
    if (sessionStorage.getItem(chunkRecoveryKey) === to.fullPath) return
    sessionStorage.setItem(chunkRecoveryKey, to.fullPath)
  } catch {
    // Private browsing can disable session storage; the browser can still reload.
  }
  window.location.replace(to.fullPath)
})

router.afterEach((_to, _from, failure) => {
  if (!failure) {
    try { sessionStorage.removeItem(chunkRecoveryKey) } catch { /* Storage may be disabled. */ }
  }
})

export default router
