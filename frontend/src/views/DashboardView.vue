<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { use, init, type ECharts } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime } from '@/utils/display-text'
import type { DashboardSummary, QualitySummary } from '@/types/qms'

use([PieChart, LegendComponent, TooltipComponent, CanvasRenderer])

const loading = ref(true)
const summary = ref<DashboardSummary>({ activeWorkOrders: 0, pendingInspectionTasks: 0, completedInspectionTasks: 0, openNonconformities: 0, firstPassRate: 0 })
const quality = ref<QualitySummary>({ totalRecords: 0, passRecords: 0, failRecords: 0, firstPassRate: 0, byProcess: [], defectRanking: [], dailyTrend: [] })
const updatedAtLabel = ref('')
const defectChartRef = ref<HTMLElement>()
let defectChart: ECharts | undefined

function updateTimestamp() {
  updatedAtLabel.value = formatDateTime(new Date())
}

function renderCharts() {
  defectChart?.dispose()
  if (defectChartRef.value && quality.value.defectRanking.length) {
    defectChart = init(defectChartRef.value)
    defectChart.setOption({
      color: ['#d92d4b', '#b77900', '#7a36df', '#1570ef', '#12b76a', '#f79009'],
      tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
      legend: { type: 'scroll', orient: 'vertical', right: 4, top: 'middle', icon: 'circle', textStyle: { color: '#344054' } },
      series: [{
        type: 'pie', radius: ['48%', '72%'], center: ['35%', '52%'], avoidLabelOverlap: true,
        label: { show: true, position: 'center', formatter: `{count|${quality.value.failRecords}}\n{label|不合格}`, rich: { count: { color: '#101828', fontSize: 22, fontWeight: 700, lineHeight: 28 }, label: { color: '#667085', fontSize: 12 } } },
        labelLine: { show: false }, data: quality.value.defectRanking.map((item) => ({ name: item.defectSummary || '未填写缺陷', value: item.failedRecords }))
      }]
    })
  }
}

function resizeCharts() { defectChart?.resize() }

async function load() {
  loading.value = true
  try {
    const [dashboardData, qualityData] = await Promise.all([qmsApi.getDashboardSummary(), qmsApi.getQualitySummary()])
    summary.value = dashboardData
    quality.value = qualityData
    updateTimestamp()
    await nextTick()
    renderCharts()
  } catch {
    ElMessage.error('无法连接 QMS 后端，请确认后端服务已启动。')
  } finally { loading.value = false }
}

onMounted(() => { window.addEventListener('resize', resizeCharts); void load() })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); defectChart?.dispose() })
</script>

<template>
  <section class="dashboard-page" v-loading="loading">
    <div class="dashboard-intro">
      <div><h2>质量总览</h2><p>同步汇总成品测试、待检任务、工序检验与不良处置数据，展示当前质量业务状态。</p></div>
      <span class="dashboard-updated"><i></i>最新业务数据 {{ updatedAtLabel || '加载中' }}</span>
    </div>

    <div class="dashboard-metrics">
      <article class="dashboard-metric"><span>检验记录总数</span><strong>{{ quality.totalRecords.toLocaleString() }}</strong><small>当前权限范围内的已提交记录</small></article>
      <article class="dashboard-metric success"><span>一次合格率</span><strong>{{ quality.firstPassRate.toFixed(2) }}%</strong><small>合格记录数 ÷ 检验记录总数</small></article>
      <article class="dashboard-metric danger"><span>待检数量</span><strong>{{ summary.pendingInspectionTasks.toLocaleString() }}</strong><small>尚未完成的检验任务</small></article>
      <article class="dashboard-metric success"><span>已完成检验</span><strong>{{ summary.completedInspectionTasks.toLocaleString() }}</strong><small>累计完成的检验任务</small></article>
    </div>

    <el-card shadow="never" class="content-card dashboard-flow-card">
      <template #header><div class="dashboard-card-heading"><div><h3>质量流程状态</h3><p>各指标直接关联当前业务模块，点击入口可继续处理或查看报表。</p></div><div class="dashboard-actions"><el-button @click="$router.push('/inspection')">进入成品测试</el-button><el-button @click="$router.push('/nonconformities')">进入不良品处理</el-button><el-button type="primary" @click="$router.push('/analytics')">进入质量报表</el-button></div></div></template>
      <div class="dashboard-flow-grid"><article><span>待检任务</span><strong>{{ summary.pendingInspectionTasks }}</strong></article><article class="attention"><span>进行中工单</span><strong>{{ summary.activeWorkOrders }}</strong></article><article class="good"><span>已完成检验</span><strong>{{ summary.completedInspectionTasks }}</strong></article><article class="good"><span>一次合格率</span><strong>{{ summary.firstPassRate.toFixed(2) }}%</strong></article></div>
    </el-card>

    <el-card shadow="never" class="content-card dashboard-chart-card dashboard-defect-card">
      <template #header><div class="dashboard-card-heading"><div><h3>不良缺陷分布</h3><p>按当前权限范围内的不合格检验记录汇总。</p></div><el-button @click="load">刷新看板</el-button></div></template>
      <div v-if="quality.defectRanking.length" ref="defectChartRef" class="dashboard-defect-chart" /><el-empty v-else description="当前暂无不良缺陷数据" />
    </el-card>
  </section>
</template>
