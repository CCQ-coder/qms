<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { statusText } from '@/utils/display-text'
import type { Capa, Nonconformity } from '@/types/qms'

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
  } catch {
    ElMessage.error('不良品与 CAPA 数据读取失败')
  } finally {
    loading.value = false
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
      <template #header><div class="card-header"><span>不良品处置单</span><el-button link type="primary" @click="load">刷新</el-button></div></template>
      <el-table :data="ncrs" empty-text="暂无不良品处置单">
        <el-table-column prop="ncrNo" label="不良单号" min-width="165" />
        <el-table-column prop="workOrderNo" label="工单号" min-width="145" />
        <el-table-column prop="batchNo" label="生产批次" min-width="160" />
        <el-table-column prop="initiatedBy" label="发起人" min-width="110" />
        <el-table-column label="状态" min-width="110"><template #default="scope"><el-tag :type="scope.row.status === 'CLOSED' ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><el-button v-if="scope.row.status === 'OPEN'" type="primary" link @click="openCreateCapa(scope.row)">创建 CAPA</el-button><span v-else>—</span></template></el-table-column>
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
