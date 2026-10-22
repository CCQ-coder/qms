<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { navigationMenuDefinitions } from '@/navigation/menu'
import type { TenantManagement } from '@/types/qms'
import { formatDateTime } from '@/utils/display-text'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingTenant = ref<TenantManagement>()
const tenants = ref<TenantManagement[]>([])
const pinningTenantCode = ref<string>()
const deletingTenantCode = ref<string>()

const pageMenus = computed(() => navigationMenuDefinitions.filter((item) => item.parentCode && item.code !== 'SYSTEM_TENANTS'))
const menuGroups = computed(() => navigationMenuDefinitions
  .filter((item) => !item.parentCode)
  .map((group) => ({ ...group, items: pageMenus.value.filter((item) => item.parentCode === group.code) }))
  .filter((group) => group.items.length > 0))
const allMenuCodes = computed(() => pageMenus.value.map((item) => item.code))
const tenantForm = reactive({
  code: '', name: '', status: 'ACTIVE', adminUserName: '', adminDisplayName: '', adminPassword: '', menuCodes: [] as string[]
})

function resetForm(tenant?: TenantManagement) {
  editingTenant.value = tenant
  Object.assign(tenantForm, {
    code: tenant?.code ?? '',
    name: tenant?.name ?? '',
    status: tenant?.status ?? 'ACTIVE',
    adminUserName: tenant?.adminUserName ?? '',
    adminDisplayName: tenant?.adminDisplayName ?? '',
    adminPassword: '',
    menuCodes: tenant ? tenant.activeMenuCodes.filter((code) => allMenuCodes.value.includes(code)) : [...allMenuCodes.value]
  })
}

