<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { loadMenuAccess, menuAccess } from '@/navigation/menu'
import { formatDate, formatDateTime, statusText } from '@/utils/display-text'
import { moveColumn } from './nonconformityColumns'
import { finishedTestProcess, responsibleDepartmentForProcess } from './nonconformityForm'
import type { ManualNonconformityInput, Nonconformity, NonconformityDetail, ProcessMaster, WorkOrder } from '@/types/qms'

const loading = ref(false)
const ncrs = ref<Nonconformity[]>([])
const saving = ref(false)
const processes = ref<ProcessMaster[]>([])
const departments = ref<string[]>([])
const scrapReasons = ref<string[]>([])
const workOrderOptions = ref<WorkOrder[]>([])
let workOrderSearchVersion = 0
const canReview = computed(() => ['PLATFORM_ADMIN', 'TENANT_ADMIN', 'SUPERVISOR'].includes(menuAccess.value?.roleCode ?? ''))
const manualDialog = ref(false)
const manualSaving = ref(false)
const selectedDetailNcrId = ref('')
const selectedLine = ref<NonconformityDetail>()
const detailDialog = ref(false)
const detailSaving = ref(false)
const detailForm = reactive({ scrapQuantity: 1, scrapReason: '' })
const draggedColumn = ref('')
const ncrColumns = ref([
  { key: 'ncrNo', label: '单据编号', width: 180 },
  { key: 'detectionDate', label: '检测日期', width: 120 },
  { key: 'shiftName', label: '班次', width: 90 },
  { key: 'processName', label: '检验工序', width: 140 },
  { key: 'responsibleDepartment', label: '责任部门', width: 130 },
  { key: 'remark', label: '备注', width: 170 },
  { key: 'dispositionMethod', label: '处置方式', width: 110 },
  { key: 'sourceType', label: '创建来源', width: 130 },
  { key: 'createdAt', label: '创建日期', width: 170 },
  { key: 'reviewedBy', label: '审核人', width: 110 },
  { key: 'reviewedAt', label: '审核时间', width: 170 },
  { key: 'status', label: '状态', width: 110 },
  { key: 'operation', label: '操作', width: 175 }
])
const selectedDetailNcr = computed(() => ncrs.value.find((ncr) => ncr.id === selectedDetailNcrId.value))
function canEditDetail(line?: NonconformityDetail) {
  return ['OPEN', 'PENDING_REVIEW'].includes(selectedDetailNcr.value?.status ?? '')
    && ['OPEN', 'PENDING_REVIEW'].includes(line?.status ?? '')
}
const detailEditable = computed(() => canEditDetail(selectedLine.value))
const calculatedScrapArea = computed(() => selectedLine.value?.singlePcsArea == null || !Number.isInteger(detailForm.scrapQuantity)
  ? '—' : Number((selectedLine.value.singlePcsArea * detailForm.scrapQuantity).toFixed(6)))
const newDetail = () => ({ workOrderNo: '', unit: 'PCS', scrapQuantity: 1, scrapReason: '', remark: '' })
const manualForm = reactive<ManualNonconformityInput>({
  detectionDate: formatDate(new Date()), shiftName: '', processCode: '', processName: '',
  responsibleDepartment: '测试', remark: '', dispositionMethod: 'SCRAP', details: [newDetail()]
})
async function load() {
  loading.value = true
  try {
    const ncrData = await qmsApi.listNonconformities()
    ncrs.value = ncrData
    if (!ncrData.some((ncr) => ncr.id === selectedDetailNcrId.value)) selectedDetailNcrId.value = ncrData[0]?.id ?? ''
  } catch {
    ElMessage.error('不良品处置单读取失败')
  } finally {
    loading.value = false
  }
}

async function searchWorkOrders(keyword: string) {
  const version = ++workOrderSearchVersion
  try {
    const page = await qmsApi.listWorkOrders({ keyword: keyword.trim() || undefined, page: 1, pageSize: 50 })
    if (version === workOrderSearchVersion) workOrderOptions.value = page.records
  } catch {
    if (version === workOrderSearchVersion) ElMessage.error('生产指令查询失败')
  }
}

async function loadDepartments() {
  try { departments.value = await qmsApi.listDepartmentOptions() }
  catch { ElMessage.warning('部门选项读取失败') }
}

async function loadScrapReasons() {
  try { scrapReasons.value = await qmsApi.listScrapReasonOptions() }
  catch { ElMessage.warning('报废原因选项读取失败') }
}

