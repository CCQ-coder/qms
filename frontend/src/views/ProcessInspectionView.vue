<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { createClientRequestId } from '@/offline/inspectionDraftStore'
import { formatDateTime, statusText, taskTypeText } from '@/utils/display-text'
import type { InspectionTask, InspectionTemplate, ProcessInspectionRecord } from '@/types/qms'

type InspectionTab = 'pending' | 'completed'

const loading = ref(false)
const activeTab = ref<InspectionTab>('pending')
const pendingTasks = ref<InspectionTask[]>([])
const completedRecords = ref<ProcessInspectionRecord[]>([])
const keyword = ref('')
const processKeyword = ref('')
const assigneeKeyword = ref('')
const dueRange = ref<string[]>([])
const completedDateRange = ref<string[]>([])
const completedRecordNo = ref('')
const completedWorkOrderNo = ref('')
const completedInternalProductNo = ref('')
const completedPcbPartNo = ref('')
const completedVerdict = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const recordDialogVisible = ref(false)
const selectedTask = ref<InspectionTask>()
const selectedTemplate = ref<InspectionTemplate>()
const templateLoading = ref(false)
const saving = ref(false)
const recordFormRef = ref<FormInstance>()
const recordForm = reactive({
  verdict: 'PASS',
  defectiveQuantity: 0,
  defectSummary: '',
  defectLocation: '',
  measurement: '',
  lowerLimit: '',
  upperLimit: '',
})
const recordValues = reactive<Record<string, string | number | undefined>>({})
const completedDetailVisible = ref(false)
const selectedCompletedRecord = ref<ProcessInspectionRecord>()

const recordRules: FormRules = {
  verdict: [{ required: true, message: '请选择检验结论', trigger: 'change' }],
  defectiveQuantity: [{ required: true, type: 'number', min: 0, message: '请输入不良数量', trigger: 'change' }],
}

function isProcessTask(task: InspectionTask) {
  if (task.taskType === 'PROCESS') return true
  return !/FQC|成品|测试/i.test(`${task.processCode} ${task.processName} ${task.taskType}`)
}

const processOptions = computed(() => Array.from(new Set(
  [...pendingTasks.value.filter(isProcessTask), ...completedRecords.value]
    .map((task) => task.processName)
    .filter(Boolean),
)).sort())

const filteredPendingRows = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return pendingTasks.value.filter(isProcessTask).filter((task) => {
    const dueDate = task.dueAt?.slice(0, 10) ?? ''
    if (dueRange.value[0] && dueDate && dueDate < dueRange.value[0]) return false
    if (dueRange.value[1] && dueDate && dueDate > dueRange.value[1]) return false
    if (processKeyword.value && task.processName !== processKeyword.value) return false
    if (assigneeKeyword.value.trim() && !String(task.assigneeName ?? '').toLowerCase().includes(assigneeKeyword.value.trim().toLowerCase())) return false
    if (!normalizedKeyword) return true
    return [task.taskNo, task.workOrderNo, task.batchNo, task.processCode, task.processName]
      .some((value) => String(value ?? '').toLowerCase().includes(normalizedKeyword))
  })
})

const filteredCompletedRows = computed(() => {
  const dateFrom = completedDateRange.value[0]
  const dateTo = completedDateRange.value[1]
  const recordNo = completedRecordNo.value.trim().toLowerCase()
  const workOrderNo = completedWorkOrderNo.value.trim().toLowerCase()
  const internalProductNo = completedInternalProductNo.value.trim().toLowerCase()
  const pcbPartNo = completedPcbPartNo.value.trim().toLowerCase()
  return completedRecords.value.filter((record) => {
    const inspectedDate = record.inspectedAt?.slice(0, 10) ?? ''
    if (dateFrom && inspectedDate && inspectedDate < dateFrom) return false
    if (dateTo && inspectedDate && inspectedDate > dateTo) return false
    if (recordNo && !record.taskNo.toLowerCase().includes(recordNo)) return false
    if (workOrderNo && !record.workOrderNo.toLowerCase().includes(workOrderNo)) return false
    if (internalProductNo && !String(record.internalProductNo ?? '').toLowerCase().includes(internalProductNo)) return false
    if (pcbPartNo && !String(record.pcbPartNo ?? '').toLowerCase().includes(pcbPartNo)) return false
    if (completedVerdict.value && record.verdict !== completedVerdict.value) return false
    return true
  })
})

