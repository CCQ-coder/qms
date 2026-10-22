<script setup lang="ts">
import { isAxiosError } from 'axios'
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime } from '@/utils/display-text'
import type { Equipment, EquipmentGatewayMapping } from '@/types/qms'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const mappings = ref<EquipmentGatewayMapping[]>([])
const equipment = ref<Equipment[]>([])
const deletingMappingId = ref<string>()
const editingMappingId = ref<string>()
const form = reactive({ equipmentId: '', machineName: '', username: '' })
const rules: FormRules = {
  equipmentId: [{ required: true, message: '请选择 QMS 设备', trigger: 'change' }],
  machineName: [{ required: true, message: '请输入设备名或电脑名', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const [mappingsResult, equipmentResult] = await Promise.all([qmsApi.listEquipmentGatewayMappings(), qmsApi.listEquipment()])
    mappings.value = mappingsResult
    equipment.value = equipmentResult
  } catch (error) {
    mappings.value = []
    ElMessage.error(requestErrorMessage(error, '设备网关映射或设备台账读取失败，请检查服务器连接'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingMappingId.value = undefined
  Object.assign(form, { equipmentId: equipment.value[0]?.id ?? '', machineName: '', username: '' })
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(mapping: EquipmentGatewayMapping) {
  editingMappingId.value = mapping.id
  Object.assign(form, {
    equipmentId: equipment.value.some((item) => item.id === mapping.equipmentId) ? mapping.equipmentId ?? '' : '',
    machineName: mapping.machineName || '',
    username: mapping.username || ''
  })
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingMappingId.value) {
      await qmsApi.updateEquipmentGatewayMapping(editingMappingId.value, {
        equipmentId: form.equipmentId,
        machineName: form.machineName.trim(),
        username: form.username || undefined
      })
    } else {
      await qmsApi.createEquipmentGatewayMapping({
        equipmentId: form.equipmentId,
        machineName: form.machineName.trim()
      })
    }
    ElMessage.success(editingMappingId.value ? '设备网关映射已更新' : '设备网关映射已创建')
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
      `确定删除“${mapping.machineName ?? mapping.username}”到“${mapping.equipmentName}”的映射吗？删除后该来源的上报将不再入库。`,
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
  const message = isAxiosError(error) ? error.response?.data?.message : error instanceof Error ? error.message : undefined
  return typeof message === 'string' && message.trim() ? message : fallback
}

onMounted(() => { void load() })
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <span>设备网关映射</span>
          <el-button type="primary" :disabled="!equipment.length" @click="openCreate">新增映射</el-button>
        </div>
      </template>

      <el-alert
        title="将现场设备的 machinename 或采集电脑的 hostName 绑定到 QMS 设备台账。名称匹配会自动忽略首尾空格和英文大小写。"
        type="info"
        :closable="false"
        show-icon
      />
      <p v-if="!equipment.length" class="field-help">请先在“设备台账”新增需要接收监控数据的设备。</p>

      <paged-table :data="mappings" empty-text="暂无设备网关映射" style="margin-top: 16px">
        <el-table-column label="QMS 设备编号" min-width="150"><template #default="scope">{{ scope.row.equipmentCode || '—' }}</template></el-table-column>
        <el-table-column label="QMS 设备名称" min-width="180"><template #default="scope">{{ scope.row.equipmentName || '目标设备不可用' }}</template></el-table-column>
        <el-table-column label="来源设备名 / 电脑名" min-width="190">
          <template #default="scope"><code>{{ scope.row.machineName || '-' }}</code></template>
        </el-table-column>
        <el-table-column label="最后更新" min-width="180">
          <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button type="primary" link :disabled="!equipment.length" @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="danger" link :loading="deletingMappingId === scope.row.id" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </paged-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingMappingId ? '编辑设备网关映射' : '新增设备网关映射'" width="min(560px, 94vw)" :close-on-click-modal="false">
      <el-alert title="填写现场上报的 machinename 或采集电脑的 hostName；标特福旧软件的 username 是操作员，不能作为设备标识。" type="info" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" style="margin-top: 18px">
        <el-form-item label="QMS 设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable class="full-width" placeholder="请选择设备台账">
            <el-option v-for="item in equipment" :key="item.id" :label="`${item.assetNo} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源名称（machinename / hostName）" prop="machineName">
          <el-input v-model="form.machineName" maxlength="160" show-word-limit placeholder="例如：SCBGX" />
          <div class="field-help">填写现场实际上报的名称；同一租户内不能重复映射到不同设备。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingMappingId ? '保存修改' : '创建映射' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
