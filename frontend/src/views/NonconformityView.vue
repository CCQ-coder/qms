<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDate, formatDateTime, statusText } from '@/utils/display-text'
import type { Capa, ManualNonconformityInput, Nonconformity, NonconformityDetail } from '@/types/qms'

const loading = ref(false)
const ncrs = ref<Nonconformity[]>([])
const capas = ref<Capa[]>([])
const createDialog = ref(false)
const closeDialog = ref(false)
const selectedNcr = ref<Nonconformity>()
const selectedCapa = ref<Capa>()
const capaFormRef = ref<FormInstance>()
const closeFormRef = ref<FormInstance>()
const saving = ref(false)
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
const capaForm = reactive({ problemDescription: '', teamMembers: '', containmentAction: '', rootCause: '', correctiveAction: '', preventiveAction: '' })
const closeForm = reactive({ verificationResult: '', recognitionNotes: '' })
const capaRules: FormRules = { problemDescription: [{ required: true, message: '请输入问题描述', trigger: 'blur' }] }
const closeRules: FormRules = { verificationResult: [{ required: true, message: '请输入验证结论', trigger: 'blur' }] }

async function load() {
  loading.value = true
  try {
    const [ncrData, capaData] = await Promise.all([
      qmsApi.listNonconformities(),
      qmsApi.listCapas()
    ])
    ncrs.value = ncrData
    capas.value = capaData
    if (!ncrData.some((ncr) => ncr.id === selectedDetailNcrId.value)) selectedDetailNcrId.value = ncrData[0]?.id ?? ''
  } catch {
    ElMessage.error('不良品与 CAPA 数据读取失败')
  } finally {
    loading.value = false
  }
}

function openManualDialog() {
  Object.assign(manualForm, {
    detectionDate: formatDate(new Date()), shiftName: '', processCode: '', processName: '',
    responsibleDepartment: '', remark: '', dispositionMethod: 'SCRAP', batchNo: '', details: [newDetail()]
  })
  manualDialog.value = true
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

function openCreateCapa(ncr: Nonconformity) {
  selectedNcr.value = ncr
  Object.assign(capaForm, { problemDescription: '', teamMembers: '', containmentAction: '', rootCause: '', correctiveAction: '', preventiveAction: '' })
  createDialog.value = true
}

async function createCapa() {
  const valid = await capaFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedNcr.value) return
  saving.value = true
  try {
    await qmsApi.createCapa(selectedNcr.value.id, capaForm)
    ElMessage.success('CAPA 已创建，等待主管验证关闭')
    createDialog.value = false
    await load()
  } catch {
    ElMessage.error('CAPA 创建失败；请确认该不良单尚未关联 CAPA。')
  } finally {
    saving.value = false
  }
}

function openCloseCapa(capa: Capa) {
  selectedCapa.value = capa
  Object.assign(closeForm, { verificationResult: '', recognitionNotes: '' })
  closeDialog.value = true
}