async function load() {
  loading.value = true
  try {
    tenants.value = await qmsApi.listTenants()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '租户列表读取失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(tenant: TenantManagement) {
  resetForm(tenant)
  dialogVisible.value = true
}

function selectAllMenus() {
  tenantForm.menuCodes = [...allMenuCodes.value]
}

function clearMenus() {
  tenantForm.menuCodes = []
}

async function toggleLoginPinned(tenant: TenantManagement, value: string | number | boolean) {
  pinningTenantCode.value = tenant.code
  try {
    await qmsApi.updateTenantLoginPinned(tenant.code, value === true)
    await load()
    ElMessage.success(value === true ? `“${tenant.name}”已置顶到登录页` : `已取消“${tenant.name}”的登录页置顶`)
  } catch (error) {
    await load()
    ElMessage.error(error instanceof Error ? error.message : '登录页置顶设置失败')
  } finally {
    pinningTenantCode.value = undefined
  }
}

async function removeTenant(tenant: TenantManagement) {
  try {
    await ElMessageBox.confirm(
      `确认删除租户“${tenant.name}”（${tenant.code}）吗？仅无业务数据的租户可以删除；删除后其管理员账号和菜单配置将一并移除。`,
      '删除租户',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }

  deletingTenantCode.value = tenant.code
  try {
    await qmsApi.deleteTenant(tenant.code)
    ElMessage.success(`租户“${tenant.name}”已删除`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '租户删除失败')
  } finally {
    deletingTenantCode.value = undefined
  }
}

async function save() {
  if (!tenantForm.code.trim() || !tenantForm.name.trim() || !tenantForm.adminUserName.trim()) {
    ElMessage.warning('请填写组织编码、组织名称和管理员账号')
    return
  }
  if (!editingTenant.value && tenantForm.adminPassword.length < 10) {
    ElMessage.warning('初始管理员密码至少需要 10 位')
    return
  }
  if (editingTenant.value && tenantForm.adminPassword && tenantForm.adminPassword.length < 10) {
    ElMessage.warning('重置密码至少需要 10 位')
    return
  }
  if (!tenantForm.menuCodes.length) {
    ElMessage.warning('请至少选择一个可用菜单')
    return
  }
  saving.value = true
  const payload = {
    code: tenantForm.code,
    name: tenantForm.name,
    status: tenantForm.status,
    adminUserName: tenantForm.adminUserName,
    adminDisplayName: tenantForm.adminDisplayName,
    adminPassword: tenantForm.adminPassword || undefined,
    menuCodes: tenantForm.menuCodes
  }
  try {
    if (editingTenant.value) {
      await qmsApi.updateTenant(editingTenant.value.code, payload)
      ElMessage.success('租户已更新')
    } else {
      await qmsApi.createTenant(payload)
      ElMessage.success('租户已创建，初始管理员可以登录')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '租户保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <div class="hero-card compact-hero">
      <div>
        <p class="eyebrow">平台管理</p>
        <h2>租户管理</h2>
        <p>维护可登录的组织、初始租户管理员和该组织可使用的业务菜单。停用组织后，该组织不能再登录或访问系统。</p>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <span>租户列表</span>
          <div><el-button @click="load">刷新</el-button><el-button type="primary" @click="openCreate">新增租户</el-button></div>
        </div>
      </template>
      <paged-table :data="tenants" empty-text="暂无租户">
        <el-table-column prop="name" label="组织名称" min-width="180" />
        <el-table-column prop="code" label="组织编码" min-width="160" show-overflow-tooltip />
        <el-table-column label="初始管理员" min-width="190"><template #default="scope">{{ scope.row.adminDisplayName || scope.row.adminUserName || '—' }}<span v-if="scope.row.adminDisplayName && scope.row.adminUserName" class="secondary-text">（{{ scope.row.adminUserName }}）</span></template></el-table-column>
        <el-table-column label="可用菜单" min-width="260" show-overflow-tooltip><template #default="scope">{{ scope.row.activeMenuCodes.length }} 项</template></el-table-column>
        <el-table-column label="登录页置顶" width="128"><template #default="scope"><el-switch :model-value="scope.row.loginPinned" :loading="pinningTenantCode === scope.row.code" @change="toggleLoginPinned(scope.row, $event)" /></template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" min-width="175"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="148" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button link type="danger" :loading="deletingTenantCode === scope.row.code" @click="removeTenant(scope.row)">删除</el-button></template></el-table-column>
      </paged-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingTenant ? '编辑租户' : '新增租户'" width="min(860px, 94vw)" destroy-on-close>
      <el-form :model="tenantForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="组织编码" required><el-input v-model="tenantForm.code" :disabled="Boolean(editingTenant)" placeholder="例如：PCB-FACTORY-A" /></el-form-item>
          <el-form-item label="组织名称" required><el-input v-model="tenantForm.name" placeholder="例如：PCB 一厂" /></el-form-item>
          <el-form-item label="初始管理员账号" required><el-input v-model="tenantForm.adminUserName" :disabled="Boolean(editingTenant)" autocomplete="username" /></el-form-item>
          <el-form-item label="管理员显示名称"><el-input v-model="tenantForm.adminDisplayName" /></el-form-item>
          <el-form-item :label="editingTenant ? '重置管理员密码（留空不修改）' : '初始管理员密码'" :required="!editingTenant" class="form-item-wide"><el-input v-model="tenantForm.adminPassword" type="password" show-password autocomplete="new-password" placeholder="至少 10 位" /></el-form-item>
          <el-form-item label="组织状态"><el-radio-group v-model="tenantForm.status"><el-radio value="ACTIVE">启用</el-radio><el-radio value="INACTIVE">停用</el-radio></el-radio-group></el-form-item>
        </div>
        <el-form-item label="可用菜单" required>
          <div class="tenant-menu-selector">
            <div class="menu-toolbar"><el-button link type="primary" @click="selectAllMenus">全选</el-button><el-button link @click="clearMenus">清空</el-button></div>
            <div v-for="group in menuGroups" :key="group.code" class="tenant-menu-group">
              <strong>{{ group.label }}</strong>
              <el-checkbox-group v-model="tenantForm.menuCodes" class="tenant-menu-options">
                <el-checkbox v-for="item in group.items" :key="item.code" :value="item.code">{{ item.label }}</el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
          <div class="form-hint">租户只能看到此处启用的页面；“租户管理”仅属于平台管理员，不会授权给普通租户。</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.secondary-text,
.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tenant-menu-selector {
  display: grid;
  gap: 12px;
  width: 100%;
}

.menu-toolbar {
  display: flex;
  gap: 14px;
}

.tenant-menu-group {
  display: grid;
  gap: 9px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.tenant-menu-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
}

.form-hint {
  margin-top: 8px;
  line-height: 1.6;
}
</style>
