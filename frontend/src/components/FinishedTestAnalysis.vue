<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { use, init, type ECharts } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import type { FinishedTestAnalysis, FinishedTestGroupMetric } from '@/types/qms'

type Dimension = 'date' | 'workOrder' | 'material' | 'equipment'
const props = withDefaults(defineProps<{ initialDimension?: Dimension, showTrend?: boolean, hideUnknownQuantity?: boolean }>(), {
  initialDimension: 'date', showTrend: false, hideUnknownQuantity: false
})
const dimension = ref<Dimension>(props.initialDimension)
const dateRange = ref<string[]>([])
const loading = ref(false)
const error = ref('')
const exportingExcel = ref(false)
const analysis = ref<FinishedTestAnalysis>({
  recordCount: 0, totalBoards: 0, goodBoards: 0, badBoards: 0, unknownQuantityRecords: 0, passRate: 0,
  byDate: [], byWorkOrder: [], byMaterial: [], byEquipment: []
})
const chartRef = ref<HTMLElement>()
let chart: ECharts | undefined
let requestVersion = 0

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const dimensionLabels: Record<Dimension, string> = {
  date: '检测日期', workOrder: '生产指令', material: '料号', equipment: '设备'
}
const rows = computed<FinishedTestGroupMetric[]>(() => ({
  date: analysis.value.byDate,
  workOrder: analysis.value.byWorkOrder,
  material: analysis.value.byMaterial,
  equipment: analysis.value.byEquipment
})[dimension.value])

function dateParams() {
  return dateRange.value.length === 2
    ? { from: dateRange.value[0], to: dateRange.value[1] }
    : { from: undefined, to: undefined }
}

async function load() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const { from, to } = dateParams()
    const result = await qmsApi.getFinishedTestAnalysis(from, to)
    if (version !== requestVersion) return
    analysis.value = result
    await nextTick()
    renderChart()
  } catch {
    if (version === requestVersion) error.value = '成品测试分析读取失败，请重试。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function renderChart() {
  if (!props.showTrend || !chartRef.value) return
  chart?.dispose()
  chart = init(chartRef.value)
  const dates = [...analysis.value.byDate].reverse()
  chart.setOption({
    grid: { top: 38, right: 24, bottom: 28, left: 45 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['板数合格率', '坏板数'] },
    xAxis: { type: 'category', data: dates.map(item => item.key) },
    yAxis: [
      { type: 'value', name: '合格率', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '坏板数', minInterval: 1 }
    ],
    series: [
      { name: '板数合格率', type: 'line', smooth: true, data: dates.map(item => item.passRate), yAxisIndex: 0, itemStyle: { color: '#155eef' } },
      { name: '坏板数', type: 'bar', data: dates.map(item => item.badBoards), yAxisIndex: 1, itemStyle: { color: '#f79009' } }
    ]
  })
}

function exportCsv() {
  const data = [
    [dimensionLabels[dimension.value], '已测板数', '好板', '坏板', '板数合格率', '记录数', '无数量记录'],
    ...rows.value.map(item => [item.key, item.totalBoards, item.goodBoards, item.badBoards,
      `${item.passRate}%`, item.recordCount, item.unknownQuantityRecords])
  ]
  const csv = '\ufeff' + data.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n')
  download(new Blob([csv], { type: 'text/csv;charset=utf-8' }), `成品测试-${dimensionLabels[dimension.value]}.csv`)
}

async function exportExcel() {
  exportingExcel.value = true
  try {
    const { from, to } = dateParams()
    download(await qmsApi.downloadFinishedTestAnalysisExcel(from, to), 'PCB-成品测试分析.xlsx')
  } catch {
    ElMessage.error('Excel 导出失败')
  } finally {
    exportingExcel.value = false
  }
}

function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

function resizeChart() { chart?.resize() }
onMounted(() => { window.addEventListener('resize', resizeChart); void load() })
onBeforeUnmount(() => { requestVersion++; window.removeEventListener('resize', resizeChart); chart?.dispose() })
</script>

<template>
  <div class="finished-analysis" v-loading="loading">
    <el-card shadow="never" class="content-card">
      <div class="report-filter">
        <span>检测日期</span>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="2026-10-20" end-placeholder="结束日期" clearable :disabled-date="(day: Date) => day.getTime() < new Date(2026, 9, 20).getTime()" />
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      </div>
    </el-card>
    <el-alert v-if="error" type="error" :closable="false" :title="error" />
    <div class="metric-grid finished-analysis-metrics" :class="{ 'four-columns': hideUnknownQuantity }">
      <article class="metric-card"><span>已测板数</span><strong>{{ analysis.totalBoards }}</strong></article>
      <article class="metric-card success"><span>好板</span><strong>{{ analysis.goodBoards }}</strong></article>
      <article class="metric-card warning"><span>坏板</span><strong>{{ analysis.badBoards }}</strong></article>
      <article class="metric-card success"><span>板数合格率</span><strong>{{ analysis.totalBoards ? `${analysis.passRate}%` : '—' }}</strong></article>
      <article v-if="!hideUnknownQuantity" class="metric-card"><span>无数量记录</span><strong>{{ analysis.unknownQuantityRecords }}</strong></article>
    </div>
    <el-card v-if="showTrend" shadow="never" class="content-card">
      <template #header><div class="card-header"><span>质量趋势</span></div></template>
      <div v-if="analysis.byDate.length" ref="chartRef" class="quality-trend-chart" />
      <el-empty v-else description="暂无数据" />
    </el-card>
    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <span>成品测试分析</span>
          <div class="finished-analysis-actions">
            <el-select v-model="dimension" aria-label="分析维度" style="width: 150px">
              <el-option v-for="(label, key) in dimensionLabels" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" :loading="exportingExcel" @click="exportExcel">导出 Excel</el-button>
            <el-button @click="exportCsv">导出 CSV</el-button>
          </div>
        </div>
      </template>
      <paged-table :key="dimension" :data="rows" empty-text="暂无数据">
        <el-table-column prop="key" :label="dimensionLabels[dimension]" min-width="170" />
        <el-table-column prop="totalBoards" label="已测板数" min-width="110" align="right" />
        <el-table-column prop="goodBoards" label="好板" min-width="100" align="right" />
        <el-table-column prop="badBoards" label="坏板" min-width="100" align="right" />
        <el-table-column label="板数合格率" min-width="150"><template #default="{ row }"><el-progress v-if="row.totalBoards" :percentage="row.passRate" :stroke-width="9" /><span v-else>—</span></template></el-table-column>
        <el-table-column prop="recordCount" label="记录数" min-width="90" align="right" />
        <el-table-column v-if="!hideUnknownQuantity" prop="unknownQuantityRecords" label="无数量记录" min-width="110" align="right" />
      </paged-table>
    </el-card>
  </div>
</template>

<style scoped>
.finished-analysis { display: grid; gap: 16px; }
.finished-analysis-metrics { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.finished-analysis-metrics.four-columns { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.finished-analysis-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
@media (max-width: 900px) { .finished-analysis-metrics, .finished-analysis-metrics.four-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
