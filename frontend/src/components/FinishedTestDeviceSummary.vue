<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { qmsApi } from '@/api/qms'
import { formatDateTime } from '@/utils/display-text'
import type { FinishedTestDeviceSummary, FinishedTestSettlement } from '@/types/qms'
import { groupFinishedTestSummary, type SummaryDimension } from '@/views/finishedTestSummary'

const rows = ref<FinishedTestDeviceSummary[]>([])
const settlements = ref<FinishedTestSettlement[]>([])
const loading = ref(false)
const error = ref('')
const settlementError = ref('')
const updatedAt = ref<Date>()
const dateRange = ref<string[]>([])
const workOrder = ref('')
const pcbMaterial = ref('')
const dimension = ref<SummaryDimension>('workOrderNo')
const dimensionLabel = computed(() => ({
  workOrderNo: '生产指令', internalProductNo: '厂内编号', dateEquipmentOrder: '检测日期＋设备编号＋生产指令'
})[dimension.value])
const filteredRows = computed(() => rows.value.filter(row => {
  const [from, to] = dateRange.value
  return (!from || row.inspectionDate >= from) && (!to || row.inspectionDate <= to)
    && (!workOrder.value.trim() || row.workOrderNo.toLowerCase().includes(workOrder.value.trim().toLowerCase()))
    && (!pcbMaterial.value.trim() || (row.pcbMaterialNo ?? '').toLowerCase().includes(pcbMaterial.value.trim().toLowerCase()))
}))
const groupedRows = computed(() => groupFinishedTestSummary(filteredRows.value, dimension.value))
const filteredSettlements = computed(() => settlements.value.filter(row => {
  const [from, to] = dateRange.value
  const date = row.lastDetectionAt.slice(0, 10)
  return (!from || date >= from) && (!to || date <= to)
    && (!workOrder.value.trim() || row.workOrderNo.toLowerCase().includes(workOrder.value.trim().toLowerCase()))
    && (!pcbMaterial.value.trim() || row.materialKey.toLowerCase().includes(pcbMaterial.value.trim().toLowerCase()))
}))
const totals = computed(() => filteredRows.value.reduce((sum, row) => ({
  tested: sum.tested + row.totalCount, good: sum.good + row.goodCount, bad: sum.bad + row.badCount
}), { tested: 0, good: 0, bad: 0 }))
const filterKey = computed(() => JSON.stringify([dimension.value, dateRange.value, workOrder.value, pcbMaterial.value]))

function resetFilters() {
  dateRange.value = []
  workOrder.value = ''
  pcbMaterial.value = ''
}

async function load() {
  loading.value = true
  const [summary, snapshot] = await Promise.allSettled([
    qmsApi.listFinishedTestDeviceSummary(), qmsApi.listFinishedTestSettlements()
  ])
  if (summary.status === 'fulfilled') {
    rows.value = summary.value
    error.value = ''
    updatedAt.value = new Date()
  } else error.value = '设备检测汇总读取失败，请重试。'
  if (snapshot.status === 'fulfilled') {
    settlements.value = snapshot.value
    settlementError.value = ''
  } else settlementError.value = '结算快照读取失败，请重试。'
  loading.value = false
}

onMounted(() => { void load() })
</script>

