import { ref } from 'vue'
import { qmsApi } from '@/api/qms'
import type { MenuAccess } from '@/types/qms'

export type NavigationMenuDefinition = {
  code: string
  label: string
  parentCode?: string
  path?: string
}

export const navigationMenuDefinitions: NavigationMenuDefinition[] = [
  { code: 'OVERVIEW', label: '总览' },
  { code: 'DASHBOARD', label: '质量总览', parentCode: 'OVERVIEW', path: '/dashboard' },
  { code: 'QUALITY', label: '质量检测' },
  { code: 'WORK_ORDERS', label: '生产指令查询', parentCode: 'QUALITY', path: '/work-orders' },
  { code: 'PROCESS_INSPECTION', label: '工序检验', parentCode: 'QUALITY', path: '/process-inspection' },
  { code: 'INSPECTION_WORKBENCH', label: '成品测试', parentCode: 'QUALITY', path: '/inspection' },
  { code: 'NONCONFORMITIES', label: '不良品处理', parentCode: 'QUALITY', path: '/nonconformities' },
  { code: 'TRACE', label: '报表' },
  { code: 'ANALYTICS', label: '报表分析', parentCode: 'TRACE', path: '/analytics' },
  { code: 'EQUIPMENT_FINISHED_TEST_SUMMARY', label: '成品测试汇总表', parentCode: 'TRACE', path: '/equipment/finished-test-summary' },
  { code: 'EQUIPMENT', label: '设备管理' },
  { code: 'EQUIPMENT_LEDGER', label: '设备台账', parentCode: 'EQUIPMENT', path: '/equipment' },
  { code: 'EQUIPMENT_INSPECTION', label: '点检管理', parentCode: 'EQUIPMENT', path: '/equipment/inspection' },
  { code: 'EQUIPMENT_MAINTENANCE', label: '保养管理', parentCode: 'EQUIPMENT', path: '/equipment/maintenance' },
  { code: 'EQUIPMENT_REPAIR', label: '维修管理', parentCode: 'EQUIPMENT', path: '/equipment/repair' },
  { code: 'EQUIPMENT_ALERTS', label: '设备告警', parentCode: 'EQUIPMENT', path: '/equipment/alerts' },
  { code: 'EQUIPMENT_TEMPLATES', label: '项目模板', parentCode: 'EQUIPMENT', path: '/equipment/templates' },
  { code: 'EQUIPMENT_GATEWAY_MAPPINGS', label: '设备网关映射', parentCode: 'EQUIPMENT', path: '/equipment/gateway-mappings' },
  { code: 'EQUIPMENT_RUNTIME_MONITOR', label: '设备数据接入', parentCode: 'EQUIPMENT', path: '/equipment/runtime-monitor' },
  { code: 'SYSTEM', label: '系统管理' },
  { code: 'SYSTEM_USERS', label: '用户管理', parentCode: 'SYSTEM', path: '/system/users' },
  { code: 'SYSTEM_ROLES', label: '角色权限', parentCode: 'SYSTEM', path: '/system/roles' },
  { code: 'SYSTEM_MENU_MANAGEMENT', label: '菜单管理', parentCode: 'SYSTEM', path: '/system/menus' },
  { code: 'SYSTEM_ORGANIZATIONS', label: '组织机构', parentCode: 'SYSTEM', path: '/system/organizations' },
  { code: 'SYSTEM_DEPARTMENTS', label: '部门设置', parentCode: 'SYSTEM', path: '/system/departments' },
  { code: 'SYSTEM_PROCESSES', label: '工序设置', parentCode: 'SYSTEM', path: '/system/processes' },
  { code: 'SYSTEM_SCRAP_REASONS', label: '报废原因设置', parentCode: 'SYSTEM', path: '/system/scrap-reasons' },
  { code: 'SYSTEM_DICTIONARIES', label: '字典管理', parentCode: 'SYSTEM', path: '/system/dictionaries' },
  { code: 'SYSTEM_PARAMETERS', label: '系统参数', parentCode: 'SYSTEM', path: '/system-parameters' },
  { code: 'SYSTEM_INTERFACES', label: '接口配置', parentCode: 'SYSTEM', path: '/system/interfaces' },
  { code: 'SYSTEM_APPROVAL_FLOW', label: '审批流', parentCode: 'SYSTEM', path: '/system/approval-flow' },
  { code: 'SYSTEM_OPERATION_LOG', label: '操作日志', parentCode: 'SYSTEM', path: '/system/operation-log' },
  { code: 'SYSTEM_TENANTS', label: '租户管理', parentCode: 'SYSTEM', path: '/system/tenants' }
]

const definitionByCode = new Map(navigationMenuDefinitions.map((definition) => [definition.code, definition]))
export const menuAccess = ref<MenuAccess>()
let loadingMenuAccess: Promise<MenuAccess | undefined> | undefined

export function menuGroups() {
  return navigationMenuDefinitions.filter((definition) => !definition.parentCode)
}

export function menuPages(parentCode: string) {
  return navigationMenuDefinitions.filter((definition) => definition.parentCode === parentCode)
}

export function isMenuVisible(menuCode: string, access = menuAccess.value) {
  const definition = definitionByCode.get(menuCode)
  if (!definition || !definition.parentCode || !access) return Boolean(definition)
  if (menuCode === 'SYSTEM_TENANTS' && access.roleCode !== 'PLATFORM_ADMIN') return false
  const activeMenuCodes = new Set(access.activeMenuCodes)
  const permitted = access.permittedMenuCodes.includes(menuCode)
    || (menuCode === 'EQUIPMENT_FINISHED_TEST_SUMMARY' && access.permittedMenuCodes.includes('INSPECTION_WORKBENCH'))
  return activeMenuCodes.has(menuCode)
    && activeMenuCodes.has(definition.parentCode)
    && permitted
}

export function isMenuGroupVisible(menuCode: string, access = menuAccess.value) {
  if (!access) return definitionByCode.has(menuCode)
  return access.activeMenuCodes.includes(menuCode) && menuPages(menuCode).some((item) => isMenuVisible(item.code, access))
}

export function firstVisibleMenuPath(access = menuAccess.value) {
  return navigationMenuDefinitions.find((definition) => definition.path && isMenuVisible(definition.code, access))?.path
}

export async function loadMenuAccess(force = false) {
  if (!force && menuAccess.value) return menuAccess.value
  if (!force && loadingMenuAccess) return loadingMenuAccess
  const request = qmsApi.getMenuAccess()
    .then((access) => {
      menuAccess.value = access
      return access
    })
    .catch(() => undefined)
    .finally(() => {
      if (loadingMenuAccess === request) loadingMenuAccess = undefined
    })
  loadingMenuAccess = request
  return request
}

export async function refreshMenuAccess() {
  return loadMenuAccess(true)
}
