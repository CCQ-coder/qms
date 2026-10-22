<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { menuGroups, menuPages, navigationMenuDefinitions, refreshMenuAccess } from '@/navigation/menu'
import { formatDateTime } from '@/utils/display-text'
import type { AdministrationAuditLog, AdministrationCatalogType, AdministrationItem, UserAccess } from '@/types/qms'

type AttributeField = { key: string; label: string; placeholder?: string; multiline?: boolean }
type CatalogConfiguration = {
  type: AdministrationCatalogType
  title: string
  eyebrow: string
  description: string
  noun: string
  parentLabel?: string
  parentPlaceholder?: string
  attributes: AttributeField[]
}

const configurations: Record<string, CatalogConfiguration> = {
  ROLE: {
    type: 'ROLE', title: '角色权限', eyebrow: '系统管理', noun: '角色',
    description: '维护系统角色的数据范围和可见菜单；标准角色编码可与用户管理中的角色对应。',
    attributes: [{ key: 'dataScope', label: '数据范围', placeholder: '例如：全部数据 / 已分配工序' }]
  },
  ORGANIZATION: {
    type: 'ORGANIZATION', title: '组织机构', eyebrow: '系统管理', noun: '组织机构',
    description: '维护工厂、事业部和班组等组织层级，为部门归属和责任分配提供基础。',
    parentLabel: '上级组织编码', parentPlaceholder: '留空表示顶级组织',
    attributes: [
      { key: 'organizationType', label: '组织类型', placeholder: '例如：工厂 / 车间 / 班组' },
      { key: 'responsiblePerson', label: '负责人', placeholder: '负责人姓名或账号' }
    ]
  },
  DEPARTMENT: {
    type: 'DEPARTMENT', title: '部门设置', eyebrow: '系统管理', noun: '部门',
    description: '维护质量、生产、设备等部门资料，可关联所属组织及负责人。',
    parentLabel: '所属组织编码', parentPlaceholder: '例如：PCB-FACTORY',
    attributes: [{ key: 'manager', label: '部门负责人', placeholder: '负责人姓名或账号' }]
  },
  SCRAP_REASON: {
    type: 'SCRAP_REASON', title: '报废原因设置', eyebrow: '系统管理', noun: '报废原因',
    description: '维护不良品处置、报废分析使用的原因编码和分类。',
    attributes: [{ key: 'category', label: '原因分类', placeholder: '例如：电气不良 / 外观不良' }]
  },
  DICTIONARY: {
    type: 'DICTIONARY', title: '字典管理', eyebrow: '系统管理', noun: '字典',
    description: '集中维护业务字典及其可选值，便于质量、设备与报表模块统一引用。',
    attributes: [
      { key: 'dataType', label: '字典类型', placeholder: '例如：业务字典 / 基础字典' },
      { key: 'values', label: '字典值', placeholder: '多个值以逗号分隔', multiline: true }
    ]
  },
  INTERFACE: {
    type: 'INTERFACE', title: '接口配置', eyebrow: '系统管理', noun: '接口',
    description: '维护 ERP、设备网关等集成端点及同步方式；敏感凭据不在页面中保存。',
    attributes: [
      { key: 'endpoint', label: '接口地址', placeholder: '例如：https://erp.example.com/api' },
      { key: 'authType', label: '认证方式', placeholder: '例如：API Key / OAuth2' },
      { key: 'syncMode', label: '同步方式', placeholder: '例如：实时 / 定时' }
    ]
  },
  APPROVAL_FLOW: {
    type: 'APPROVAL_FLOW', title: '审批流', eyebrow: '系统管理', noun: '审批流',
    description: '维护不良品处置等业务的触发条件与审批角色，供业务流程配置使用。',
    attributes: [
      { key: 'businessType', label: '适用业务', placeholder: '例如：不良品处置' },
      { key: 'approverRole', label: '审批角色编码', placeholder: '例如：SUPERVISOR' },
      { key: 'trigger', label: '触发条件', placeholder: '例如：提交处置' }
    ]
  },
  EQUIPMENT_TYPE: {
    type: 'EQUIPMENT_TYPE', title: '设备类型', eyebrow: '设备管理', noun: '设备类型',
    description: '维护设备台账可选择的设备类型。停用的类型不会出现在新增设备的选项中。',
    attributes: []
  },
  EQUIPMENT_TEMPLATE: {
    type: 'EQUIPMENT_TEMPLATE', title: '设备项目模板', eyebrow: '设备管理', noun: '项目模板',
    description: '维护点检、保养和维修项目模板，可按设备类型和周期建立标准作业项目。',
    attributes: [
      { key: 'maintenanceType', label: '维护类型', placeholder: 'INSPECTION / MAINTENANCE / REPAIR' },
      { key: 'equipmentType', label: '适用设备类型', placeholder: '例如：成品电测设备' },
      { key: 'cycle', label: '执行周期', placeholder: '例如：每日 / 每周 / 每月' },
      { key: 'items', label: '项目内容', placeholder: '多个项目以逗号分隔', multiline: true }
    ]
  }
}

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingItem = ref<AdministrationItem>()
const items = ref<AdministrationItem[]>([])
const menuItems = ref<AdministrationItem[]>([])
const auditLogs = ref<AdministrationAuditLog[]>([])
const auditUsers = ref<UserAccess[]>([])
const auditModule = ref('')
const roleMenuCodes = ref<string[]>([])
const form = reactive({
  itemCode: '', itemName: '', parentCode: '', description: '', status: 'ACTIVE', sortOrder: 100,
  attributes: {} as Record<string, string>
})

