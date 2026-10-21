<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime, statusText } from '@/utils/display-text'
import type { Equipment, EquipmentAlert, EquipmentMaintenance, EquipmentMaintenanceItem, EquipmentType } from '@/types/qms'

const loading = ref(false)
const route = useRoute()
const equipment = ref<Equipment[]>([])
const equipmentTotal = ref(0)
const equipmentCurrentPage = ref(1)
const equipmentPageSize = ref(10)
const equipmentTypes = ref<EquipmentType[]>([])
const maintenance = ref<EquipmentMaintenance[]>([])
const alerts = ref<EquipmentAlert[]>([])
const equipmentDialog = ref(false)
const editingEquipmentId = ref<string>()
const maintenanceDialog = ref(false)
const completeDialog = ref(false)
const selectedMaintenance = ref<EquipmentMaintenance>()
const completionItems = ref<EquipmentMaintenanceItem[]>([])
const completionValues = reactive<Record<string, number | undefined>>({})
const equipmentFormRef = ref<FormInstance>()
const maintenanceFormRef = ref<FormInstance>()
const completeFormRef = ref<FormInstance>()
const saving = ref(false)
const deletingEquipmentId = ref<string>()
const equipmentForm = reactive({ assetNo: '', name: '', equipmentType: '', manufacturer: '', model: '', processCode: '', processName: '', responsibleName: '', status: 'IN_USE', calibrationDueAt: '' })
const equipmentFilters = reactive({ keyword: '', equipmentType: '', status: '' })
const maintenanceForm = reactive({ equipmentId: '', maintenanceType: 'MAINTENANCE', scheduledAt: '', description: '', executorName: '' })
const completeForm = reactive({ result: '' })
const equipmentRules: FormRules = { assetNo: [{ required: true, message: '请输入设备编号', trigger: 'blur' }], name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }], equipmentType: [{ required: true, message: '请输入设备类型', trigger: 'blur' }] }
const maintenanceRules: FormRules = { equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }], maintenanceType: [{ required: true, message: '请选择类型', trigger: 'change' }], description: [{ required: true, message: '请输入工作内容', trigger: 'blur' }] }
const completeRules: FormRules = { result: [{ required: true, message: '请输入执行结果', trigger: 'blur' }] }
const fixedEquipmentTypes: EquipmentType[] = [
  { code: 'PRODUCTION', name: '生产设备' },
  { code: 'PROCESS_INSPECTION', name: '制程检测设备' },
  { code: 'FINISHED_TEST', name: '成品测试设备' },
  { code: 'MEASUREMENT', name: '测量设备' },
  { code: 'AUXILIARY', name: '辅助设备' }
]

const requestedMaintenanceType = computed(() => String(route.meta.maintenanceType ?? ''))
const isMaintenancePage = computed(() => route.meta.equipmentSection === 'maintenance')
const isAlertPage = computed(() => route.meta.equipmentSection === 'alerts')
const isLedgerPage = computed(() => !isMaintenancePage.value && !isAlertPage.value)
const maintenanceTitle = computed(() => ({
  INSPECTION: '点检管理',
  MAINTENANCE: '保养管理',
  REPAIR: '维修管理'
}[requestedMaintenanceType.value] ?? '点检、保养、维修与校准'))
const visibleMaintenance = computed(() => requestedMaintenanceType.value
  ? maintenance.value.filter((item) => item.maintenanceType === requestedMaintenanceType.value)
  : maintenance.value)
const equipmentPageStart = computed(() => equipmentTotal.value ? (equipmentCurrentPage.value - 1) * equipmentPageSize.value + 1 : 0)
const equipmentPageEnd = computed(() => Math.min(equipmentCurrentPage.value * equipmentPageSize.value, equipmentTotal.value))

function maintenanceTypeText(type: string) {
  return ({ INSPECTION: '点检', MAINTENANCE: '保养', REPAIR: '维修', CALIBRATION: '校准/检定' })[type] ?? type
}

function equipmentStatusTagType(status: string) {
  return ({ ALARM: 'danger', MAINTENANCE: 'warning', DISABLED: 'info' } as Record<string, 'danger' | 'warning' | 'info'>)[status] ?? 'success'
}

