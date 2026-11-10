<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { loadMenuAccess, menuAccess } from '@/navigation/menu'
import { formatDate, formatDateTime, statusText } from '@/utils/display-text'
import type { ManualNonconformityInput, Nonconformity, NonconformityDetail, ProcessMaster, WorkOrder } from '@/types/qms'

const loading = ref(false)
const ncrs = ref<Nonconformity[]>([])
const saving = ref(false)
const processes = ref<ProcessMaster[]>([])
const workOrderOptions = ref<WorkOrder[]>([])
let workOrderSearchVersion = 0
const canReview = computed(() => ['PLATFORM_ADMIN', 'TENANT_ADMIN', 'SUPERVISOR'].includes(menuAccess.value?.roleCode ?? ''))
const manualDialog = ref(false)
const manualSaving = ref(false)
const selectedDetailNcrId = ref('')
const selectedLine = ref<NonconformityDetail>()
const detailDialog = ref(false)
const selectedDetailNcr = computed(() => ncrs.value.find((ncr) => ncr.id === selectedDetailNcrId.value))
const newDetail = () => ({ workOrderNo: '', unit: 'PCS', scrapQuantity: 1, scrapReason: '', remark: '' })
const manualForm = reactive<ManualNonconformityInput>({
  detectionDate: formatDate(new Date()), shiftName: '', processCode: '', processName: '',
  responsibleDepartment: '', remark: '', dispositionMethod: 'SCRAP', batchNo: '', details: [newDetail()]
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
  if (!keyword.trim()) { workOrderOptions.value = []; return }
  try {
    const page = await qmsApi.listWorkOrders({ keyword: keyword.trim(), page: 1, pageSize: 50 })
    if (version === workOrderSearchVersion) workOrderOptions.value = page.records
  } catch {
    if (version === workOrderSearchVersion) ElMessage.error('生产指令查询失败')
  }
}

function selectProcess(code: string) {
  manualForm.processName = processes.value.find((process) => process.processCode === code)?.processName ?? ''
}

function openManualDialog() {
  Object.assign(manualForm, {
    detectionDate: formatDate(new Date()), shiftName: '', processCode: '', processName: '',
    responsibleDepartment: '', remark: '', dispositionMethod: 'SCRAP', batchNo: '', details: [newDetail()]
  })
  manualDialog.value = true
  void searchWorkOrders('')
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
})
</script>

<template>
  <section class="page-stack">
    <el-card shadow="never" class="content-card" v-loading="loading">
      <template #header><div class="card-header"><span>不良品处置单</span><div><el-button type="primary" @click="openManualDialog">新增</el-button><el-button link type="primary" @click="load">刷新</el-button></div></div></template>
      <paged-table :data="ncrs" empty-text="暂无不良品处置单">
        <el-table-column prop="ncrNo" label="单据编号" min-width="180" />
        <el-table-column label="检测日期" min-width="120"><template #default="scope">{{ formatDate(scope.row.detectionDate) }}</template></el-table-column>
        <el-table-column prop="shiftName" label="班次" min-width="90"><template #default="scope">{{ scope.row.shiftName || '—' }}</template></el-table-column>
        <el-table-column prop="processName" label="检验工序" min-width="140"><template #default="scope">{{ scope.row.processName || '—' }}</template></el-table-column>
        <el-table-column prop="responsibleDepartment" label="责任部门" min-width="130"><template #default="scope">{{ scope.row.responsibleDepartment || '—' }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="170" show-overflow-tooltip><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column>
        <el-table-column label="处置方式" min-width="110"><template #default="scope">{{ dispositionText(scope.row.dispositionMethod) }}</template></el-table-column>
        <el-table-column label="创建来源" min-width="130"><template #default="scope">{{ scope.row.sourceType === 'DEVICE' ? '系统自动生成' : (scope.row.initiatedBy || '—') }}</template></el-table-column>
        <el-table-column label="创建日期" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="审核人" min-width="110"><template #default="scope">{{ scope.row.reviewedBy || '—' }}</template></el-table-column>
        <el-table-column label="审核时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.reviewedAt) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="scope"><el-tag :type="['APPROVED', 'CLOSED'].includes(scope.row.status) ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="175" fixed="right"><template #default="scope"><el-button type="primary" link @click="selectedDetailNcrId = scope.row.id">查看明细</el-button><el-button v-if="canReview && scope.row.status === 'PENDING_REVIEW'" type="primary" link :disabled="saving" @click="approve(scope.row)">审核</el-button></template></el-table-column>
      </paged-table>
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
        <el-table-column label="操作" width="85" fixed="right"><template #default="scope"><el-button link type="primary" @click="selectedLine = scope.row; detailDialog = true">查看</el-button></template></el-table-column>
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
        <el-descriptions-item label="报废数量">{{ selectedLine.scrapQuantity }}</el-descriptions-item>
        <el-descriptions-item label="报废总面积">{{ selectedLine.scrapArea ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="报废原因" :span="2">{{ selectedLine.scrapReason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedLine.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="manualDialog" title="新增不良品处置单" width="min(1120px, 94vw)" top="5vh" destroy-on-close>
      <el-form :model="manualForm" label-position="top">
        <div class="form-grid">
          <el-form-item label="检测日期" required><el-date-picker v-model="manualForm.detectionDate" type="date" value-format="YYYY-MM-DD" class="full-width" /></el-form-item>
          <el-form-item label="班次"><el-input v-model="manualForm.shiftName" placeholder="如白班" /></el-form-item>
          <el-form-item label="工序编码" required><el-select v-model="manualForm.processCode" filterable placeholder="请选择工序" class="full-width" @change="selectProcess"><el-option v-for="process in processes" :key="process.id" :label="`${process.processCode} · ${process.processName}`" :value="process.processCode" /></el-select></el-form-item>
          <el-form-item label="检验工序" required><el-input v-model="manualForm.processName" readonly /></el-form-item>
          <el-form-item label="责任部门"><el-input v-model="manualForm.responsibleDepartment" /></el-form-item>
          <el-form-item label="生产批次"><el-input v-model="manualForm.batchNo" /></el-form-item>
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
            <el-input v-model="line.scrapReason" placeholder="报废原因" />
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
