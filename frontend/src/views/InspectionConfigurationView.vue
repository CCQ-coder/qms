<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { menuAccess } from '@/navigation/menu'
import { statusText, taskTypeText } from '@/utils/display-text'
import type { InspectionRule, InspectionTemplate, ProcessMaster, ProductMaster, UserAccess } from '@/types/qms'

type EditableItem = {
  sortOrder: number
  itemCode: string
  itemName: string
  inputType: 'TEXT' | 'NUMBER' | 'SELECT' | 'BOOLEAN'
  required: boolean
  lowerLimit?: number
  upperLimit?: number
  unit: string
  optionsText: string
  helpText: string
}

const activeTab = ref('templates')
const route = useRoute()
const loading = ref(false)
const templates = ref<InspectionTemplate[]>([])
const rules = ref<InspectionRule[]>([])
const products = ref<ProductMaster[]>([])
const processes = ref<ProcessMaster[]>([])
const users = ref<UserAccess[]>([])
const templateDialogVisible = ref(false)
const ruleDialogVisible = ref(false)
const productDialogVisible = ref(false)
const processDialogVisible = ref(false)
const userDialogVisible = ref(false)
const savingTemplate = ref(false)
const savingRule = ref(false)
const savingProduct = ref(false)
const savingProcess = ref(false)
const savingUser = ref(false)
const platformAdministrator = computed(() => menuAccess.value?.roleCode === 'PLATFORM_ADMIN')

const newItem = (sortOrder: number): EditableItem => ({
  sortOrder, itemCode: '', itemName: '', inputType: 'TEXT', required: true,
  lowerLimit: undefined, upperLimit: undefined, unit: '', optionsText: '', helpText: ''
})

const templateForm = reactive({
  templateCode: '', name: '', productCode: '', processCode: '', processName: '', taskType: 'PROCESS',
  items: [newItem(10)] as EditableItem[]
})
const ruleForm = reactive({
  name: '', productCode: '', processCode: '', processName: '', taskType: 'PROCESS', templateId: '', enabled: true, priority: 100
})
const productForm = reactive({ productCode: '', productName: '', specification: '' })
const processForm = reactive({ processCode: '', processName: '', sequenceNo: 100 })
const userForm = reactive({ id: '', userName: '', displayName: '', roleCode: 'INSPECTOR', status: 'ACTIVE', password: '', processCodes: [] as string[] })

async function load() {
  loading.value = true
  try {
    const [templateData, ruleData, productData, processData] = await Promise.all([
      qmsApi.listInspectionTemplates(), qmsApi.listInspectionRules(), qmsApi.listProducts(), qmsApi.listProcesses()
    ])
    templates.value = templateData
    rules.value = ruleData
    products.value = productData
    processes.value = processData
  } catch {
    ElMessage.error('检验标准配置读取失败')
  } finally {
    loading.value = false
  }
}

function openTemplateDialog() {
  Object.assign(templateForm, { templateCode: '', name: '', productCode: '', processCode: '', processName: '', taskType: 'PROCESS' })
  templateForm.items.splice(0, templateForm.items.length, newItem(10))
  templateDialogVisible.value = true
}

function addItem() {
  templateForm.items.push(newItem((templateForm.items.length + 1) * 10))
}

function removeItem(index: number) {
  if (templateForm.items.length === 1) {
    ElMessage.warning('模板至少需要一个检验项')
    return
  }
  templateForm.items.splice(index, 1)
}

async function saveTemplate() {
  if (!templateForm.templateCode.trim() || !templateForm.name.trim() || !templateForm.processCode.trim() || !templateForm.processName.trim()) {
    ElMessage.warning('请填写模板编码、模板名称和对应工序')
    return
  }
  const invalidItem = templateForm.items.find((item) => !item.itemCode.trim() || !item.itemName.trim()
    || (item.inputType === 'SELECT' && !item.optionsText.trim())
    || (item.lowerLimit !== undefined && item.upperLimit !== undefined && item.lowerLimit > item.upperLimit))
  if (invalidItem) {
    ElMessage.warning('请完善检验项；下拉项需填写选项，且下限不能大于上限')
    return
  }
  savingTemplate.value = true
  try {
    await qmsApi.createInspectionTemplate({
      ...templateForm,
      items: templateForm.items.map((item) => ({
        ...item,
        options: item.optionsText.split(/[，,]/).map((value) => value.trim()).filter(Boolean)
      }))
    })
    ElMessage.success('检验模板已创建，可在派单规则中选用')
    templateDialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('检验模板创建失败')
  } finally {
    savingTemplate.value = false
  }
}