async function load() {
  loading.value = true
  try {
    const [equipmentResult, maintenanceResult, equipmentTypesResult, alertsResult] = await Promise.allSettled([
      isLedgerPage.value ? qmsApi.queryEquipment({
        keyword: equipmentFilters.keyword.trim() || undefined,
        equipmentType: equipmentFilters.equipmentType || undefined,
        status: equipmentFilters.status || undefined,
        page: equipmentCurrentPage.value,
        pageSize: equipmentPageSize.value
      }) : isMaintenancePage.value ? qmsApi.listEquipment() : Promise.resolve([]),
      isMaintenancePage.value ? qmsApi.listEquipmentMaintenance() : Promise.resolve([]),
      isLedgerPage.value ? qmsApi.listEquipmentTypes() : Promise.resolve([]),
      isAlertPage.value ? qmsApi.listEquipmentAlerts('OPEN') : Promise.resolve([])
    ])
    if (equipmentResult.status === 'fulfilled') {
      if (isLedgerPage.value) {
        const pageResult = equipmentResult.value as Awaited<ReturnType<typeof qmsApi.queryEquipment>>
        equipment.value = pageResult.records
        equipmentTotal.value = pageResult.total
        equipmentCurrentPage.value = pageResult.page
        equipmentPageSize.value = pageResult.pageSize
      } else {
        equipment.value = equipmentResult.value as Equipment[]
      }
    } else {
      equipment.value = []
      ElMessage.error(isMaintenancePage.value ? '设备数据读取失败' : '设备台账数据读取失败')
    }
    if (maintenanceResult.status === 'fulfilled') {
      maintenance.value = maintenanceResult.value
    } else if (isMaintenancePage.value) {
      maintenance.value = []
      ElMessage.warning('维护记录读取失败')
    }
    equipmentTypes.value = equipmentTypesResult.status === 'fulfilled' && equipmentTypesResult.value.length
      ? equipmentTypesResult.value
      : fixedEquipmentTypes
    if (alertsResult.status === 'fulfilled') {
      alerts.value = alertsResult.value
    } else {
      alerts.value = []
      ElMessage.warning('设备告警读取失败')
    }
  } finally {
    loading.value = false
  }
}

function searchEquipment() {
  equipmentCurrentPage.value = 1
  void load()
}

function resetEquipmentFilters() {
  Object.assign(equipmentFilters, { keyword: '', equipmentType: '', status: '' })
  equipmentCurrentPage.value = 1
  void load()
}

function changeEquipmentPageSize(size: number) {
  equipmentPageSize.value = size
  equipmentCurrentPage.value = 1
  void load()
}

function openEquipment() {
  editingEquipmentId.value = undefined
  Object.assign(equipmentForm, { assetNo: '', name: '', equipmentType: '', manufacturer: '', model: '', processCode: '', processName: '', responsibleName: '', status: 'IN_USE', calibrationDueAt: '' })
  equipmentDialog.value = true
}

function openEditEquipment(item: Equipment) {
  editingEquipmentId.value = item.id
  Object.assign(equipmentForm, {
    assetNo: item.assetNo,
    name: item.name,
    equipmentType: item.equipmentType,
    manufacturer: item.manufacturer ?? '',
    model: item.model ?? '',
    processCode: item.processCode ?? '',
    processName: item.processName ?? '',
    responsibleName: item.responsibleName ?? '',
    status: item.status,
    calibrationDueAt: item.calibrationDueAt ?? ''
  })
  equipmentDialog.value = true
}

async function saveEquipment() {
  const valid = await equipmentFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...equipmentForm, calibrationDueAt: equipmentForm.calibrationDueAt || null }
    if (editingEquipmentId.value) {
      const { assetNo: _assetNo, ...updatePayload } = payload
      await qmsApi.updateEquipment(editingEquipmentId.value, updatePayload)
      ElMessage.success('设备台账已更新')
    } else {
      const { status: _status, ...createPayload } = payload
      await qmsApi.createEquipment(createPayload)
      ElMessage.success('设备台账已保存')
    }
    equipmentDialog.value = false
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '设备保存失败'))
  } finally { saving.value = false }
}

function requestErrorMessage(error: unknown, fallback: string) {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  return typeof message === 'string' && message.trim() ? message : fallback
}