<template>
  <div class="page-stack">
  <el-card shadow="never" class="content-card summary-table-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <div><span>成品测试汇总表</span><small>按生产指令、厂内编号或检测日期＋设备编号＋生产指令查看汇总；未关联生产指令的记录暂不计入。</small></div>
        <div class="task-queue-actions"><span v-if="updatedAt" class="live-status"><i></i>更新于 {{ updatedAt.toLocaleString('zh-CN') }}</span><el-button size="small" @click="load">刷新</el-button></div>
      </div>
    </template>
    <el-alert v-if="error" type="error" :closable="false" :title="error" style="margin-bottom: 12px" />
    <div role="group" aria-label="汇总维度" class="finished-summary-dimensions">
      <el-button :type="dimension === 'workOrderNo' ? 'primary' : 'default'" :aria-pressed="dimension === 'workOrderNo'" @click="dimension = 'workOrderNo'">生产指令</el-button>
      <el-button :type="dimension === 'internalProductNo' ? 'primary' : 'default'" :aria-pressed="dimension === 'internalProductNo'" @click="dimension = 'internalProductNo'">厂内编号</el-button>
      <el-button :type="dimension === 'dateEquipmentOrder' ? 'primary' : 'default'" :aria-pressed="dimension === 'dateEquipmentOrder'" @click="dimension = 'dateEquipmentOrder'">检测日期＋设备编号＋生产指令</el-button>
    </div>
    <div class="report-filter finished-summary-filters">
      <span>检测日期</span>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" clearable />
      <el-input v-model="workOrder" clearable placeholder="筛选生产指令" aria-label="筛选生产指令" />
      <el-input v-model="pcbMaterial" clearable placeholder="筛选 PCB 料号" aria-label="筛选 PCB 料号" />
      <el-button @click="resetFilters">重置</el-button>
    </div>
    <paged-table v-if="!error" :key="filterKey" :data="groupedRows" empty-text="当前筛选条件下暂无设备检测记录">
      <el-table-column v-if="dimension === 'dateEquipmentOrder'" prop="inspectionDate" label="检测日期" min-width="140" />
      <el-table-column v-if="dimension === 'dateEquipmentOrder'" prop="equipmentCode" label="设备编号" min-width="135" />
      <el-table-column v-if="dimension !== 'internalProductNo'" prop="workOrderNo" label="生产指令" min-width="150" />
      <el-table-column prop="internalProductNo" label="厂内编号" min-width="160" />
      <el-table-column v-if="dimension !== 'internalProductNo'" prop="pcbMaterialNo" label="料号" min-width="165" show-overflow-tooltip><template #default="scope">{{ scope.row.pcbMaterialNo || '—' }}</template></el-table-column>
      <el-table-column prop="totalCount" label="检测总数" min-width="130" align="right" />
      <el-table-column prop="goodCount" label="合格" min-width="110" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodCount }}</span></template></el-table-column>
      <el-table-column prop="badCount" label="不合格" min-width="110" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.badCount }}</span></template></el-table-column>
      <el-table-column label="合格率" min-width="190"><template #default="scope"><el-progress :percentage="scope.row.passRate" :stroke-width="9" :color="scope.row.passRate >= 98.5 ? '#12b76a' : '#f79009'" /></template></el-table-column>
    </paged-table>
    <div v-if="!error" class="summary-footnote">当前筛选结果：{{ totals.tested }} 板，合格 {{ totals.good }} 板，不合格 {{ totals.bad }} 板（按{{ dimensionLabel }}汇总，共 {{ groupedRows.length }} 组）。</div>
  </el-card>
  <el-card shadow="never" class="content-card" v-loading="loading">
    <template #header>
      <div class="card-header"><div><span>12 小时静默结算快照</span><small>同料号所有设备连续 12 小时没有新增已测板后自动生成；显示最近 100 轮，快照不重复计入上方汇总。</small></div><el-button @click="load">刷新</el-button></div>
    </template>
    <el-alert v-if="settlementError" type="error" :closable="false" :title="settlementError" style="margin-bottom: 12px" />
    <paged-table v-else :key="filterKey" :data="filteredSettlements" empty-text="当前筛选条件下暂无已结算的检测轮次">
      <el-table-column prop="materialKey" label="设备料号" min-width="130" />
      <el-table-column prop="workOrderNo" label="生产指令" min-width="145" />
      <el-table-column prop="internalProductNo" label="厂内编号" min-width="160" />
      <el-table-column label="首次检测" min-width="175"><template #default="scope">{{ formatDateTime(scope.row.firstDetectionAt) }}</template></el-table-column>
      <el-table-column label="最后新增" min-width="175"><template #default="scope">{{ formatDateTime(scope.row.lastDetectionAt) }}</template></el-table-column>
      <el-table-column prop="equipmentCount" label="有新增设备" min-width="110" align="right" />
      <el-table-column prop="tested" label="已测" min-width="100" align="right" />
      <el-table-column prop="good" label="好板" min-width="100" align="right" />
      <el-table-column prop="bad" label="坏板" min-width="100" align="right" />
      <el-table-column label="结算时间" min-width="175"><template #default="scope">{{ formatDateTime(scope.row.settledAt) }}</template></el-table-column>
    </paged-table>
  </el-card>
  </div>
</template>