async function changeTemplateStatus(row: InspectionTemplate, active: boolean) {
  try {
    await qmsApi.updateInspectionTemplateStatus(row.id, active ? 'ACTIVE' : 'INACTIVE')
    row.status = active ? 'ACTIVE' : 'INACTIVE'
    ElMessage.success(active ? '模板已启用' : '模板已停用；不会用于新任务')
  } catch {
    ElMessage.error('模板状态更新失败')
  }
}

function onTemplateStatusChange(value: string | number | boolean, row: InspectionTemplate) {
  void changeTemplateStatus(row, Boolean(value))
}

function openRuleDialog() {
  Object.assign(ruleForm, { name: '', productCode: '', processCode: '', processName: '', taskType: 'PROCESS', templateId: '', enabled: true, priority: 100 })
  ruleDialogVisible.value = true
}

function applyTemplateToRule(templateId: string) {
  const template = templates.value.find((item) => item.id === templateId)
  if (!template) return
  ruleForm.processCode = template.processCode
  ruleForm.processName = template.processName
  ruleForm.taskType = template.taskType
  if (!ruleForm.name) ruleForm.name = `${template.name} 自动派单规则`
}

async function saveRule() {
  if (!ruleForm.name.trim() || !ruleForm.processCode.trim() || !ruleForm.processName.trim()) {
    ElMessage.warning('请填写规则名称和工序')
    return
  }
  savingRule.value = true
  try {
    await qmsApi.createInspectionRule({ ...ruleForm, templateId: ruleForm.templateId || undefined })
    ElMessage.success('自动派单规则已创建')
    ruleDialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('自动派单规则创建失败')
  } finally {
    savingRule.value = false
  }
}

function templateName(templateId?: string) {
  return templates.value.find((template) => template.id === templateId)?.name ?? '未关联模板（通用记录）'
}

function openProductDialog() {
  Object.assign(productForm, { productCode: '', productName: '', specification: '' })
  productDialogVisible.value = true
}

async function saveProduct() {
  if (!productForm.productCode.trim() || !productForm.productName.trim()) {
    ElMessage.warning('请填写产品编号和产品名称')
    return
  }
  savingProduct.value = true
  try {
    await qmsApi.createProduct(productForm)
    ElMessage.success('产品资料已创建')
    productDialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('产品资料创建失败；如已启用料号规则，请确认产品编号符合系统参数中的格式要求')
  } finally {
    savingProduct.value = false
  }
}

function openProcessDialog() {
  Object.assign(processForm, { processCode: '', processName: '', sequenceNo: 100 })
  processDialogVisible.value = true
}

async function saveProcess() {
  if (!processForm.processCode.trim() || !processForm.processName.trim()) {
    ElMessage.warning('请填写工序编码和工序名称')
    return
  }
  savingProcess.value = true
  try {
    await qmsApi.createProcess(processForm)
    ElMessage.success('工序资料已创建')
    processDialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('工序资料创建失败')
  } finally {
    savingProcess.value = false
  }
}

async function changeProductStatus(row: ProductMaster, active: boolean) {
  try {
    await qmsApi.updateProductStatus(row.id, active ? 'ACTIVE' : 'INACTIVE')
    row.status = active ? 'ACTIVE' : 'INACTIVE'
  } catch {
    ElMessage.error('产品状态更新失败')
  }
}

async function changeProcessStatus(row: ProcessMaster, active: boolean) {
  try {
    await qmsApi.updateProcessStatus(row.id, active ? 'ACTIVE' : 'INACTIVE')
    row.status = active ? 'ACTIVE' : 'INACTIVE'
  } catch {
    ElMessage.error('工序状态更新失败')
  }
}

function onProductStatusChange(value: string | number | boolean, row: ProductMaster) {
  void changeProductStatus(row, Boolean(value))
}

function onProcessStatusChange(value: string | number | boolean, row: ProcessMaster) {
  void changeProcessStatus(row, Boolean(value))
}

async function handleTabChange(tabName: string | number) {
  if (tabName !== 'users' || users.value.length) return
  try {
    users.value = await qmsApi.listUserAccesses()
  } catch {
    ElMessage.warning('仅租户管理员可查看和维护用户权限')
  }
}

function configuredTab() {
  const tabName = String(route.meta.configTab ?? 'templates')
  return ['templates', 'rules', 'master-data', 'users'].includes(tabName) ? tabName : 'templates'
}

