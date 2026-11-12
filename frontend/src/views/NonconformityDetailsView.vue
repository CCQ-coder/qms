<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDate, formatDateTime, statusText } from '@/utils/display-text'
import type { Nonconformity, NonconformityDetail, WorkOrder } from '@/types/qms'
import ScrapImportPanel from './ScrapImportPanel.vue'

const loading = ref(false)
const exporting = ref(false)
const nonconformities = ref<Nonconformity[]>([])
const rows = computed(() => nonconformities.value.flatMap((ncr) =>
  ncr.details.map((detail) => ({ ncr, detail }))))
const filters = reactive({ ncrNo: '', dateRange: [] as string[], process: '', department: '',
  workOrderNo: '', internalNo: '', materialNo: '' })
const appliedFilters = ref({ ...filters, dateRange: [] as string[] })
const filteredRows = computed(() => rows.value.filter(({ ncr, detail }) => {
  const filter = appliedFilters.value
  return (!filter.ncrNo || ncr.ncrNo.toLowerCase().includes(filter.ncrNo.toLowerCase()))
    && (!filter.dateRange.length || Boolean(ncr.detectionDate && ncr.detectionDate >= filter.dateRange[0] && ncr.detectionDate <= filter.dateRange[1]))
    && (!filter.process || (ncr.processName ?? '').includes(filter.process) || (detail.processName ?? '').includes(filter.process))
    && (!filter.department || (ncr.responsibleDepartment ?? '').includes(filter.department))
    && (!filter.workOrderNo || (detail.workOrderNo ?? '').includes(filter.workOrderNo))
    && (!filter.internalNo || (detail.internalProductNo ?? '').toLowerCase().includes(filter.internalNo.toLowerCase()))
    && (!filter.materialNo || (detail.materialNo ?? '').toLowerCase().includes(filter.materialNo.toLowerCase()))
}))
const bindingDialog = ref(false)
const bindingRow = ref<{ ncr: Nonconformity; detail: NonconformityDetail }>()
const bindingKeyword = ref('')
const bindingOrders = ref<WorkOrder[]>([])
const selectedWorkOrderNo = ref('')
const bindingLoading = ref(false)
const bindingSaving = ref(false)

function resetFilters() {
  Object.assign(filters, { ncrNo: '', dateRange: [], process: '', department: '', workOrderNo: '', internalNo: '', materialNo: '' })
  appliedFilters.value = { ...filters, dateRange: [] }
}

function openBinding(ncr: Nonconformity, detail: NonconformityDetail) {
  bindingRow.value = { ncr, detail }
  bindingKeyword.value = detail.internalProductNo ?? ''
  bindingOrders.value = []
  selectedWorkOrderNo.value = ''
  bindingDialog.value = true
  void searchBindingOrders()
}

function matchesInternalNo(imported?: string, candidate?: string) {
  const key = imported?.trim().toUpperCase() ?? ''
  const value = candidate?.trim().toUpperCase() ?? ''
  if (!key) return false
  return /^\d{5}$/.test(key) ? new RegExp(`(^|\\D)${key}(\\D|$)`).test(value) : value.includes(key)
}

async function searchBindingOrders() {
  if (!bindingRow.value) return
  bindingLoading.value = true
  try {
    const page = await qmsApi.listWorkOrders({ keyword: bindingKeyword.value.trim() || undefined, page: 1, pageSize: 50 })
    const { ncr, detail } = bindingRow.value
    bindingOrders.value = page.records.filter((order) => matchesInternalNo(detail.internalProductNo, order.internalProductNo)
      && Boolean(order.inputDate && ncr.detectionDate && order.inputDate <= ncr.detectionDate))
  } catch { ElMessage.error('生产指令查询失败') }
  finally { bindingLoading.value = false }
}

async function bindWorkOrder() {
  if (!bindingRow.value || !selectedWorkOrderNo.value) return
  bindingSaving.value = true
  try {
    await qmsApi.bindImportedScrapRow(bindingRow.value.ncr.id, bindingRow.value.detail.id, selectedWorkOrderNo.value)
    bindingDialog.value = false
    await load()
    ElMessage.success('生产指令已关联')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '手工关联失败') }
  finally { bindingSaving.value = false }
}

function dispositionText(method?: string) {
  return ({ SCRAP: '报废', REWORK: '返工', CONCESSION: '让步接收' } as Record<string, string>)[method ?? ''] ?? method ?? '—'
}

async function load() {
  loading.value = true
  try {
    nonconformities.value = await qmsApi.listNonconformities()
  } catch {
    ElMessage.error('不良品明细读取失败')
  } finally {
    loading.value = false
  }
}