async function closeCapa() {
  const valid = await closeFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedCapa.value) return
  saving.value = true
  try {
    await qmsApi.closeCapa(selectedCapa.value.id, closeForm)
    ElMessage.success('CAPA 已由主管审核关闭')
    closeDialog.value = false
    await load()
  } catch {
    ElMessage.error('CAPA 关闭失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <el-alert type="info" :closable="false" show-icon title="检测人发起 CAPA，主管验证并关闭。" />
    <el-card shadow="never" class="content-card" v-loading="loading">
      <template #header><div class="card-header"><span>不良品处置单</span><div><el-button type="primary" @click="openManualDialog">新增</el-button><el-button link type="primary" @click="load">刷新</el-button></div></div></template>
      <el-table :data="ncrs" empty-text="暂无不良品处置单">
        <el-table-column prop="ncrNo" label="单据编号" min-width="180" />
        <el-table-column label="检测日期" min-width="120"><template #default="scope">{{ formatDate(scope.row.detectionDate) }}</template></el-table-column>
        <el-table-column prop="shiftName" label="班次" min-width="90"><template #default="scope">{{ scope.row.shiftName || '—' }}</template></el-table-column>
        <el-table-column prop="processName" label="检验工序" min-width="140"><template #default="scope">{{ scope.row.processName || '—' }}</template></el-table-column>
        <el-table-column prop="responsibleDepartment" label="责任部门" min-width="130"><template #default="scope">{{ scope.row.responsibleDepartment || '—' }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="170" show-overflow-tooltip><template #default="scope">{{ scope.row.remark || '—' }}</template></el-table-column>
        <el-table-column label="处置方式" min-width="110"><template #default="scope">{{ dispositionText(scope.row.dispositionMethod) }}</template></el-table-column>
        <el-table-column prop="initiatedBy" label="创建人" min-width="110" />
        <el-table-column label="创建日期" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="scope"><el-tag :type="scope.row.status === 'CLOSED' ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="175" fixed="right"><template #default="scope"><el-button type="primary" link @click="selectedDetailNcrId = scope.row.id">查看明细</el-button><el-button v-if="scope.row.status === 'OPEN'" type="primary" link @click="openCreateCapa(scope.row)">创建 CAPA</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>明细</span><span class="helper-text">{{ selectedDetailNcr ? `${selectedDetailNcr.ncrNo} · ${selectedDetailNcr.details?.length ?? 0} 行` : '请选择不良品处置单' }}</span></div></template>
      <el-table :data="selectedDetailNcr?.details ?? []" empty-text="暂无不良品明细">
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
      </el-table>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>8D / CAPA 闭环</span><span class="helper-text">主管审核关闭</span></div></template>
      <el-table :data="capas" empty-text="暂无 CAPA 记录">
        <el-table-column prop="capaNo" label="CAPA 编号" min-width="165" />
        <el-table-column prop="ncrNo" label="关联不良单" min-width="165" />
        <el-table-column prop="problemDescription" label="问题描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="containmentAction" label="临时遏制" min-width="180" show-overflow-tooltip />
        <el-table-column prop="correctiveAction" label="纠正措施" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" min-width="100"><template #default="scope"><el-tag :type="scope.row.status === 'CLOSED' ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><el-button v-if="scope.row.status !== 'CLOSED'" type="primary" link @click="openCloseCapa(scope.row)">主管关闭</el-button><span v-else>已关闭</span></template></el-table-column>
      </el-table>
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
          <el-form-item label="工序编码" required><el-input v-model="manualForm.processCode" placeholder="如 FQC" /></el-form-item>
          <el-form-item label="检验工序" required><el-input v-model="manualForm.processName" placeholder="如成品测试" /></el-form-item>
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
            <el-input v-model="line.workOrderNo" placeholder="生产指令号 *" />
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

    <el-dialog v-model="createDialog" title="创建 CAPA" width="min(640px, 94vw)">
      <el-alert :title="selectedNcr ? `关联不良单：${selectedNcr.ncrNo}` : ''" type="info" :closable="false" class="dialog-alert" />
      <el-form ref="capaFormRef" :model="capaForm" :rules="capaRules" label-position="top">
        <el-form-item label="D1 团队成员"><el-input v-model="capaForm.teamMembers" placeholder="填写负责人员及职责（可选）" /></el-form-item>
        <el-form-item label="D2 问题描述" prop="problemDescription"><el-input v-model="capaForm.problemDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="D3 临时遏制措施"><el-input v-model="capaForm.containmentAction" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D4 根因分析"><el-input v-model="capaForm.rootCause" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="D5 纠正措施"><el-input v-model="capaForm.correctiveAction" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="D7 预防再发措施"><el-input v-model="capaForm.preventiveAction" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="createCapa">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="closeDialog" title="主管审核关闭 CAPA" width="min(560px, 94vw)">
      <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-position="top"><el-form-item label="D6 有效性验证结论" prop="verificationResult"><el-input v-model="closeForm.verificationResult" type="textarea" :rows="4" placeholder="填写措施有效性验证结果" /></el-form-item><el-form-item label="D8 关闭确认"><el-input v-model="closeForm.recognitionNotes" type="textarea" :rows="2" placeholder="填写经验固化、团队确认或关闭说明（可选）" /></el-form-item></el-form>
      <template #footer><el-button @click="closeDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="closeCapa">审核关闭</el-button></template>
    </el-dialog>
  </section>
</template>