function selectProcess(code: string) {
  const process = processes.value.find((item) => item.processCode === code)
  manualForm.processName = process?.processName ?? ''
  manualForm.responsibleDepartment = responsibleDepartmentForProcess(process, code)
}

function openManualDialog() {
  const fqc = finishedTestProcess(processes.value)
  Object.assign(manualForm, {
    detectionDate: formatDate(new Date()), shiftName: '', processCode: fqc?.processCode ?? '', processName: fqc?.processName ?? '',
    responsibleDepartment: responsibleDepartmentForProcess(fqc, fqc?.processCode ?? ''), remark: '', dispositionMethod: 'SCRAP', details: [newDetail()]
  })
  manualDialog.value = true
  void searchWorkOrders('')
  void loadDepartments()
  void loadScrapReasons()
}

function openDetail(line: NonconformityDetail) {
  selectedLine.value = line
  detailForm.scrapQuantity = line.scrapQuantity
  detailForm.scrapReason = line.scrapReason ?? ''
  detailDialog.value = true
  void loadScrapReasons()
}

function reorderColumn(target: string) {
  if (!draggedColumn.value || draggedColumn.value === 'operation' || target === 'operation') return
  const from = ncrColumns.value.findIndex((column) => column.key === draggedColumn.value)
  const to = ncrColumns.value.findIndex((column) => column.key === target)
  if (from >= 0 && to >= 0) ncrColumns.value = moveColumn(ncrColumns.value, from, to)
  draggedColumn.value = ''
}

function resizeColumn(width: number, _oldWidth: number, column: { label: string }) {
  const entry = ncrColumns.value.find((item) => item.label === column.label)
  if (entry) entry.width = width
}