async function exportScrapStatistics() {
  exporting.value = true
  try {
    const blob = await qmsApi.downloadScrapStatisticsExcel()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '报废统计.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('报废统计导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <el-card shadow="never" class="content-card">
    <template #header><div class="card-header"><span>不良品明细表</span><div><ScrapImportPanel @imported="load" /><el-button type="primary" :loading="exporting" @click="exportScrapStatistics">导出报废统计</el-button><el-button link type="primary" @click="load">刷新</el-button></div></div></template>
    <div class="report-filter ncr-details-filter">
      <el-input v-model="filters.ncrNo" clearable placeholder="单据编号" aria-label="单据编号" />
      <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="检测开始日期" end-placeholder="检测结束日期" clearable />
      <el-input v-model="filters.process" clearable placeholder="检验工序" aria-label="检验工序" />
      <el-input v-model="filters.department" clearable placeholder="责任部门" aria-label="责任部门" />
      <el-input v-model="filters.workOrderNo" clearable placeholder="生产指令" aria-label="生产指令" />
      <el-input v-model="filters.internalNo" clearable placeholder="厂内编号" aria-label="厂内编号" />
      <el-input v-model="filters.materialNo" clearable placeholder="料号" aria-label="料号" />
      <el-button type="primary" @click="appliedFilters = { ...filters, dateRange: [...filters.dateRange] }">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>
    <el-table v-loading="loading" :data="filteredRows" empty-text="暂无不良品明细" border>
      <el-table-column label="单据编号" min-width="180" fixed="left"><template #default="{ row }">{{ row.ncr.ncrNo }}</template></el-table-column>
      <el-table-column label="检测日期" min-width="120"><template #default="{ row }">{{ formatDate(row.ncr.detectionDate) }}</template></el-table-column>
      <el-table-column label="班次" min-width="90"><template #default="{ row }">{{ row.ncr.shiftName || '—' }}</template></el-table-column>
      <el-table-column label="检验工序" min-width="130"><template #default="{ row }">{{ row.ncr.processName || '—' }}</template></el-table-column>
      <el-table-column label="责任部门" min-width="130"><template #default="{ row }">{{ row.ncr.responsibleDepartment || '—' }}</template></el-table-column>
      <el-table-column label="处置方式" min-width="105"><template #default="{ row }">{{ dispositionText(row.ncr.dispositionMethod) }}</template></el-table-column>
      <el-table-column label="行号" min-width="70"><template #default="{ row }">{{ row.detail.lineNo }}</template></el-table-column>
      <el-table-column label="生产指令" min-width="150"><template #default="{ row }">{{ row.detail.workOrderNo || '待关联' }}</template></el-table-column>
      <el-table-column label="厂内编号" min-width="135"><template #default="{ row }">{{ row.detail.internalProductNo || '—' }}</template></el-table-column>
      <el-table-column label="料号" min-width="125"><template #default="{ row }">{{ row.detail.materialNo || '—' }}</template></el-table-column>
      <el-table-column label="单位" min-width="75"><template #default="{ row }">{{ row.detail.unit }}</template></el-table-column>
      <el-table-column label="单 PCS 面积" min-width="125" align="right"><template #default="{ row }">{{ row.detail.singlePcsArea ?? '—' }}</template></el-table-column>
      <el-table-column label="报废数量" min-width="105" align="right"><template #default="{ row }">{{ row.detail.scrapQuantity }}</template></el-table-column>
      <el-table-column label="报废总面积" min-width="125" align="right"><template #default="{ row }">{{ row.detail.scrapArea ?? '—' }}</template></el-table-column>
      <el-table-column label="报废原因" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.detail.scrapReason || '—' }}</template></el-table-column>
      <el-table-column label="明细备注" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.detail.remark || '—' }}</template></el-table-column>
      <el-table-column label="单据备注" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.ncr.remark || '—' }}</template></el-table-column>
      <el-table-column label="创建来源" min-width="130"><template #default="{ row }">{{ row.ncr.sourceType === 'DEVICE' ? '系统自动生成' : (row.ncr.initiatedBy || '—') }}</template></el-table-column>
      <el-table-column label="创建日期" min-width="170"><template #default="{ row }">{{ formatDateTime(row.ncr.createdAt) }}</template></el-table-column>
      <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ statusText(row.ncr.status) }}</template></el-table-column>
      <el-table-column label="审核人" min-width="110"><template #default="{ row }">{{ row.ncr.reviewedBy || '—' }}</template></el-table-column>
      <el-table-column label="审核时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.ncr.reviewedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button v-if="row.ncr.sourceType === 'EXCEL' && !row.detail.workOrderNo" link type="primary" @click="openBinding(row.ncr, row.detail)">手工关联</el-button></template></el-table-column>
    </el-table>
  </el-card>
  <el-dialog v-model="bindingDialog" title="手工关联生产指令" width="min(760px, 94vw)">
    <p v-if="bindingRow">{{ bindingRow.ncr.ncrNo }} · 厂内编号 {{ bindingRow.detail.internalProductNo }} · Excel 时间 {{ formatDate(bindingRow.ncr.detectionDate) }}</p>
    <div class="task-queue-actions"><el-input v-model="bindingKeyword" clearable placeholder="搜索厂内编号或生产指令" @keyup.enter="searchBindingOrders" /><el-button :loading="bindingLoading" @click="searchBindingOrders">查询工单</el-button></div>
    <el-table v-loading="bindingLoading" :data="bindingOrders" empty-text="无输入时间不晚于 Excel 日期的同厂内编号工单" max-height="320" style="margin-top: 12px">
        <el-table-column label="选择" width="70"><template #default="{ row }"><input type="radio" name="scrap-binding-order" :checked="selectedWorkOrderNo === row.workOrderNo" :aria-label="`选择工单 ${row.workOrderNo}`" @change="selectedWorkOrderNo = row.workOrderNo" /></template></el-table-column>
      <el-table-column prop="workOrderNo" label="生产指令" min-width="150" />
      <el-table-column prop="internalProductNo" label="厂内编号" min-width="150" />
      <el-table-column prop="inputDate" label="输入日期" min-width="130" />
    </el-table>
    <template #footer><el-button @click="bindingDialog = false">取消</el-button><el-button type="primary" :disabled="!selectedWorkOrderNo" :loading="bindingSaving" @click="bindWorkOrder">确认关联</el-button></template>
  </el-dialog>
</template>