const selectedType = computed(() => String(route.meta.adminType ?? 'ROLE'))
const configuration = computed(() => configurations[selectedType.value] ?? configurations.ROLE)
const isAuditLog = computed(() => selectedType.value === 'AUDIT_LOG')
const isRole = computed(() => selectedType.value === 'ROLE')
const menuItemByCode = computed(() => new Map(menuItems.value.map((item) => [item.itemCode, item])))
const roleMenuGroups = computed(() => menuGroups().map((group) => ({
  ...group,
  items: menuPages(group.code)
})))

function parseMenuCodes(attributes: Record<string, string> | undefined) {
  if (attributes?.menuCodesConfigured !== 'true') {
    return navigationMenuDefinitions.filter((item) => item.parentCode).map((item) => item.code)
  }
  return (attributes.menuCodes ?? '').split(',').map((code) => code.trim()).filter(Boolean)
}

function visibleMenuSummary(attributes: Record<string, string>) {
  if (attributes.menuCodesConfigured !== 'true') return '全部菜单（未单独设置）'
  const labels = parseMenuCodes(attributes)
    .map((code) => navigationMenuDefinitions.find((item) => item.code === code)?.label ?? code)
  return labels.length ? labels.join('、') : '未分配菜单'
}

function auditModuleText(value?: string) {
  return ({
    SYSTEM: '系统管理',
    ROLE: '角色权限',
    MENU: '菜单管理',
    ORGANIZATION: '组织机构',
    DEPARTMENT: '部门设置',
    SCRAP_REASON: '报废原因设置',
    DICTIONARY: '字典管理',
    INTERFACE: '接口配置',
    APPROVAL_FLOW: '审批流',
    EQUIPMENT_TYPE: '设备类型',
    EQUIPMENT_TEMPLATE: '设备项目模板',
  } as Record<string, string>)[value ?? ''] ?? '其他模块'
}

function auditOperationText(value?: string) {
  return ({
    INITIALIZE: '初始化',
    CREATE: '新建',
    UPDATE: '修改',
    DELETE: '删除',
  } as Record<string, string>)[value ?? ''] ?? '其他操作'
}

function auditActorText(actor?: string) {
  if (!actor || actor === 'system') return '系统'
  const user = auditUsers.value.find((item) => item.userName === actor)
  return user?.displayName?.trim() || '未知用户'
}

function resetForm(item?: AdministrationItem) {
  editingItem.value = item
  Object.assign(form, {
    itemCode: item?.itemCode ?? '',
    itemName: item?.itemName ?? '',
    parentCode: item?.parentCode ?? '',
    description: item?.description ?? '',
    status: item?.status ?? 'ACTIVE',
    sortOrder: item?.sortOrder ?? 100,
    attributes: { ...(item?.attributes ?? {}) }
  })
  roleMenuCodes.value = isRole.value ? parseMenuCodes(item?.attributes) : []
}