async function syncTabFromRoute() {
  activeTab.value = configuredTab()
  await handleTabChange(activeTab.value)
}

function openUserDialog(user?: UserAccess) {
  Object.assign(userForm, user
    ? { id: user.id, userName: user.userName, displayName: user.displayName ?? '', roleCode: user.roleCode, status: user.status, password: '', processCodes: [...user.processCodes] }
    : { id: '', userName: '', displayName: '', roleCode: 'INSPECTOR', status: 'ACTIVE', password: '', processCodes: [] })
  userDialogVisible.value = true
}

async function saveUser() {
  if (!userForm.userName.trim()) {
    ElMessage.warning('请填写用户标识')
    return
  }
  if (!userForm.id && userForm.password.length < 10) {
    ElMessage.warning('新建用户的密码至少需要 10 位')
    return
  }
  if (userForm.id && userForm.password && userForm.password.length < 10) {
    ElMessage.warning('重置密码至少需要 10 位；如不修改密码请留空')
    return
  }
  if (userForm.roleCode === 'INSPECTOR' && !userForm.processCodes.length) {
    ElMessage.warning('检验员至少需要分配一个工序')
    return
  }
  savingUser.value = true
  try {
    const payload = { userName: userForm.userName, displayName: userForm.displayName, roleCode: userForm.roleCode, status: userForm.status, password: userForm.password || undefined, processCodes: userForm.processCodes }
    if (userForm.id) await qmsApi.updateUserAccess(userForm.id, payload)
    else await qmsApi.createUserAccess(payload)
    ElMessage.success('用户权限已保存')
    userDialogVisible.value = false
    users.value = await qmsApi.listUserAccesses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户权限保存失败')
  } finally {
    savingUser.value = false
  }
}

watch(() => route.fullPath, () => { void syncTabFromRoute() })
onMounted(async () => {
  await load()
  await syncTabFromRoute()
})
</script>

