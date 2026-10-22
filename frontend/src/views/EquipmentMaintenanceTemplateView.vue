<script setup lang="ts">
import { isAxiosError } from 'axios'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import type { EquipmentMaintenanceTemplateItem, EquipmentType } from '@/types/qms'

const fixedEquipmentTypes: EquipmentType[] = [
  { code: 'PRODUCTION', name: '生产设备' },
  { code: 'PROCESS_INSPECTION', name: '制程检测设备' },
  { code: 'FINISHED_TEST', name: '成品测试设备' },
  { code: 'MEASUREMENT', name: '测量设备' },
  { code: 'AUXILIARY', name: '辅助设备' }
]
const maintenanceTypes = [
  { value: 'INSPECTION', label: '点检' },
  { value: 'MAINTENANCE', label: '保养' },
  { value: 'REPAIR', label: '维修' },
  { value: 'CALIBRATION', label: '校准/检定' }
]

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const templateItems = ref<EquipmentMaintenanceTemplateItem[]>([])
const equipmentTypes = ref<EquipmentType[]>(fixedEquipmentTypes)
const filter = reactive({ equipmentType: '', maintenanceType: '' })
const formRef = ref<FormInstance>()
const form = reactive({ equipmentType: '', maintenanceType: 'MAINTENANCE', itemCode: '', itemName: '', lowerLimit: undefined as number | undefined, upperLimit: undefined as number | undefined, unit: '' })
const rules: FormRules = {
  equipmentType: [{ required: true, message: '请选择适用设备类型', trigger: 'change' }],
  maintenanceType: [{ required: true, message: '请选择维护类型', trigger: 'change' }],
  itemCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
}

function maintenanceTypeText(type: string) {
  return maintenanceTypes.find((item) => item.value === type)?.label ?? type
}

function requestErrorMessage(error: unknown, fallback: string) {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof message === 'string' && message.trim() ? message : fallback
}

async function load() {
  loading.value = true
  try {
    const [itemsResult, typesResult] = await Promise.allSettled([
      qmsApi.listEquipmentMaintenanceTemplateItems(filter.equipmentType || undefined, filter.maintenanceType || undefined),
      qmsApi.listEquipmentTypes()
    ])
    if (itemsResult.status === 'fulfilled') templateItems.value = itemsResult.value
    else ElMessage.error('维护项目模板读取失败')
    equipmentTypes.value = typesResult.status === 'fulfilled' && typesResult.value.length ? typesResult.value : fixedEquipmentTypes
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { equipmentType: filter.equipmentType, maintenanceType: filter.maintenanceType || 'MAINTENANCE', itemCode: '', itemName: '', lowerLimit: undefined, upperLimit: undefined, unit: '' })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.lowerLimit !== undefined && form.upperLimit !== undefined && form.lowerLimit > form.upperLimit) {
    ElMessage.warning('标准下限不能大于上限')
    return
  }
  saving.value = true
  try {
    await qmsApi.saveEquipmentMaintenanceTemplateItem(form)
    ElMessage.success('维护项目已保存；后续创建维护记录会自动带入该项目')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '维护项目保存失败'))
  } finally {
    saving.value = false
  }
}

async function remove(item: EquipmentMaintenanceTemplateItem) {
  try {
    await ElMessageBox.confirm(`确定删除维护项目“${item.itemName}”吗？已创建的维护记录不受影响。`, '删除维护项目', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch {
    return
  }
  try {
    await qmsApi.deleteEquipmentMaintenanceTemplateItem(item.id)
    ElMessage.success('维护项目已删除')
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '维护项目删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <section class="finished-test-hero equipment-template-hero">
      <div>
        <p class="eyebrow">设备管理 / 项目模板</p>
        <h2>维护项目与标准值</h2>
        <p>按设备类型配置点检、保养、维修或校准项目。创建维护记录时系统自动带入项目，填写实测值后自动判定是否异常。</p>
      </div>
    </section>

    <el-card shadow="never" class="content-card">
      <div class="equipment-template-toolbar">
        <el-select v-model="filter.equipmentType" clearable placeholder="全部设备类型" @change="load"><el-option v-for="item in equipmentTypes" :key="item.code" :label="item.name" :value="item.name" /></el-select>
        <el-select v-model="filter.maintenanceType" clearable placeholder="全部维护类型" @change="load"><el-option v-for="item in maintenanceTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-button type="primary" @click="openCreate">新增维护项目</el-button>
      </div>
      <paged-table :key="`${filter.equipmentType}:${filter.maintenanceType}`" :data="templateItems" empty-text="暂无维护项目，请新增项目并设置标准值">
        <el-table-column prop="equipmentType" label="适用设备类型" min-width="160" />
        <el-table-column label="维护类型" min-width="120"><template #default="scope">{{ maintenanceTypeText(scope.row.maintenanceType) }}</template></el-table-column>
        <el-table-column prop="itemCode" label="项目编码" min-width="140" />
        <el-table-column prop="itemName" label="维护项目" min-width="170" />
        <el-table-column label="标准下限" min-width="120"><template #default="scope">{{ scope.row.lowerLimit ?? '未设置' }}</template></el-table-column>
        <el-table-column label="标准上限" min-width="120"><template #default="scope">{{ scope.row.upperLimit ?? '未设置' }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" min-width="90" />
        <el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button type="danger" link @click="remove(scope.row)">删除</el-button></template></el-table-column>
      </paged-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增维护项目" width="min(680px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="适用设备类型" prop="equipmentType"><el-select v-model="form.equipmentType" class="full-width"><el-option v-for="item in equipmentTypes" :key="item.code" :label="item.name" :value="item.name" /></el-select></el-form-item>
          <el-form-item label="维护类型" prop="maintenanceType"><el-select v-model="form.maintenanceType" class="full-width"><el-option v-for="item in maintenanceTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="项目编码" prop="itemCode"><el-input v-model="form.itemCode" placeholder="例如 POWER_VOLTAGE" /></el-form-item>
          <el-form-item label="项目名称" prop="itemName"><el-input v-model="form.itemName" placeholder="例如 供电电压" /></el-form-item>
          <el-form-item label="标准下限"><el-input-number v-model="form.lowerLimit" :precision="4" class="full-width" /></el-form-item>
          <el-form-item label="标准上限"><el-input-number v-model="form.upperLimit" :precision="4" class="full-width" /></el-form-item>
          <el-form-item label="单位"><el-input v-model="form.unit" placeholder="例如 V、Ω、%" /></el-form-item>
        </div>
        <el-alert title="标准值可只设置上限或下限；两者都不设置时，该项目仅记录实测值，不参与超标判定。" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
