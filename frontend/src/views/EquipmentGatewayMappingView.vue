<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime } from '@/utils/display-text'
import type { Equipment, EquipmentGatewayMapping } from '@/types/qms'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const mappings = ref<EquipmentGatewayMapping[]>([])
const equipment = ref<Equipment[]>([])
const invalidMappingCount = computed(() => mappings.value.filter((mapping) => !mapping.valid && !mapping.ignored).length)
const deletingMappingId = ref<string>()
const editingMappingId = ref<string>()
const form = reactive({ equipmentId: '', machineName: '', username: '', ignored: false })
const rules: FormRules = {
  equipmentId: [{ validator: (_rule, value, callback) => callback(form.ignored || value ? undefined : new Error('请选择 QMS 设备')), trigger: 'change' }],
  machineName: [{ required: true, message: '请输入旧监控软件的设备名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const [mappingsResult, equipmentResult] = await Promise.all([qmsApi.listEquipmentGatewayMappings(), qmsApi.listEquipment()])
    mappings.value = mappingsResult
    equipment.value = equipmentResult
  } catch (error) {
    mappings.value = []
    ElMessage.error(requestErrorMessage(error, '设备网关映射读取失败，请确认当前账号具有设备管理权限'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingMappingId.value = undefined
  Object.assign(form, { equipmentId: equipment.value[0]?.id ?? '', machineName: '', username: '', ignored: false })
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(mapping: EquipmentGatewayMapping) {
  editingMappingId.value = mapping.id
  Object.assign(form, {
    equipmentId: equipment.value.some((item) => item.id === mapping.equipmentId) ? mapping.equipmentId : '',
    machineName: mapping.machineName || '',
    username: mapping.username || '',
    ignored: !!mapping.ignored
  })
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openRequestedEdit() {
  const mapping = mappings.value.find((item) => item.id === route.query.edit)
  if (mapping) openEdit(mapping)
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      equipmentId: form.ignored ? undefined : form.equipmentId,
      ignored: form.ignored,
      machineName: form.machineName.trim(),
      username: form.username || undefined
    }
    if (editingMappingId.value) {
      await qmsApi.updateEquipmentGatewayMapping(editingMappingId.value, payload)
    } else {
      await qmsApi.createEquipmentGatewayMapping(payload)
    }
    ElMessage.success('设备网关映射已保存，下一次上报将按新映射处理')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '设备网关映射保存失败'))
  } finally {
    saving.value = false
  }
}

async function remove(mapping: EquipmentGatewayMapping) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${mapping.machineName ?? mapping.username}”到“${mapping.equipmentName ?? '已失效设备'}”的映射吗？删除后该来源的上报将不再入库。`,
      '删除设备网关映射',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  deletingMappingId.value = mapping.id
  try {
    await qmsApi.deleteEquipmentGatewayMapping(mapping.id)
    ElMessage.success('设备网关映射已删除')
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '设备网关映射删除失败'))
  } finally {
    deletingMappingId.value = undefined
  }
}

function requestErrorMessage(error: unknown, fallback: string) {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof message === 'string' && message.trim() ? message : fallback
}

watch(() => route.query.edit, openRequestedEdit)
watch(dialogVisible, (visible) => {
  if (!visible && route.query.edit) {
    const query = { ...route.query }
    delete query.edit
    void router.replace({ query })
  }
})
onMounted(async () => {
  await load()
  openRequestedEdit()
})
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <span>设备网关映射</span>
          <el-button type="primary" @click="openCreate">新增映射</el-button>
        </div>
      </template>

      <el-alert
        title="将旧监控软件的 machinename 绑定到 QMS 设备台账。名称匹配会自动忽略首尾空格和英文大小写。"
        type="info"
        :closable="false"
        show-icon
      />
      <p v-if="!equipment.length" class="field-help">请先在“设备台账”新增需要接收监控数据的设备。</p>
      <el-alert v-if="invalidMappingCount" :title="`有 ${invalidMappingCount} 条映射已失效，相应上报会跳过，其他有效设备继续处理。点击“编辑”选择正确的目标设备即可修复。`" type="warning" :closable="false" show-icon style="margin-top: 12px" />

      <el-table :data="mappings" empty-text="暂无设备网关映射" style="margin-top: 16px">
        <el-table-column label="QMS 设备编号" min-width="150"><template #default="scope">{{ scope.row.equipmentCode || '—' }}</template></el-table-column>
        <el-table-column label="QMS 设备名称" min-width="180"><template #default="scope">{{ scope.row.equipmentName || (scope.row.ignored ? '—' : '目标设备不可用') }}</template></el-table-column>
        <el-table-column label="旧监控软件设备名" min-width="190">
          <template #default="scope"><code>{{ scope.row.machineName || '-' }}</code></template>
        </el-table-column>
        <el-table-column label="最后更新" min-width="180">
          <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="映射状态" min-width="300">
          <template #default="scope"><el-tag :type="scope.row.ignored ? 'info' : scope.row.valid ? 'success' : 'warning'">{{ scope.row.ignored ? '已忽略' : scope.row.valid ? '有效' : '映射失效' }}</el-tag><div v-if="scope.row.mappingIssue" class="field-help">{{ scope.row.mappingIssue }}</div></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="danger" link :loading="deletingMappingId === scope.row.id" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingMappingId ? '编辑设备网关映射' : '新增设备网关映射'" width="min(560px, 94vw)" :close-on-click-modal="false">
      <el-alert title="标特福旧监控软件中 username 表示操作员，不能作为设备标识；请只填写 machinename。" type="warning" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" style="margin-top: 18px">
        <el-form-item v-if="form.username" label="已有来源用户标识"><el-input :model-value="form.username" disabled /><div class="field-help">保留其他网关使用的来源标识；旧监控软件按下方设备名称匹配。</div></el-form-item>
        <el-form-item label="映射状态"><el-radio-group v-model="form.ignored"><el-radio :value="false">接收数据</el-radio><el-radio :value="true">忽略</el-radio></el-radio-group><div v-if="form.ignored" class="field-help">忽略后仍正常回应上报，但不入库，也不加入待处理列表；可在此恢复接收。</div></el-form-item>
        <el-form-item v-if="!form.ignored" label="QMS 设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable class="full-width" placeholder="请选择设备台账">
            <el-option v-for="item in equipment" :key="item.id" :label="`${item.assetNo} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="旧监控软件设备名（machinename）" prop="machineName">
          <el-input v-model="form.machineName" maxlength="160" show-word-limit placeholder="例如：L001" />
          <div class="field-help">填写旧软件实际上报的 machinename；同一租户内不能重复映射到不同设备。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingMappingId ? '保存修改' : '创建映射' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