const totalRows = computed(() => activeTab.value === 'pending' ? filteredPendingRows.value.length : filteredCompletedRows.value.length)
const paginatedPendingRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredPendingRows.value.slice(start, start + pageSize.value)
})
const paginatedCompletedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredCompletedRows.value.slice(start, start + pageSize.value)
})

const pageStart = computed(() => totalRows.value ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, totalRows.value))
const pendingProcessCount = computed(() => pendingTasks.value.filter(isProcessTask).length)
const completedProcessCount = computed(() => completedRecords.value.length)
const assignedCount = computed(() => pendingTasks.value.filter((task) => isProcessTask(task) && task.assigneeName).length)
const latestDueAt = computed(() => pendingTasks.value.filter(isProcessTask).map((task) => task.dueAt).filter(Boolean).sort().at(-1))

function taskStatusType(status: string): 'success' | 'warning' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

async function loadTasks() {
  loading.value = true
  try {
    const [pending, completed] = await Promise.all([
      qmsApi.listInspectionTasks('PENDING'),
      qmsApi.listCompletedProcessInspectionRecords(),
    ])
    pendingTasks.value = pending
    completedRecords.value = completed
  } catch {
    ElMessage.error('工序检验任务读取失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  processKeyword.value = ''
  assigneeKeyword.value = ''
  dueRange.value = []
  currentPage.value = 1
}

function resetCompletedFilters() {
  completedDateRange.value = []
  completedRecordNo.value = ''
  completedWorkOrderNo.value = ''
  completedInternalProductNo.value = ''
  completedPcbPartNo.value = ''
  completedVerdict.value = ''
  currentPage.value = 1
}

function openCompletedDetail(record: ProcessInspectionRecord) {
  selectedCompletedRecord.value = record
  completedDetailVisible.value = true
}

function completedResultItems(record?: ProcessInspectionRecord) {
  if (!record) return []
  return Object.entries(record.results).map(([itemCode, rawValue]) => {
    const item = rawValue && typeof rawValue === 'object' && !Array.isArray(rawValue)
      ? rawValue as Record<string, unknown> : undefined
    return {
      itemCode,
      itemName: String(item?.itemName ?? itemCode),
      value: item?.value ?? rawValue,
      range: item && (item.lowerLimit !== undefined || item.upperLimit !== undefined)
        ? `${item.lowerLimit ?? '—'} ~ ${item.upperLimit ?? '—'}${item.unit ? ` ${item.unit}` : ''}` : '—',
    }
  })
}

function detailValue(value: unknown) {
  if (value === undefined || value === null || value === '') return '—'
  if (value === true) return '是'
  if (value === false) return '否'
  return String(value)
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
}

function resetRecordForm() {
  Object.assign(recordForm, {
    verdict: 'PASS',
    defectiveQuantity: 0,
    defectSummary: '',
    defectLocation: '',
    measurement: '',
    lowerLimit: '',
    upperLimit: '',
  })
  Object.keys(recordValues).forEach((key) => delete recordValues[key])
}

async function openRecord(task: InspectionTask) {
  selectedTask.value = task
  selectedTemplate.value = undefined
  resetRecordForm()
  recordDialogVisible.value = true
  if (!task.templateId) return

  templateLoading.value = true
  try {
    selectedTemplate.value = await qmsApi.getInspectionTemplate(task.templateId)
    selectedTemplate.value.items.forEach((item) => { recordValues[item.itemCode] = '' })
  } catch {
    ElMessage.warning('未能读取检验模板，将使用通用检验记录。')
  } finally {
    templateLoading.value = false
  }
}

function displayRange(lowerLimit?: number, upperLimit?: number, unit?: string) {
  if (lowerLimit === undefined && upperLimit === undefined) return ''
  return `${lowerLimit ?? '—'} ~ ${upperLimit ?? '—'}${unit ? ` ${unit}` : ''}`
}

function dynamicResults() {
  if (!selectedTemplate.value) {
    return {
      measurement: recordForm.measurement,
      lowerLimit: recordForm.lowerLimit,
      upperLimit: recordForm.upperLimit,
    }
  }
  return Object.fromEntries(selectedTemplate.value.items.map((item) => [item.itemCode, {
    itemName: item.itemName,
    value: recordValues[item.itemCode] ?? '',
    lowerLimit: item.lowerLimit,
    upperLimit: item.upperLimit,
    unit: item.unit,
  }]))
}

function exceededNumberItems() {
  return selectedTemplate.value?.items.filter((item) => {
    if (item.inputType !== 'NUMBER') return false
    const rawValue = recordValues[item.itemCode]
    if (rawValue === undefined || rawValue === '') return false
    const value = Number(rawValue)
    return Number.isFinite(value) && ((item.lowerLimit !== undefined && value < item.lowerLimit)
      || (item.upperLimit !== undefined && value > item.upperLimit))
  }) ?? []
}

async function offerNonconformity(verdict: string, inspectionRecordId: string, ncrNo?: string) {
  if (verdict !== 'FAIL' || ncrNo) return ncrNo
  try {
    await ElMessageBox.confirm('当前未开启自动建单。是否立即为本条不合格记录发起不良品处置单？', '发起不良品处置', {
      confirmButtonText: '立即发起',
      cancelButtonText: '稍后处理',
      type: 'warning',
    })
    return (await qmsApi.createNonconformity(inspectionRecordId)).ncrNo
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.warning('检验已提交，但不良品处置单创建失败。')
    return undefined
  }
}

async function submitRecord() {
  const valid = await recordFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedTask.value) return

  const missingItem = selectedTemplate.value?.items.find((item) => item.required && !String(recordValues[item.itemCode] ?? '').trim())
  if (missingItem) {
    ElMessage.warning(`请填写必填检验项：${missingItem.itemName}`)
    return
  }

  const exceededItems = exceededNumberItems()
  if (recordForm.verdict === 'PASS' && exceededItems.length) {
    recordForm.verdict = 'FAIL'
    ElMessage.warning(`“${exceededItems.map((item) => item.itemName).join('、')}”超出控制范围，已自动切换为不合格。`)
  }

  saving.value = true
  try {
    const result = await qmsApi.submitInspectionRecord(selectedTask.value.id, {
      verdict: recordForm.verdict,
      defectiveQuantity: recordForm.defectiveQuantity,
      defectSummary: recordForm.defectSummary,
      defectLocation: recordForm.defectLocation,
      results: dynamicResults(),
      clientRequestId: createClientRequestId(),
    })
    recordDialogVisible.value = false
    const ncrNo = await offerNonconformity(recordForm.verdict, result.id, result.ncrNo)
    ElMessage.success(ncrNo ? `工序检验已提交，已创建 ${ncrNo}` : '工序检验已提交')
    await loadTasks()
  } catch {
    ElMessage.error('工序检验提交失败，请检查网络后重试。')
  } finally {
    saving.value = false
  }
}

watch([activeTab, keyword, processKeyword, assigneeKeyword, dueRange, completedDateRange, completedRecordNo, completedWorkOrderNo, completedInternalProductNo, completedPcbPartNo, completedVerdict], () => {
  currentPage.value = 1
})

watch(totalRows, (value) => {
  const lastPage = Math.max(1, Math.ceil(value / pageSize.value))
  if (currentPage.value > lastPage) currentPage.value = lastPage
})

onMounted(() => { void loadTasks() })
</script>

<template>
  <section class="process-inspection-page" v-loading="loading">
    <section class="process-inspection-hero">
      <div>
        <p class="eyebrow">制程质量控制</p>
        <h2>工序检验</h2>
        <p>设备检测或现场首检完成后，在此核对生产指令、填写检验结果，并将不合格项转入处置流程。</p>
      </div>
      <div class="process-inspection-hero-actions">
        <span class="process-sync"><i></i>任务数据已同步</span>
        <el-button plain @click="loadTasks">刷新任务</el-button>
      </div>
    </section>

    <section class="process-flow-card">
      <div class="process-flow-item"><b>1</b><span>设备检测完成</span></div>
      <div class="process-flow-item"><b>2</b><span>核对生产指令</span></div>
      <div class="process-flow-item"><b>3</b><span>工序检验确认</span></div>
      <div class="process-flow-item"><b>4</b><span>合格完成 / 不合格推送处置</span></div>
      <div class="process-flow-item"><b>5</b><span>ERP 接收加工数据</span></div>
    </section>

    <el-card class="content-card process-inspection-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="pending">
          <template #label>工序待检 <el-badge :value="pendingProcessCount" :hidden="!pendingProcessCount" class="process-tab-badge" /></template>
        </el-tab-pane>
        <el-tab-pane name="completed" label="工序已检" />
      </el-tabs>

      <div class="process-summary-grid">
        <div><span>待检数量</span><strong class="attention">{{ pendingProcessCount }}</strong></div>
        <div><span>已检数量</span><strong class="success">{{ completedProcessCount }}</strong></div>
        <div><span>已分配检验员</span><strong>{{ assignedCount }}</strong></div>
        <div><span>最近计划检验</span><strong class="date">{{ formatDateTime(latestDueAt) }}</strong></div>
      </div>

      <template v-if="activeTab === 'pending'">
        <p class="process-tab-intro">工序待检任务将在核对生产指令与批次后进入检验确认；完成检验后自动转入“工序已检”。</p>
        <div class="process-filter-grid">
          <label class="process-filter-field process-filter-field--wide">
            <span>快速查询</span>
            <el-input v-model="keyword" clearable placeholder="任务号、生产指令、批次、工序编号" @keyup.enter="currentPage = 1" />
          </label>
          <label class="process-filter-field">
            <span>计划检验日期</span>
            <el-date-picker v-model="dueRange" type="daterange" unlink-panels value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
          </label>
          <label class="process-filter-field">
            <span>工序</span>
            <el-select v-model="processKeyword" clearable placeholder="全部">
              <el-option v-for="item in processOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </label>
          <label class="process-filter-field">
            <span>检验人员</span>
            <el-input v-model="assigneeKeyword" clearable placeholder="输入检验人员" />
          </label>
          <div class="process-filter-actions">
            <el-button @click="resetFilters">重置</el-button>
            <el-button type="primary" @click="currentPage = 1">查询</el-button>
          </div>
        </div>

        <div class="process-table-heading">
          <span>共查询到 <strong>{{ totalRows }}</strong> 条待检任务</span>
          <small>双击待检任务可直接进入检验确认。</small>
        </div>
        <div class="process-table-wrap">
          <el-table :data="paginatedPendingRows" stripe empty-text="暂无工序待检任务" @row-dblclick="(row: InspectionTask) => openRecord(row)">
            <el-table-column prop="taskNo" label="检验编号" min-width="150" />
            <el-table-column label="计划检验时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.dueAt) }}</template></el-table-column>
            <el-table-column prop="processName" label="工序" min-width="125" />
            <el-table-column prop="workOrderNo" label="生产指令" min-width="135" />
            <el-table-column prop="batchNo" label="生产批次" min-width="145"><template #default="scope">{{ scope.row.batchNo || '—' }}</template></el-table-column>
            <el-table-column label="检验类型" min-width="115"><template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template></el-table-column>
            <el-table-column prop="assigneeName" label="检验人员" min-width="115"><template #default="scope">{{ scope.row.assigneeName || '待分配' }}</template></el-table-column>
            <el-table-column label="状态" min-width="105"><template #default="scope"><el-tag :type="taskStatusType(scope.row.status)" size="small">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="126" fixed="right"><template #default="scope"><el-button link type="primary" @click="openRecord(scope.row)">检验确认</el-button></template></el-table-column>
          </el-table>
        </div>
      </template>

      <template v-else>
        <p class="process-tab-intro">工序检验单保留生产指令归属、适用检验方案、逐项结果、良品数和不良品数；可打开查看完整检验明细。</p>
        <div class="process-completed-filter-grid">
          <label class="process-filter-field"><span>检验日期</span><el-date-picker v-model="completedDateRange" type="daterange" unlink-panels value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" /></label>
          <label class="process-filter-field"><span>检验单号</span><el-input v-model="completedRecordNo" clearable placeholder="输入检验单号" /></label>
          <label class="process-filter-field"><span>生产指令</span><el-input v-model="completedWorkOrderNo" clearable placeholder="输入生产指令号" /></label>
          <label class="process-filter-field"><span>厂内编号</span><el-input v-model="completedInternalProductNo" clearable placeholder="输入厂内编号" /></label>
          <label class="process-filter-field"><span>PCB料号</span><el-input v-model="completedPcbPartNo" clearable placeholder="输入PCB料号" /></label>
          <label class="process-filter-field"><span>检验结果</span><el-select v-model="completedVerdict" clearable placeholder="全部"><el-option label="合格" value="PASS" /><el-option label="不合格" value="FAIL" /></el-select></label>
          <div class="process-filter-actions"><el-button @click="resetCompletedFilters">重置</el-button><el-button type="primary" @click="currentPage = 1">查询</el-button></div>
        </div>

        <div class="process-table-heading">
          <span>共查询到 <strong>{{ totalRows }}</strong> 条工序已检数据</span>
          <small>检验结果和后续处置状态均来自已提交的检验记录。</small>
        </div>
        <div class="process-table-wrap">
          <el-table :data="paginatedCompletedRows" stripe empty-text="暂无工序已检记录">
            <el-table-column prop="taskNo" label="工序检验单号" min-width="155" />
            <el-table-column label="检验时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.inspectedAt) }}</template></el-table-column>
            <el-table-column label="PCB料号" min-width="135"><template #default="scope">{{ scope.row.pcbPartNo || '—' }}</template></el-table-column>
            <el-table-column prop="workOrderNo" label="生产指令" min-width="135" />
            <el-table-column label="厂内编号" min-width="145"><template #default="scope">{{ scope.row.internalProductNo || '—' }}</template></el-table-column>
            <el-table-column prop="inspectionQuantity" label="检验数量" min-width="100" align="right" />
            <el-table-column label="良品" min-width="90" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodQuantity }}</span></template></el-table-column>
            <el-table-column label="不良品" min-width="95" align="right"><template #default="scope"><span :class="scope.row.defectiveQuantity ? 'bad-text' : 'good-text'">{{ scope.row.defectiveQuantity }}</span></template></el-table-column>
            <el-table-column label="检验方案" min-width="220" show-overflow-tooltip><template #default="scope">{{ scope.row.templateName }}{{ scope.row.templateVersionNo ? ` | V${scope.row.templateVersionNo}` : '' }}</template></el-table-column>
            <el-table-column label="检验员" min-width="110"><template #default="scope">{{ scope.row.inspectedBy || '—' }}</template></el-table-column>
            <el-table-column label="结果" min-width="90"><template #default="scope"><el-tag :type="scope.row.verdict === 'PASS' ? 'success' : 'danger'" size="small">{{ statusText(scope.row.verdict) }}</el-tag></template></el-table-column>
            <el-table-column label="后续处理" min-width="130"><template #default="scope"><span :class="scope.row.verdict === 'FAIL' ? 'bad-text' : 'process-completed-text'">{{ scope.row.followUpStatus }}</span></template></el-table-column>
            <el-table-column label="操作" width="90" fixed="right"><template #default="scope"><el-button link type="primary" @click="openCompletedDetail(scope.row)">查看</el-button></template></el-table-column>
          </el-table>
        </div>
      </template>

      <div class="process-pagination">
        <span>显示第 {{ pageStart }}–{{ pageEnd }} 条，共 {{ totalRows }} 条</span>
        <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="totalRows" background layout="sizes, prev, pager, next, jumper" @size-change="handlePageSizeChange" />
      </div>
    </el-card>

    <el-dialog v-model="completedDetailVisible" title="工序检验单明细" width="min(980px, 94vw)">
      <template v-if="selectedCompletedRecord">
        <el-descriptions :column="3" border class="process-record-descriptions">
          <el-descriptions-item label="工序检验单号">{{ selectedCompletedRecord.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="检验时间">{{ formatDateTime(selectedCompletedRecord.inspectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="检验员">{{ selectedCompletedRecord.inspectedBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="生产指令">{{ selectedCompletedRecord.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ selectedCompletedRecord.processName }}</el-descriptions-item>
          <el-descriptions-item label="生产批次">{{ selectedCompletedRecord.batchNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="检验数量">{{ selectedCompletedRecord.inspectionQuantity }}</el-descriptions-item>
          <el-descriptions-item label="良品 / 不良品"><span class="good-text">{{ selectedCompletedRecord.goodQuantity }}</span> / <span class="bad-text">{{ selectedCompletedRecord.defectiveQuantity }}</span></el-descriptions-item>
          <el-descriptions-item label="检验结论"><el-tag :type="selectedCompletedRecord.verdict === 'PASS' ? 'success' : 'danger'">{{ statusText(selectedCompletedRecord.verdict) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="检验方案" :span="2">{{ selectedCompletedRecord.templateName }}{{ selectedCompletedRecord.templateVersionNo ? ` · V${selectedCompletedRecord.templateVersionNo}` : '' }}</el-descriptions-item>
          <el-descriptions-item label="后续处理">{{ selectedCompletedRecord.followUpStatus }}{{ selectedCompletedRecord.ncrNo ? ` · ${selectedCompletedRecord.ncrNo}` : '' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">检验项目</el-divider>
        <el-table v-if="completedResultItems(selectedCompletedRecord).length" :data="completedResultItems(selectedCompletedRecord)" size="small" class="process-detail-table">
          <el-table-column prop="itemName" label="检验项目" min-width="180" />
          <el-table-column label="检验结果" min-width="180"><template #default="scope">{{ detailValue(scope.row.value) }}</template></el-table-column>
          <el-table-column prop="range" label="控制范围" min-width="170" />
        </el-table>
        <el-empty v-else description="该检验单未填写逐项检验结果" :image-size="72" />

        <template v-if="selectedCompletedRecord.verdict === 'FAIL'">
          <el-divider content-position="left">不良信息</el-divider>
          <div class="process-defect-detail"><div><span>缺陷说明</span><strong>{{ selectedCompletedRecord.defectSummary || '未填写' }}</strong></div><div><span>缺陷位置</span><strong>{{ selectedCompletedRecord.defectLocation || '未填写' }}</strong></div></div>
        </template>
      </template>
      <template #footer><el-button type="primary" @click="completedDetailVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="recordDialogVisible" title="工序检验确认" width="min(920px, 94vw)" :close-on-click-modal="false">
      <el-alert :title="selectedTask ? `${selectedTask.taskNo} · ${selectedTask.processName} · ${selectedTask.workOrderNo}` : '工序检验'" type="info" :closable="false" class="dialog-alert" />
      <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-position="top" v-loading="templateLoading">
        <div class="form-grid">
          <el-form-item label="检验结论" prop="verdict"><el-radio-group v-model="recordForm.verdict"><el-radio-button value="PASS">合格</el-radio-button><el-radio-button value="FAIL">不合格</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="不良数量" prop="defectiveQuantity"><el-input-number v-model="recordForm.defectiveQuantity" :min="0" class="full-width" /></el-form-item>
        </div>
        <template v-if="selectedTemplate">
          <el-divider content-position="left">{{ selectedTemplate.name }} · V{{ selectedTemplate.versionNo }}</el-divider>
          <div class="form-grid">
            <el-form-item v-for="item in selectedTemplate.items" :key="item.itemCode" :label="`${item.itemName}${item.required ? ' *' : ''}`">
              <el-input v-if="item.inputType === 'TEXT'" v-model="recordValues[item.itemCode]" :placeholder="item.helpText || '填写检验结果'" />
              <el-input-number v-else-if="item.inputType === 'NUMBER'" v-model="recordValues[item.itemCode]" class="full-width" :placeholder="item.helpText" />
              <el-select v-else-if="item.inputType === 'SELECT'" v-model="recordValues[item.itemCode]" class="full-width" :placeholder="item.helpText || '请选择'">
                <el-option v-for="option in item.options" :key="option" :label="option" :value="option" />
              </el-select>
              <el-radio-group v-else v-model="recordValues[item.itemCode]"><el-radio-button value="true">是</el-radio-button><el-radio-button value="false">否</el-radio-button></el-radio-group>
              <div v-if="displayRange(item.lowerLimit, item.upperLimit, item.unit) || item.helpText" class="field-help">
                <span v-if="displayRange(item.lowerLimit, item.upperLimit, item.unit)">控制范围：{{ displayRange(item.lowerLimit, item.upperLimit, item.unit) }}</span>
                <span v-if="item.helpText">{{ item.helpText }}</span>
              </div>
            </el-form-item>
          </div>
        </template>
        <div v-else class="form-grid">
          <el-form-item label="测量值"><el-input v-model="recordForm.measurement" placeholder="例如：1.58 mm" /></el-form-item>
          <el-form-item label="规格上下限"><div class="range-input"><el-input v-model="recordForm.lowerLimit" placeholder="下限" /><span>至</span><el-input v-model="recordForm.upperLimit" placeholder="上限" /></div></el-form-item>
        </div>
        <template v-if="recordForm.verdict === 'FAIL'">
          <el-form-item label="缺陷说明"><el-input v-model="recordForm.defectSummary" type="textarea" :rows="3" placeholder="请填写缺陷类型与处置建议" /></el-form-item>
          <el-form-item label="缺陷位置"><el-input v-model="recordForm.defectLocation" placeholder="例如：A 面左上角、板号 3、孔位 J12" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="recordDialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRecord">提交检验</el-button></template>
    </el-dialog>
  </section>
</template>