async function deleteEquipment(item: Equipment) {
  try {
    await ElMessageBox.confirm(`确定删除设备“${item.name}（${item.assetNo}）”吗？删除后不可恢复。`, '删除设备', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  deletingEquipmentId.value = item.id
  try {
    await qmsApi.deleteEquipment(item.id)
    ElMessage.success('设备已删除')
    await load()
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '设备删除失败'))
  } finally {
    deletingEquipmentId.value = undefined
  }
}

function openMaintenance() {
  Object.assign(maintenanceForm, {
    equipmentId: equipment.value[0]?.id ?? '',
    maintenanceType: requestedMaintenanceType.value || 'MAINTENANCE',
    scheduledAt: '',
    description: '',
    executorName: ''
  })
  maintenanceDialog.value = true
}

async function saveMaintenance() {
  const valid = await maintenanceFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await qmsApi.createEquipmentMaintenance({ ...maintenanceForm, scheduledAt: maintenanceForm.scheduledAt || null })
    ElMessage.success('维护计划已创建')
    maintenanceDialog.value = false
    await load()
  } catch {
    ElMessage.error('维护计划创建失败')
  } finally { saving.value = false }
}

async function openComplete(item: EquipmentMaintenance) {
  selectedMaintenance.value = item
  completeForm.result = ''
  completionItems.value = []
  Object.keys(completionValues).forEach((key) => delete completionValues[key])
  try {
    completionItems.value = await qmsApi.listEquipmentMaintenanceItems(item.id)
    completionItems.value.forEach((maintenanceItem) => { completionValues[maintenanceItem.id] = maintenanceItem.measuredValue })
    completeDialog.value = true
  } catch (error) {
    ElMessage.error(requestErrorMessage(error, '维护项目读取失败'))
  }
}

async function completeMaintenance() {
  const valid = await completeFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedMaintenance.value) return
  saving.value = true
  try {
    await qmsApi.completeEquipmentMaintenance(selectedMaintenance.value.id, {
      result: completeForm.result,
      items: completionItems.value.map((item) => ({ maintenanceItemId: item.id, measuredValue: completionValues[item.id] }))
    })
    ElMessage.success(completionItems.value.length ? '维护结果已判定' : '维修记录已完成，相关告警已消除')
    completeDialog.value = false
    await load()
  } catch {
    ElMessage.error('维护完成提交失败')
  } finally { saving.value = false }
}

