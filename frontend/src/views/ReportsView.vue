<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { use, init, type ECharts } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { qmsApi } from '@/api/qms'
import { formatDateTime, statusText, taskTypeText } from '@/utils/display-text'
import type { QualitySummary, Traceability } from '@/types/qms'

const loading = ref(false)
const route = useRoute()
const summary = ref<QualitySummary>({ totalRecords: 0, passRecords: 0, failRecords: 0, firstPassRate: 0, byProcess: [], defectRanking: [], dailyTrend: [] })
const reportDateRange = ref<string[]>([])
const trendChartRef = ref<HTMLElement>()
let trendChart: ECharts | undefined

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const batchNo = ref('')
const traceLoading = ref(false)
const traceability = ref<Traceability>()
const exportingExcel = ref(false)
const isAnalyticsPage = computed(() => route.meta.reportSection !== 'traceability')
const dateRangeLabel = computed(() => reportDateRange.value.length === 2
  ? `${reportDateRange.value[0]} 至 ${reportDateRange.value[1]}` : '全部检验日期')

function reportDateParams() {
  return reportDateRange.value.length === 2
    ? { from: reportDateRange.value[0], to: reportDateRange.value[1] }
    : { from: undefined, to: undefined }
}

async function loadSummary() {
  if (!isAnalyticsPage.value) return
  loading.value = true
  try {
    const { from, to } = reportDateParams()
    summary.value = await qmsApi.getQualitySummary(from, to)
    await nextTick()
    renderTrendChart()
  } catch {
    ElMessage.error('质量汇总读取失败')
  } finally {
    loading.value = false
  }
}

function renderTrendChart() {
  if (!trendChartRef.value || !summary.value.dailyTrend.length) return
  trendChart?.dispose()
  trendChart = init(trendChartRef.value)
  trendChart.setOption({
    grid: { top: 38, right: 24, bottom: 28, left: 45 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['一次合格率', '不合格记录'] },
    xAxis: { type: 'category', data: summary.value.dailyTrend.map((item) => item.inspectionDate) },
    yAxis: [
      { type: 'value', name: '合格率', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '不合格记录', minInterval: 1 }
    ],
    series: [
      { name: '一次合格率', type: 'line', smooth: true, data: summary.value.dailyTrend.map((item) => item.passRate), yAxisIndex: 0, itemStyle: { color: '#155eef' } },
      { name: '不合格记录', type: 'bar', data: summary.value.dailyTrend.map((item) => item.failRecords), yAxisIndex: 1, itemStyle: { color: '#f79009' } }
    ]
  })
}

function resizeTrendChart() {
  trendChart?.resize()
}

async function searchTrace() {
  if (!batchNo.value.trim()) return
  traceLoading.value = true
  traceability.value = undefined
  try {
    traceability.value = await qmsApi.getBatchTraceability(batchNo.value.trim())
  } catch {
    ElMessage.error('未查询到该生产批次')
  } finally {
    traceLoading.value = false
  }
}

function exportProcessCsv() {
  const rows = [
    ['工序编码', '工序名称', '检验记录数', '合格数', '不合格数', '一次合格率'],
    ...summary.value.byProcess.map(item => [item.processCode, item.processName, item.totalRecords, item.passRecords, item.failRecords, `${item.passRate}%`])
  ]
  const csv = '\ufeff' + rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = '质量汇总报表.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function exportQualityExcel() {
  exportingExcel.value = true
  try {
    const { from, to } = reportDateParams()
    const blob = await qmsApi.downloadQualitySummaryExcel(from, to)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'PCB-质量汇总报表.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('Excel 导出失败')
  } finally {
    exportingExcel.value = false
  }
}