async function load() {
  loading.value = true
  try {
    if (isAuditLog.value) {
      const [logs, users] = await Promise.all([
        qmsApi.listAdministrationAuditLogs(auditModule.value || undefined),
        qmsApi.listUserAccesses(),
      ])
      auditLogs.value = logs
      auditUsers.value = users
    } else {
      const [configurationItems, availableMenus] = await Promise.all([
        qmsApi.listAdministrationItems(configuration.value.type),
        isRole.value ? qmsApi.listAdministrationItems('MENU') : Promise.resolve([] as AdministrationItem[])
      ])
      items.value = configurationItems
      menuItems.value = availableMenus
    }
  } catch {
    ElMessage.error('管理配置读取失败，请确认当前账号为租户管理员')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(item: AdministrationItem) {
  resetForm(item)
  dialogVisible.value = true
}

async function saveItem() {
  if (!form.itemCode.trim() || !form.itemName.trim()) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  saving.value = true
  const payload = {
    itemCode: form.itemCode,
    itemName: form.itemName,
    parentCode: form.parentCode || undefined,
    description: form.description || undefined,
    status: form.status,
    sortOrder: form.sortOrder,
    attributes: {
      ...form.attributes,
      ...(isRole.value ? {
        menuCodes: roleMenuCodes.value.join(','),
        menuCodesConfigured: 'true'
      } : {})
    }
  }
  try {
    if (editingItem.value) {
      await qmsApi.updateAdministrationItem(configuration.value.type, editingItem.value.id, payload)
      ElMessage.success(`${configuration.value.noun}已更新`)
    } else {
      await qmsApi.createAdministrationItem(configuration.value.type, payload)
      ElMessage.success(`${configuration.value.noun}已创建`)
    }
    dialogVisible.value = false
    await load()
    if (isRole.value) await refreshMenuAccess()
  } catch {
    ElMessage.error(`${configuration.value.noun}保存失败`)
  } finally {
    saving.value = false
  }
}

async function changeStatus(item: AdministrationItem, active: boolean) {
  try {
    await qmsApi.updateAdministrationItem(configuration.value.type, item.id, {
      itemCode: item.itemCode,
      itemName: item.itemName,
      parentCode: item.parentCode,
      description: item.description,
      status: active ? 'ACTIVE' : 'INACTIVE',
      sortOrder: item.sortOrder,
      attributes: item.attributes
    })
    item.status = active ? 'ACTIVE' : 'INACTIVE'
    ElMessage.success(active ? '已启用' : '已停用')
  } catch {
    ElMessage.error('状态更新失败')
  }
}

watch(() => route.fullPath, () => {
  auditModule.value = ''
  items.value = []
  auditLogs.value = []
  auditUsers.value = []
  menuItems.value = []
  void load()
})
onMounted(load)
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <template v-if="isAuditLog">
      <div class="hero-card compact-hero">
        <div><p class="eyebrow">系统管理</p><h2>操作日志</h2><p>记录管理配置的创建、修改和初始化操作，按模块筛选查看。</p></div>
      </div>
      <el-card shadow="never" class="content-card">
        <div class="toolbar"><el-select v-model="auditModule" clearable placeholder="全部模块" style="width: 220px"><el-option v-for="item in Object.values(configurations)" :key="item.type" :label="item.title" :value="item.type" /></el-select><el-button type="primary" @click="load">查询</el-button></div>
        <paged-table :key="auditModule" :data="auditLogs" empty-text="暂无操作日志"><el-table-column label="操作时间" min-width="185"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column><el-table-column label="模块" min-width="145"><template #default="scope">{{ auditModuleText(scope.row.moduleCode) }}</template></el-table-column><el-table-column label="操作" min-width="100"><template #default="scope">{{ auditOperationText(scope.row.operation) }}</template></el-table-column><el-table-column label="操作人" min-width="140"><template #default="scope">{{ auditActorText(scope.row.actor) }}</template></el-table-column><el-table-column prop="summary" label="操作说明" min-width="260" show-overflow-tooltip /></paged-table>
      </el-card>
    </template>

    <template v-else>
      <div class="hero-card compact-hero">
        <div><p class="eyebrow">{{ configuration.eyebrow }}</p><h2>{{ configuration.title }}</h2><p>{{ configuration.description }}</p></div>
      </div>
      <el-card shadow="never" class="content-card">
        <template #header><div class="card-header"><span>{{ configuration.title }}列表</span><div><el-button @click="load">刷新</el-button><el-button type="primary" @click="openCreate">新建{{ configuration.noun }}</el-button></div></div></template>
        <paged-table :key="selectedType" :data="items" empty-text="暂无配置数据">
          <el-table-column prop="itemCode" label="编码" min-width="155" show-overflow-tooltip />
          <el-table-column prop="itemName" label="名称" min-width="170" show-overflow-tooltip />
          <el-table-column v-if="configuration.parentLabel" prop="parentCode" :label="configuration.parentLabel" min-width="145" show-overflow-tooltip />
          <el-table-column v-for="field in configuration.attributes" :key="field.key" :label="field.label" min-width="150" show-overflow-tooltip><template #default="scope">{{ scope.row.attributes[field.key] || '—' }}</template></el-table-column>
          <el-table-column v-if="isRole" label="可见菜单" min-width="280" show-overflow-tooltip><template #default="scope">{{ visibleMenuSummary(scope.row.attributes) }}</template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="210" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="86" />
          <el-table-column label="状态" width="124" fixed="right"><template #default="scope"><div class="table-status-control"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag><el-switch :model-value="scope.row.status === 'ACTIVE'" @change="changeStatus(scope.row, Boolean($event))" /></div></template></el-table-column>
          <el-table-column label="操作" width="88" fixed="right"><template #default="scope"><el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
        </paged-table>
      </el-card>

      <el-dialog v-model="dialogVisible" :title="`${editingItem ? '编辑' : '新建'}${configuration.noun}`" width="min(720px, 94vw)">
        <el-form :model="form" label-position="top">
          <div class="form-grid">
            <el-form-item label="编码" required><el-input v-model="form.itemCode" :disabled="Boolean(editingItem)" placeholder="建议使用大写英文、数字和连字符" /></el-form-item>
            <el-form-item label="名称" required><el-input v-model="form.itemName" /></el-form-item>
            <el-form-item v-if="configuration.parentLabel" :label="configuration.parentLabel"><el-input v-model="form.parentCode" :placeholder="configuration.parentPlaceholder" /></el-form-item>
            <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" class="full-width" /></el-form-item>
            <el-form-item v-for="field in configuration.attributes" :key="field.key" :label="field.label" :class="{ 'form-item-wide': field.multiline }"><el-input v-model="form.attributes[field.key]" :type="field.multiline ? 'textarea' : 'text'" :rows="field.multiline ? 3 : 1" :placeholder="field.placeholder" /></el-form-item>
          </div>
          <el-form-item v-if="isRole" label="可见菜单">
            <div class="role-menu-selector">
              <div v-for="group in roleMenuGroups" :key="group.code" class="role-menu-group">
                <div class="role-menu-group-title">{{ group.label }}</div>
                <el-checkbox-group v-model="roleMenuCodes" class="role-menu-options">
                  <el-checkbox v-for="item in group.items" :key="item.code" :value="item.code">
                    {{ item.label }}<span v-if="menuItemByCode.get(item.code)?.status === 'INACTIVE'" class="menu-hidden-note">（当前全局隐藏）</span>
                  </el-checkbox>
                </el-checkbox-group>
              </div>
            </div>
            <div class="form-hint">菜单管理控制全局显示；此处仅选择该角色可见的页面。两项都启用后，页面才会显示在导航中。</div>
          </el-form-item>
          <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="补充使用说明、适用范围或注意事项" /></el-form-item>
          <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="INACTIVE">停用</el-radio></el-radio-group></el-form-item>
        </el-form>
        <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveItem">保存</el-button></template>
      </el-dialog>
    </template>
  </section>
</template>

<style scoped>
.role-menu-selector {
  display: grid;
  gap: 12px;
  width: 100%;
}

.role-menu-group {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.role-menu-group-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.role-menu-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
}

.menu-hidden-note,
.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-hint {
  margin-top: 8px;
  line-height: 1.6;
}
</style>