watch(() => route.fullPath, () => { void load() })
onMounted(load)
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <template v-if="isLedgerPage">
    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>设备台账</span><el-button type="primary" @click="openEquipment">新增设备</el-button></div></template>
      <el-alert title="设备类型为固定选项：类型选择“成品测试设备”的设备，会自动展示在“成品测试实时监控工作台”。" type="info" :closable="false" show-icon class="equipment-monitor-tip" />
      <div class="toolbar equipment-filter-bar"><el-input v-model="equipmentFilters.keyword" clearable placeholder="设备编号、名称、厂商、型号、责任人" style="width: 280px" @keyup.enter="searchEquipment" /><el-select v-model="equipmentFilters.equipmentType" clearable placeholder="全部设备类型" style="width: 180px"><el-option v-for="item in equipmentTypes" :key="item.code" :label="item.name" :value="item.name" /></el-select><el-select v-model="equipmentFilters.status" clearable placeholder="全部状态" style="width: 130px"><el-option label="在用" value="IN_USE" /><el-option label="维护中" value="MAINTENANCE" /><el-option label="停用" value="DISABLED" /><el-option label="告警中" value="ALARM" /></el-select><el-button @click="resetEquipmentFilters">重置</el-button><el-button type="primary" @click="searchEquipment">查询</el-button></div>
      <el-table :data="equipment" empty-text="暂无设备台账">
        <el-table-column prop="assetNo" label="设备编号" min-width="145" />
        <el-table-column prop="name" label="设备名称" min-width="160" />
        <el-table-column prop="equipmentType" label="设备类型" min-width="150" />
        <el-table-column prop="manufacturer" label="厂商" min-width="130" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" min-width="100"><template #default="scope"><el-tag :type="equipmentStatusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="145" fixed="right"><template #default="scope"><el-button type="primary" link @click="openEditEquipment(scope.row)">编辑</el-button><el-button type="danger" link :loading="deletingEquipmentId === scope.row.id" @click="deleteEquipment(scope.row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="equipment-pagination"><span>显示第 {{ equipmentPageStart }}–{{ equipmentPageEnd }} 条，共 {{ equipmentTotal }} 条</span><el-pagination :current-page="equipmentCurrentPage" :page-size="equipmentPageSize" :page-sizes="[10, 20, 50, 100]" :total="equipmentTotal" background layout="sizes, prev, pager, next, jumper" @current-change="(page: number) => { equipmentCurrentPage = page; load() }" @size-change="changeEquipmentPageSize" /></div>
    </el-card>

    <el-dialog v-model="equipmentDialog" :title="editingEquipmentId ? '编辑设备' : '新增设备'" width="min(640px, 94vw)">
      <el-form ref="equipmentFormRef" :model="equipmentForm" :rules="equipmentRules" label-position="top"><div class="form-grid"><el-form-item label="设备编号" prop="assetNo"><el-input v-model="equipmentForm.assetNo" :disabled="Boolean(editingEquipmentId)" /></el-form-item><el-form-item label="设备名称" prop="name"><el-input v-model="equipmentForm.name" /></el-form-item><el-form-item label="设备类型" prop="equipmentType"><el-select v-model="equipmentForm.equipmentType" class="full-width" placeholder="请选择设备类型"><el-option v-for="item in equipmentTypes" :key="item.code" :label="item.name" :value="item.name" /></el-select><div v-if="!equipmentTypes.length" class="field-help">请先在“设备管理 / 设备类型”中配置并启用设备类型。</div></el-form-item><el-form-item v-if="editingEquipmentId" label="状态"><el-select v-model="equipmentForm.status" class="full-width"><el-option label="在用" value="IN_USE" /><el-option label="维护中" value="MAINTENANCE" /><el-option label="停用" value="DISABLED" /><el-option label="告警中" value="ALARM" /></el-select></el-form-item><el-form-item label="厂商"><el-input v-model="equipmentForm.manufacturer" placeholder="例如：三菱" /></el-form-item><el-form-item label="型号"><el-input v-model="equipmentForm.model" placeholder="例如：MV-2000" /></el-form-item><el-form-item label="责任人"><el-input v-model="equipmentForm.responsibleName" /></el-form-item><el-form-item label="工序编码"><el-input v-model="equipmentForm.processCode" /></el-form-item><el-form-item label="工序名称"><el-input v-model="equipmentForm.processName" /></el-form-item></div><el-form-item label="校准/检定到期时间"><el-date-picker v-model="equipmentForm.calibrationDueAt" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" /></el-form-item></el-form>
      <template #footer><el-button @click="equipmentDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEquipment">保存</el-button></template>
    </el-dialog>
    </template>

    <template v-else-if="isMaintenancePage">
      <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>{{ maintenanceTitle }}</span><el-button type="primary" :disabled="!equipment.length" @click="openMaintenance">创建维护记录</el-button></div></template>
      <el-table :data="visibleMaintenance" empty-text="暂无维护记录">
        <el-table-column prop="recordNo" label="记录号" min-width="160" />
        <el-table-column prop="equipmentAssetNo" label="设备编号" min-width="140" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="150" />
        <el-table-column label="类型" min-width="115"><template #default="scope">{{ maintenanceTypeText(scope.row.maintenanceType) }}</template></el-table-column>
        <el-table-column label="计划时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.scheduledAt) }}</template></el-table-column>
        <el-table-column prop="executorName" label="执行人" min-width="110" />
        <el-table-column label="状态" min-width="135"><template #default="scope"><el-tag :type="scope.row.scheduleStatus === 'ALERT' ? 'danger' : scope.row.status === 'COMPLETED' ? 'success' : scope.row.scheduleStatus === 'OVERDUE' ? 'danger' : 'warning'">{{ scope.row.scheduleStatus === 'ALERT' ? '异常告警' : scope.row.status === 'COMPLETED' ? '已完成' : scope.row.scheduleStatus === 'OVERDUE' ? '计划逾期' : '待执行' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="scope"><el-button v-if="!['COMPLETED', 'ALERT'].includes(scope.row.status)" type="primary" link @click="openComplete(scope.row)">填写结果</el-button><span v-else>{{ scope.row.status === 'ALERT' ? '已告警' : '已完成' }}</span></template></el-table-column>
      </el-table>
      </el-card>

    <el-dialog v-model="maintenanceDialog" title="创建设备维护记录" width="min(640px, 94vw)">
      <el-form ref="maintenanceFormRef" :model="maintenanceForm" :rules="maintenanceRules" label-position="top"><div class="form-grid"><el-form-item label="设备" prop="equipmentId"><el-select v-model="maintenanceForm.equipmentId" class="full-width"><el-option v-for="item in equipment" :key="item.id" :label="`${item.assetNo} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item label="维护类型" prop="maintenanceType"><el-select v-model="maintenanceForm.maintenanceType" class="full-width"><el-option label="点检" value="INSPECTION" /><el-option label="保养" value="MAINTENANCE" /><el-option label="维修" value="REPAIR" /><el-option label="校准/检定" value="CALIBRATION" /></el-select></el-form-item><el-form-item label="计划时间"><el-date-picker v-model="maintenanceForm.scheduledAt" type="datetime" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" /></el-form-item><el-form-item label="执行人"><el-input v-model="maintenanceForm.executorName" /></el-form-item></div><el-form-item label="工作内容" prop="description"><el-input v-model="maintenanceForm.description" type="textarea" :rows="3" /></el-form-item></el-form>
      <template #footer><el-button @click="maintenanceDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMaintenance">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="completeDialog" title="填写维护结果" width="min(760px, 94vw)"><el-alert v-if="completionItems.length" title="系统会依据实测值和项目标准自动判定；超出范围将自动生成设备告警。" type="info" :closable="false" show-icon class="equipment-monitor-tip" /><el-table v-if="completionItems.length" :data="completionItems" class="completion-item-table"><el-table-column prop="itemName" label="维护项目" min-width="150" /><el-table-column label="标准范围" min-width="160"><template #default="scope">{{ scope.row.lowerLimit ?? '-∞' }} ～ {{ scope.row.upperLimit ?? '+∞' }} {{ scope.row.unit ?? '' }}</template></el-table-column><el-table-column label="实测值" min-width="180"><template #default="scope"><el-input-number v-model="completionValues[scope.row.id]" :precision="4" :step="0.1" controls-position="right" /><span class="value-unit">{{ scope.row.unit }}</span></template></el-table-column></el-table><el-alert v-else title="该维修记录没有数值维护项目；提交完成后将自动消除该设备的未处理告警。" type="warning" :closable="false" show-icon class="equipment-monitor-tip" /><el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-position="top"><el-form-item label="执行结果" prop="result"><el-input v-model="completeForm.result" type="textarea" :rows="4" placeholder="填写维护、维修或复检结果" /></el-form-item></el-form><template #footer><el-button @click="completeDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="completeMaintenance">提交并判定</el-button></template></el-dialog>
    </template>

    <el-card v-else shadow="never" class="content-card">
      <template #header><div class="card-header"><span>设备告警</span><small>维护项目实测值超出标准时自动生成；完成该设备的维修记录后自动消警。</small></div></template>
      <el-table :data="alerts" empty-text="暂无未消除告警">
        <el-table-column prop="alertNo" label="告警编号" min-width="155" />
        <el-table-column prop="equipmentAssetNo" label="设备编号" min-width="130" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="140" />
        <el-table-column prop="title" label="告警内容" min-width="190" />
        <el-table-column prop="description" label="异常项目与标准" min-width="280" show-overflow-tooltip />
        <el-table-column label="告警时间" min-width="160"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="状态" min-width="100"><template #default><el-tag type="danger">待检修</el-tag></template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.equipment-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 2px 0;
  color: #667085;
  font-size: 13px;
}

.equipment-pagination > span {
  white-space: nowrap;
}

.equipment-pagination :deep(.el-pagination) {
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .equipment-pagination {
    align-items: flex-start;
    flex-direction: column;
  }

  .equipment-pagination :deep(.el-pagination) {
    justify-content: flex-start;
    overflow-x: auto;
    max-width: 100%;
  }
}
</style>