watch(() => summary.value.dailyTrend, () => nextTick(renderTrendChart), { deep: true })
watch(() => route.fullPath, () => {
  if (isAnalyticsPage.value) void loadSummary()
})
onMounted(() => {
  window.addEventListener('resize', resizeTrendChart)
  if (isAnalyticsPage.value) void loadSummary()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeTrendChart)
  trendChart?.dispose()
})
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <template v-if="isAnalyticsPage">
    <el-card shadow="never" class="content-card"><div class="report-filter"><span>检验日期</span><el-date-picker v-model="reportDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" clearable /><el-button type="primary" :loading="loading" @click="loadSummary">查询</el-button><span class="filter-hint">当前范围：{{ dateRangeLabel }}</span></div></el-card>
    <div class="metric-grid report-metrics"><article class="metric-card"><span>检验记录</span><strong>{{ summary.totalRecords }}</strong><small>{{ dateRangeLabel }} · 当前权限范围</small></article><article class="metric-card success"><span>一次合格率</span><strong>{{ summary.firstPassRate }}%</strong><small>按可见已提交记录计算</small></article><article class="metric-card"><span>合格记录</span><strong>{{ summary.passRecords }}</strong><small>合格</small></article><article class="metric-card warning"><span>不合格记录</span><strong>{{ summary.failRecords }}</strong><small>不合格</small></article></div>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>质量趋势</span><small>一次合格率与不合格记录按检验日期汇总</small></div></template>
      <div v-if="summary.dailyTrend.length" ref="trendChartRef" class="quality-trend-chart" />
      <el-empty v-else description="当前日期范围暂无可见检验趋势数据" />
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>工序质量汇总</span><div><el-button type="primary" :loading="exportingExcel" @click="exportQualityExcel">导出 Excel</el-button><el-button plain @click="exportProcessCsv">导出 CSV</el-button></div></div></template>
      <paged-table :key="dateRangeLabel" :data="summary.byProcess" empty-text="暂无检验数据"><el-table-column prop="processCode" label="工序编码" min-width="130" /><el-table-column prop="processName" label="工序名称" min-width="150" /><el-table-column prop="totalRecords" label="检验记录" min-width="110" /><el-table-column prop="passRecords" label="合格" min-width="100" /><el-table-column prop="failRecords" label="不合格" min-width="100" /><el-table-column label="一次合格率" min-width="125"><template #default="scope"><el-progress :percentage="scope.row.passRate" :stroke-width="10" /></template></el-table-column></paged-table>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>缺陷排行（Top 10）</span><small>按当前权限范围内的不合格检验记录汇总</small></div></template>
      <paged-table :key="dateRangeLabel" :data="summary.defectRanking" empty-text="暂无不良缺陷数据"><el-table-column type="index" label="排名" width="80" /><el-table-column prop="defectSummary" label="缺陷说明" min-width="260" show-overflow-tooltip /><el-table-column prop="failedRecords" label="不合格记录数" min-width="140" /><el-table-column prop="defectiveQuantity" label="不良数量" min-width="120" /></paged-table>
    </el-card>
    </template>

    <section v-else>
      <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>生产批次质量追溯</span><div class="trace-search"><el-input v-model="batchNo" placeholder="输入生产批次，如 BATCH-260902-A01" @keyup.enter="searchTrace" /><el-button type="primary" :loading="traceLoading" @click="searchTrace">追溯查询</el-button></div></div></template>
      <template v-if="traceability"><div class="trace-overview"><span>工单：<strong>{{ traceability.workOrder.workOrderNo }}</strong></span><span>批次：<strong>{{ traceability.batchNo }}</strong></span><span>产品：<strong>{{ traceability.workOrder.productCode }} {{ traceability.workOrder.productName }}</strong></span></div><paged-table :key="traceability.batchNo" :data="traceability.inspectionRecords" empty-text="该批次暂无检验记录"><el-table-column prop="taskNo" label="任务号" min-width="150" /><el-table-column prop="processName" label="工序" min-width="140" /><el-table-column label="检验类型" min-width="110"><template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template></el-table-column><el-table-column label="结果" min-width="100"><template #default="scope"><el-tag :type="scope.row.verdict === 'PASS' ? 'success' : 'danger'">{{ statusText(scope.row.verdict) }}</el-tag></template></el-table-column><el-table-column prop="defectiveQuantity" label="不良数量" min-width="100" /><el-table-column prop="defectSummary" label="缺陷说明" min-width="210" show-overflow-tooltip /><el-table-column prop="defectLocation" label="缺陷位置" min-width="180" show-overflow-tooltip /><el-table-column prop="inspectedBy" label="检验人" min-width="110" /><el-table-column label="检验时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.inspectedAt) }}</template></el-table-column></paged-table><div v-if="traceability.nonconformities.length" class="trace-ncr"><strong>关联不良品处置：</strong><el-tag v-for="ncr in traceability.nonconformities" :key="ncr.ncrNo" type="warning">{{ ncr.ncrNo }} · {{ statusText(ncr.status) }}</el-tag></div></template>
      <el-empty v-else description="输入批次号后查看工单、检验记录与不良品处置闭环" />
      </el-card>
    </section>
  </section>
</template>