async function saveDetail() {
  if (!selectedDetailNcr.value || !selectedLine.value || !Number.isInteger(detailForm.scrapQuantity) || detailForm.scrapQuantity < 1) {
    ElMessage.warning('报废数量必须是大于 0 的整数')
    return
  }
  detailSaving.value = true
  try {
    await qmsApi.updateNonconformityDetail(selectedDetailNcr.value.id, selectedLine.value.id, {
      scrapQuantity: detailForm.scrapQuantity, scrapReason: detailForm.scrapReason.trim()
    })
    detailDialog.value = false
    await load()
    ElMessage.success('不良品明细已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '不良品明细更新失败')
  } finally {
    detailSaving.value = false
  }
}

function dispositionText(method?: string) {
  return ({ SCRAP: '报废', REWORK: '返工', CONCESSION: '让步接收' } as Record<string, string>)[method ?? 'SCRAP'] ?? method ?? '报废'
}

async function createManualNcr() {
  if (!manualForm.detectionDate || !manualForm.processCode.trim() || !manualForm.processName.trim()) {
    ElMessage.warning('请填写检测日期、工序编码和检验工序')
    return
  }
  if (!manualForm.details.length || manualForm.details.some((line) => !line.workOrderNo.trim() || line.scrapQuantity < 1)) {
    ElMessage.warning('每条明细都需要生产指令和大于 0 的报废数量')
    return
  }
  manualSaving.value = true
  try {
    const created = await qmsApi.createManualNonconformity({
      ...manualForm,
      processCode: manualForm.processCode.trim(),
      processName: manualForm.processName.trim(),
      details: manualForm.details.map((line) => ({ ...line, workOrderNo: line.workOrderNo.trim() }))
    })
    manualDialog.value = false
    selectedDetailNcrId.value = created.id
    await load()
    ElMessage.success(`已创建 ${created.ncrNo}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '手工新建不良品处置单失败')
  } finally {
    manualSaving.value = false
  }
}

async function approve(ncr: Nonconformity) {
  const badCount = ncr.details.reduce((sum, line) => sum + line.scrapQuantity, 0)
  try {
    await ElMessageBox.confirm(`确认审核 ${ncr.ncrNo} 的 ${badCount} 件不良品？`, '审核不良品处置单', {
      confirmButtonText: '确认审核', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  saving.value = true
  try {
    await qmsApi.approveDeviceDisposition(ncr.id, badCount)
    ElMessage.success('处置单已审核')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处置单审核失败')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  void loadMenuAccess()
  await load()
  try { processes.value = (await qmsApi.listProcesses()).filter((process) => process.status === 'ACTIVE') }
  catch { ElMessage.warning('工序选项读取失败') }
  await loadDepartments()
  await loadScrapReasons()
})
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never" class="content-card" v-loading="loading">
      <template #header><div class="card-header"><span>不良品处置单</span><div><el-button type="primary" @click="openManualDialog">新增</el-button><el-button link type="primary" @click="load">刷新</el-button></div></div></template>
      <el-table :key="ncrColumns.map(column => column.key).join(',')" :data="ncrs" empty-text="暂无不良品处置单" border max-height="600" @header-dragend="resizeColumn">
        <el-table-column v-for="column in ncrColumns" :key="column.key" :label="column.label" :width="column.width" :fixed="column.key === 'operation' ? 'right' : undefined" :show-overflow-tooltip="['remark', 'responsibleDepartment'].includes(column.key)">
          <template #header><span :draggable="column.key !== 'operation'" :title="column.key === 'operation' ? '' : '拖动表头调整顺序，拖动列边缘调整宽度'" @dragstart="draggedColumn = column.key" @dragover.prevent @drop.prevent="reorderColumn(column.key)" @dragend="draggedColumn = ''">{{ column.label }}</span></template>
          <template #default="scope">
            <template v-if="column.key === 'ncrNo'">{{ scope.row.ncrNo }}</template>
            <template v-else-if="column.key === 'detectionDate'">{{ formatDate(scope.row.detectionDate) }}</template>
            <template v-else-if="column.key === 'shiftName'">{{ scope.row.shiftName || '—' }}</template>
            <template v-else-if="column.key === 'processName'">{{ scope.row.processName || '—' }}</template>
            <template v-else-if="column.key === 'responsibleDepartment'">{{ scope.row.responsibleDepartment || '—' }}</template>
            <template v-else-if="column.key === 'remark'">{{ scope.row.remark || '—' }}</template>
            <template v-else-if="column.key === 'dispositionMethod'">{{ dispositionText(scope.row.dispositionMethod) }}</template>
            <template v-else-if="column.key === 'sourceType'">{{ scope.row.sourceType === 'DEVICE' ? '系统自动生成' : (scope.row.initiatedBy || '—') }}</template>
            <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(scope.row.createdAt) }}</template>
            <template v-else-if="column.key === 'reviewedBy'">{{ scope.row.reviewedBy || '—' }}</template>
            <template v-else-if="column.key === 'reviewedAt'">{{ formatDateTime(scope.row.reviewedAt) }}</template>
            <el-tag v-else-if="column.key === 'status'" :type="['APPROVED', 'CLOSED'].includes(scope.row.status) ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag>
            <template v-else-if="column.key === 'operation'"><el-button type="primary" link @click="selectedDetailNcrId = scope.row.id">查看明细</el-button><el-button v-if="canReview && scope.row.status === 'PENDING_REVIEW'" type="primary" link :disabled="saving" @click="approve(scope.row)">审核</el-button></template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>明细</span><span v-if="selectedDetailNcr" class="helper-text">{{ selectedDetailNcr.ncrNo }}</span></div></template>
      <paged-table :key="selectedDetailNcrId" :data="selectedDetailNcr?.details ?? []" empty-text="暂无不良品明细">
        <el-table-column prop="lineNo" label="行号" min-width="70" />
        <el-table-column prop="workOrderNo" label="生产指令" min-width="155" />
        <el-table-column prop="internalProductNo" label="厂内编号" min-width="135"><template #default="scope">{{ scope.row.internalProductNo || '—' }}</template></el-table-column>
        <el-table-column prop="processName" label="工序" min-width="125" />
        <el-table-column prop="unit" label="单位" min-width="75" />
        <el-table-column prop="singlePcsArea" label="单 PCS 面积" min-width="125"><template #default="scope">{{ scope.row.singlePcsArea ?? '—' }}</template></el-table-column>
        <el-table-column prop="scrapQuantity" label="报废数量" min-width="105" align="right" />
        <el-table-column prop="scrapArea" label="报废总面积" min-width="120" align="right"><template #default="scope">{{ scope.row.scrapArea ?? '—' }}</template></el-table-column>
        <el-table-column prop="scrapReason" label="报废原因" min-width="170" show-overflow-tooltip><template #default="scope">{{ scope.row.scrapReason || '—' }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column>
        <el-table-column label="状态" min-width="100"><template #default="scope">{{ statusText(scope.row.status) }}</template></el-table-column>
        <el-table-column label="操作" width="85" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">{{ canEditDetail(scope.row) ? '编辑' : '查看' }}</el-button></template></el-table-column>
      </paged-table>
    </el-card>

    <el-dialog v-model="detailDialog" title="不良品明细" width="min(650px, 94vw)">
      <el-descriptions v-if="selectedLine" :column="2" border>
        <el-descriptions-item label="行号">{{ selectedLine.lineNo }}</el-descriptions-item>
        <el-descriptions-item label="生产指令">{{ selectedLine.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="厂内编号">{{ selectedLine.internalProductNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ selectedLine.processName }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ selectedLine.unit }}</el-descriptions-item>
        <el-descriptions-item label="单 PCS 面积">{{ selectedLine.singlePcsArea ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="报废数量"><el-input-number v-if="detailEditable" v-model="detailForm.scrapQuantity" :min="1" :precision="0" /><template v-else>{{ selectedLine.scrapQuantity }}</template></el-descriptions-item>
        <el-descriptions-item label="报废总面积">{{ detailEditable ? calculatedScrapArea : (selectedLine.scrapArea ?? '—') }}</el-descriptions-item>
        <el-descriptions-item label="报废原因" :span="2"><el-select v-if="detailEditable" v-model="detailForm.scrapReason" filterable clearable placeholder="选择报废原因" class="full-width"><el-option v-for="reason in scrapReasons" :key="reason" :label="reason" :value="reason" /></el-select><template v-else>{{ selectedLine.scrapReason || '—' }}</template></el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedLine.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="detailEditable" #footer><el-button @click="detailDialog = false">取消</el-button><el-button type="primary" :loading="detailSaving" @click="saveDetail">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="manualDialog" title="新增不良品处置单" width="min(1120px, 94vw)" top="5vh" destroy-on-close>
      <el-form :model="manualForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="检测日期" required><el-date-picker v-model="manualForm.detectionDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
          <el-form-item label="班次"><el-input v-model="manualForm.shiftName" placeholder="如白班" /></el-form-item>
          <el-form-item label="工序编码" required><el-select v-model="manualForm.processCode" filterable placeholder="请选择工序" class="full-width" @change="selectProcess"><el-option v-for="process in processes" :key="process.id" :label="`${process.processCode} · ${process.processName}`" :value="process.processCode" /></el-select></el-form-item>
          <el-form-item label="检验工序" required><el-input v-model="manualForm.processName" readonly /></el-form-item>
          <el-form-item label="责任部门"><el-select v-model="manualForm.responsibleDepartment" filterable clearable placeholder="请选择责任部门" class="full-width"><el-option v-for="department in departments" :key="department" :label="department" :value="department" /></el-select></el-form-item>
          <el-form-item label="处置方式"><el-select v-model="manualForm.dispositionMethod" class="full-width"><el-option label="报废" value="SCRAP" /><el-option label="返工" value="REWORK" /><el-option label="让步接收" value="CONCESSION" /></el-select></el-form-item>
          <el-form-item label="备注"><el-input v-model="manualForm.remark" /></el-form-item>
        </div>
        <div class="item-editor-header"><strong>明细行</strong><el-button link type="primary" @click="manualForm.details.push(newDetail())">+ 添加明细</el-button></div>
        <div class="ncr-detail-editor-scroll">
          <div class="ncr-detail-editor ncr-detail-editor-header"><span>行号</span><span>生产指令号 *</span><span>单位</span><span>报废数量 *</span><span>报废原因</span><span>备注</span><span>操作</span></div>
          <div v-for="(line, index) in manualForm.details" :key="index" class="ncr-detail-editor">
            <strong>{{ index + 1 }}</strong>
            <el-select v-model="line.workOrderNo" filterable remote :remote-method="searchWorkOrders" placeholder="选择生产指令号 *"><el-option v-for="order in workOrderOptions" :key="order.id" :label="`${order.workOrderNo} · ${order.internalProductNo || '无厂内编号'}`" :value="order.workOrderNo" /></el-select>
            <el-input v-model="line.unit" placeholder="单位" />
            <el-input-number v-model="line.scrapQuantity" :min="1" class="full-width" />
            <el-select v-model="line.scrapReason" filterable clearable placeholder="选择报废原因"><el-option v-for="reason in scrapReasons" :key="reason" :label="reason" :value="reason" /></el-select>
            <el-input v-model="line.remark" placeholder="备注" />
            <el-button link type="danger" :disabled="manualForm.details.length === 1" @click="manualForm.details.splice(index, 1)">移除</el-button>
          </div>
        </div>
        <p class="helper-text">厂内编号和单 PCS 面积从生产指令读取，报废总面积按单 PCS 面积 × 报废数量计算。</p>
      </el-form>
      <template #footer><el-button @click="manualDialog = false">取消</el-button><el-button type="primary" :loading="manualSaving" @click="createManualNcr">保存处置单</el-button></template>
    </el-dialog>

  </section>
</template>