<template>
  <section class="page-stack">
    <div class="hero-card compact-hero">
      <div>
        <p class="eyebrow">客户管理员</p>
        <h2>检验标准与自动派单</h2>
        <p>先定义检验模板，再将模板绑定到工序派单规则；新生成的任务会在现场展示对应的检验项。</p>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="检验模板" name="templates">
          <div class="toolbar"><el-button type="primary" @click="openTemplateDialog">新建检验模板</el-button><el-button @click="load">刷新</el-button></div>
          <el-table v-loading="loading" :data="templates" empty-text="暂无检验模板">
            <el-table-column prop="templateCode" label="模板编码" min-width="155" />
            <el-table-column prop="name" label="模板名称" min-width="180" />
            <el-table-column label="适用工序" min-width="150"><template #default="scope">{{ scope.row.processName }}（{{ scope.row.processCode }}）</template></el-table-column>
            <el-table-column label="任务类型" width="115"><template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template></el-table-column>
            <el-table-column label="检验项" width="90"><template #default="scope">{{ scope.row.items.length }}</template></el-table-column>
            <el-table-column label="状态" width="124" fixed="right"><template #default="scope"><div class="table-status-control"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ statusText(scope.row.status) }}</el-tag><el-switch :model-value="scope.row.status === 'ACTIVE'" @change="onTemplateStatusChange($event, scope.row)" /></div></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="自动派单规则" name="rules">
          <div class="toolbar"><el-button type="primary" @click="openRuleDialog">新建派单规则</el-button><el-button @click="load">刷新</el-button></div>
          <el-table v-loading="loading" :data="rules" empty-text="暂无自动派单规则">
            <el-table-column prop="priority" label="优先级" width="85" />
            <el-table-column prop="name" label="规则名称" min-width="180" />
            <el-table-column prop="productCode" label="限定产品" min-width="130"><template #default="scope">{{ scope.row.productCode || '全部产品' }}</template></el-table-column>
            <el-table-column label="工序" min-width="140"><template #default="scope">{{ scope.row.processName }}（{{ scope.row.processCode }}）</template></el-table-column>
            <el-table-column label="检验模板" min-width="190"><template #default="scope">{{ templateName(scope.row.templateId) }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="产品与工序" name="master-data">
          <div class="master-data-grid">
            <div>
              <div class="toolbar"><strong>产品资料</strong><el-button type="primary" plain @click="openProductDialog">新增产品</el-button></div>
              <el-table v-loading="loading" :data="products" size="small" empty-text="暂无产品资料">
                <el-table-column prop="productCode" label="产品编号" min-width="130" />
                <el-table-column prop="productName" label="产品名称" min-width="150" />
                <el-table-column prop="specification" label="规格" min-width="170" show-overflow-tooltip />
                <el-table-column label="状态" width="124" fixed="right"><template #default="scope"><div class="table-status-control"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ statusText(scope.row.status) }}</el-tag><el-switch :model-value="scope.row.status === 'ACTIVE'" @change="onProductStatusChange($event, scope.row)" /></div></template></el-table-column>
              </el-table>
            </div>
            <div>
              <div class="toolbar"><strong>工序资料</strong><el-button type="primary" plain @click="openProcessDialog">新增工序</el-button></div>
              <el-table v-loading="loading" :data="processes" size="small" empty-text="暂无工序资料">
                <el-table-column prop="sequenceNo" label="顺序" width="75" />
                <el-table-column prop="processCode" label="工序编码" min-width="120" />
                <el-table-column prop="processName" label="工序名称" min-width="150" />
                <el-table-column label="状态" width="124" fixed="right"><template #default="scope"><div class="table-status-control"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ statusText(scope.row.status) }}</el-tag><el-switch :model-value="scope.row.status === 'ACTIVE'" @change="onProcessStatusChange($event, scope.row)" /></div></template></el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="用户与工序权限" name="users">
          <div class="toolbar"><span class="helper-text">仅租户管理员可维护；检验员需绑定可操作的工序。</span><el-button type="primary" @click="openUserDialog()">新增用户</el-button></div>
          <el-table :data="users" empty-text="切换为租户管理员后可查看用户权限">
            <el-table-column prop="userName" label="用户标识" min-width="150" />
            <el-table-column prop="displayName" label="显示名称" min-width="130" />
            <el-table-column prop="roleCode" label="角色" min-width="130" />
            <el-table-column label="授权工序" min-width="190"><template #default="scope">{{ scope.row.processCodes.join('、') || '全部工序' }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="scope"><el-button link type="primary" @click="openUserDialog(scope.row)">编辑</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="templateDialogVisible" title="新建检验模板" width="min(920px, 94vw)" destroy-on-close>
      <el-form :model="templateForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="模板编码"><el-input v-model="templateForm.templateCode" placeholder="例如 FQC-STD-V1" /></el-form-item>
          <el-form-item label="模板名称"><el-input v-model="templateForm.name" placeholder="例如 FQC 成品检验标准" /></el-form-item>
          <el-form-item label="限定产品编号"><el-input v-model="templateForm.productCode" placeholder="留空代表所有产品" /></el-form-item>
          <el-form-item label="任务类型"><el-select v-model="templateForm.taskType" class="full-width"><el-option label="制程巡检" value="PROCESS" /><el-option label="成品检验" value="FINISHED" /></el-select></el-form-item>
          <el-form-item label="工序编码"><el-input v-model="templateForm.processCode" placeholder="例如 FQC" /></el-form-item>
          <el-form-item label="工序名称"><el-input v-model="templateForm.processName" placeholder="例如 成品检验" /></el-form-item>
        </div>
        <div class="item-editor-header"><strong>检验项</strong><el-button link type="primary" @click="addItem">+ 添加检验项</el-button></div>
        <div v-for="(item, index) in templateForm.items" :key="index" class="template-item-editor">
          <div class="template-item-title">检验项 {{ index + 1 }}<el-button link type="danger" @click="removeItem(index)">移除</el-button></div>
          <div class="template-item-grid">
            <el-input v-model="item.itemCode" placeholder="检验项编码" />
            <el-input v-model="item.itemName" placeholder="检验项名称" />
            <el-select v-model="item.inputType"><el-option label="文本" value="TEXT" /><el-option label="数值" value="NUMBER" /><el-option label="下拉选择" value="SELECT" /><el-option label="是/否" value="BOOLEAN" /></el-select>
            <el-checkbox v-model="item.required">必填</el-checkbox>
            <el-input-number v-model="item.lowerLimit" :controls="false" placeholder="下限" />
            <el-input-number v-model="item.upperLimit" :controls="false" placeholder="上限" />
            <el-input v-model="item.unit" placeholder="单位，例如 mm" />
            <el-input v-if="item.inputType === 'SELECT'" v-model="item.optionsText" placeholder="选项用逗号分隔，例如 合格,不合格" />
            <el-input v-else v-model="item.helpText" placeholder="检验说明（可选）" />
          </div>
        </div>
      </el-form>
      <template #footer><el-button @click="templateDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingTemplate" @click="saveTemplate">创建模板</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" title="新建自动派单规则" width="min(620px, 94vw)" destroy-on-close>
      <el-form :model="ruleForm" label-position="top">
        <el-alert type="info" :closable="false" title="当同步或手工导入的工单到达对应当前工序时，系统可按此规则创建待检任务。" class="dialog-alert" />
        <div class="form-grid">
          <el-form-item label="规则名称"><el-input v-model="ruleForm.name" /></el-form-item>
          <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="0" class="full-width" /></el-form-item>
          <el-form-item label="关联模板"><el-select v-model="ruleForm.templateId" clearable class="full-width" placeholder="可不关联" @change="applyTemplateToRule"><el-option v-for="template in templates.filter((item) => item.status === 'ACTIVE')" :key="template.id" :label="`${template.templateCode} · ${template.name}`" :value="template.id" /></el-select></el-form-item>
          <el-form-item label="限定产品编号"><el-input v-model="ruleForm.productCode" placeholder="留空代表所有产品" /></el-form-item>
          <el-form-item label="工序编码"><el-input v-model="ruleForm.processCode" /></el-form-item>
          <el-form-item label="工序名称"><el-input v-model="ruleForm.processName" /></el-form-item>
          <el-form-item label="任务类型"><el-select v-model="ruleForm.taskType" class="full-width"><el-option label="制程巡检" value="PROCESS" /><el-option label="成品检验" value="FINISHED" /></el-select></el-form-item>
          <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="ruleDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingRule" @click="saveRule">创建规则</el-button></template>
    </el-dialog>

    <el-dialog v-model="productDialogVisible" title="新增产品资料" width="min(520px, 94vw)" destroy-on-close>
      <el-form :model="productForm" label-position="top"><el-form-item label="产品编号"><el-input v-model="productForm.productCode" /></el-form-item><el-form-item label="产品名称"><el-input v-model="productForm.productName" /></el-form-item><el-form-item label="规格"><el-input v-model="productForm.specification" type="textarea" :rows="3" /></el-form-item></el-form>
      <template #footer><el-button @click="productDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingProduct" @click="saveProduct">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="processDialogVisible" title="新增工序资料" width="min(520px, 94vw)" destroy-on-close>
      <el-form :model="processForm" label-position="top"><el-form-item label="工序编码"><el-input v-model="processForm.processCode" placeholder="例如 DRILL" /></el-form-item><el-form-item label="工序名称"><el-input v-model="processForm.processName" /></el-form-item><el-form-item label="工序顺序"><el-input-number v-model="processForm.sequenceNo" :min="0" class="full-width" /></el-form-item></el-form>
      <template #footer><el-button @click="processDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingProcess" @click="saveProcess">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" :title="userForm.id ? '编辑用户权限' : '新增用户权限'" width="min(620px, 94vw)" destroy-on-close>
      <el-form :model="userForm" label-position="top">
        <div class="form-grid"><el-form-item label="用户标识"><el-input v-model="userForm.userName" :disabled="Boolean(userForm.id)" placeholder="登录用户名，创建后不可修改" /></el-form-item><el-form-item label="显示名称"><el-input v-model="userForm.displayName" /></el-form-item><el-form-item :label="userForm.id ? '重置密码（留空不修改）' : '初始密码'" :required="!userForm.id"><el-input v-model="userForm.password" type="password" show-password autocomplete="new-password" placeholder="至少 10 位" /></el-form-item><el-form-item label="角色"><el-select v-model="userForm.roleCode" class="full-width"><el-option v-if="platformAdministrator" label="平台管理员" value="PLATFORM_ADMIN" /><el-option label="租户管理员" value="TENANT_ADMIN" /><el-option label="主管" value="SUPERVISOR" /><el-option label="检验员" value="INSPECTOR" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="userForm.status" class="full-width"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select></el-form-item></div>
        <el-form-item label="授权工序"><el-select v-model="userForm.processCodes" multiple clearable filterable class="full-width" placeholder="检验员必须选择至少一个工序"><el-option v-for="process in processes.filter((item) => item.status === 'ACTIVE')" :key="process.id" :label="`${process.processCode} · ${process.processName}`" :value="process.processCode" /></el-select><div class="field-help">管理员和主管不受此限制；检验员仅可查看和提交所选工序的检验任务。</div></el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingUser" @click="saveUser">保存</el-button></template>
    </el-dialog>
  </section>
</template>
