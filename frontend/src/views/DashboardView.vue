<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { use, init, type ECharts } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { qmsApi } from '@/api/qms'
import { formatDateTime } from '@/utils/display-text'
import type { DashboardSummary, EquipmentTelemetryRecord, QualitySummary } from '@/types/qms'

use([PieChart, LegendComponent, TooltipComponent, CanvasRenderer])

const loading = ref(true)
const summary = ref<DashboardSummary>()
const quality = ref<QualitySummary>()
const summaryError = ref('')
const qualityError = ref('')
const probeRecords = ref<EquipmentTelemetryRecord[]>([])
const probeError = ref('')
const latestProbeRecord = computed(() => probeRecords.value[0])
const businessUpdatedAt = ref<Date>()
const updatedAtLabel = computed(() => businessUpdatedAt.value ? formatDateTime(businessUpdatedAt.value) : loading.value ? '读取中' : '尚未完整读取')
const defectChartRef = ref<HTMLElement>()
let defectChart: ECharts | undefined

function renderCharts() {
  defectChart?.dispose()
  defectChart = undefined
  const currentQuality = quality.value
  if (defectChartRef.value && currentQuality?.defectRanking.length) {
    defectChart = init(defectChartRef.value)
    defectChart.setOption({
      color: ['#d92d4b', '#b77900', '#7a36df', '#1570ef', '#12b76a', '#f79009'],
      tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
      legend: { type: 'scroll', orient: 'vertical', right: 4, top: 'middle', icon: 'circle', textStyle: { color: '#344054' } },
      series: [{
        type: 'pie', radius: ['48%', '72%'], center: ['35%', '52%'], avoidLabelOverlap: true,
        label: { show: true, position: 'center', formatter: `{count|${currentQuality.failRecords}}\n{label|不合格}`, rich: { count: { color: '#101828', fontSize: 22, fontWeight: 700, lineHeight: 28 }, label: { color: '#667085', fontSize: 12 } } },
        labelLine: { show: false }, data: currentQuality.defectRanking.map((item) => ({ name: item.defectSummary || '未填写缺陷', value: item.failedRecords }))
      }]
    })
  }
}

function resizeCharts() { defectChart?.resize() }

async function load() {
  loading.value = true
  try {
    const [dashboardResult, qualityResult, probeResult] = await Promise.allSettled([
      qmsApi.getDashboardSummary(),
      qmsApi.getQualitySummary(),
      qmsApi.listEquipmentTelemetryHistory({ eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 1 })
    ])
    summaryError.value = dashboardResult.status === 'rejected' ? '质量流程状态读取失败，请刷新重试。' : ''
    qualityError.value = qualityResult.status === 'rejected' ? '检验记录与缺陷汇总读取失败，请刷新重试。' : ''
    summary.value = dashboardResult.status === 'fulfilled' ? dashboardResult.value : undefined
    quality.value = qualityResult.status === 'fulfilled' ? qualityResult.value : undefined
    if (probeResult.status === 'fulfilled') {
      probeRecords.value = probeResult.value.items
      probeError.value = ''
    } else {
      probeRecords.value = []
      probeError.value = '飞针机采集状态读取失败，请刷新重试。'
    }
    if (dashboardResult.status === 'fulfilled' && qualityResult.status === 'fulfilled') {
      businessUpdatedAt.value = new Date()
    }
    await nextTick()
    renderCharts()
  } catch {
    summary.value = undefined
    quality.value = undefined
    probeRecords.value = []
    summaryError.value = '质量业务数据处理失败，请刷新重试。'
    qualityError.value = summaryError.value
    probeError.value = '飞针机采集状态处理失败，请刷新重试。'
  } finally { loading.value = false }
}

onMounted(() => { window.addEventListener('resize', resizeCharts); void load() })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); defectChart?.dispose() })
</script>

<template>
  <section class="dashboard-page" v-loading="loading">
    <div class="dashboard-intro">
      <div><h2>质量总览</h2><p>同步汇总成品测试、待检任务、工序检验与不良处置数据，展示当前质量业务状态。</p></div>
      <span class="dashboard-updated" :class="{ 'dashboard-updated--unavailable': summaryError || qualityError || !businessUpdatedAt }"><i></i>最近完整更新 {{ updatedAtLabel }}</span>
    </div>

    <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" />
    <el-alert v-if="qualityError" type="error" :closable="false" :title="qualityError" />
    <div class="dashboard-metrics">
      <article class="dashboard-metric"><span>检验记录总数</span><strong>{{ quality ? quality.totalRecords.toLocaleString() : '—' }}</strong><small>当前权限范围内的已提交记录</small></article>
      <article class="dashboard-metric success"><span>一次合格率</span><strong>{{ quality ? `${quality.firstPassRate.toFixed(2)}%` : '—' }}</strong><small>合格记录数 ÷ 检验记录总数</small></article>
      <article class="dashboard-metric danger"><span>待检数量</span><strong>{{ summary ? summary.pendingInspectionTasks.toLocaleString() : '—' }}</strong><small>尚未完成的检验任务</small></article>
      <article class="dashboard-metric success"><span>已完成检验</span><strong>{{ summary ? summary.completedInspectionTasks.toLocaleString() : '—' }}</strong><small>累计完成的检验任务</small></article>
    </div>

    <el-card shadow="never" class="content-card dashboard-probe-card">
      <template #header><div class="dashboard-card-heading"><div><h3>飞针机采集状态</h3><p>展示最新一条设备计数快照；正式 FQC 检验按已提交记录统计。</p></div><el-button @click="$router.push({ path: '/inspection', query: { panel: 'records' } })">查看设备记录</el-button></div></template>
      <el-alert v-if="probeError" :title="probeError" type="error" :closable="false" />
      <div v-else-if="latestProbeRecord" class="dashboard-probe-snapshot">
        <div><span>检测设备</span><strong>{{ latestProbeRecord.equipmentCode }}</strong></div>
        <div><span>料号</span><strong>{{ latestProbeRecord.materialNo || '—' }}</strong></div>
        <div><span>已测</span><strong>{{ latestProbeRecord.machiningCount ?? '—' }}</strong></div>
        <div><span>好板</span><strong class="good-text">{{ latestProbeRecord.goodBoardCount ?? '—' }}</strong></div>
        <div><span>坏板</span><strong class="bad-text">{{ latestProbeRecord.badBoardCount ?? '—' }}</strong></div>
        <div><span>QMS 接收时间</span><strong>{{ formatDateTime(latestProbeRecord.receivedAt) }}</strong></div>
      </div>
      <el-empty v-else description="当前查询范围内暂无飞针机采集记录" />
    </el-card>

    <el-card shadow="never" class="content-card dashboard-flow-card">
      <template #header><div class="dashboard-card-heading"><div><h3>质量流程状态</h3><p>各指标直接关联当前业务模块，点击入口可继续处理或查看报表。</p></div><div class="dashboard-actions"><el-button @click="$router.push('/inspection')">进入成品测试</el-button><el-button @click="$router.push('/nonconformities')">进入不良品处理</el-button><el-button type="primary" @click="$router.push('/analytics')">进入质量报表</el-button></div></div></template>
      <div class="dashboard-flow-grid"><article><span>待检任务</span><strong>{{ summary?.pendingInspectionTasks ?? '—' }}</strong></article><article class="attention"><span>进行中工单</span><strong>{{ summary?.activeWorkOrders ?? '—' }}</strong></article><article class="good"><span>已完成检验</span><strong>{{ summary?.completedInspectionTasks ?? '—' }}</strong></article><article class="good"><span>一次合格率</span><strong>{{ summary ? `${summary.firstPassRate.toFixed(2)}%` : '—' }}</strong></article></div>
    </el-card>

    <el-card shadow="never" class="content-card dashboard-chart-card dashboard-defect-card">
      <template #header><div class="dashboard-card-heading"><div><h3>不良缺陷分布</h3><p>按当前权限范围内的不合格检验记录汇总。</p></div><el-button @click="load">刷新看板</el-button></div></template>
      <div v-if="quality?.defectRanking.length" ref="defectChartRef" class="dashboard-defect-chart" /><el-empty v-else :description="qualityError ? '缺陷数据暂不可用' : '当前暂无不良缺陷数据'" />
    </el-card>
  </section>
</template>

<style scoped>
.dashboard-updated--unavailable { color: #d92d20; }
.dashboard-probe-snapshot { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.dashboard-probe-snapshot > div { display: grid; gap: 6px; min-width: 0; padding: 12px 14px; border-radius: 8px; background: #f5faff; }
.dashboard-probe-snapshot span { color: #667085; font-size: 12px; }
.dashboard-probe-snapshot strong { overflow: hidden; color: #344054; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-probe-snapshot strong.good-text { color: #039855; }
.dashboard-probe-snapshot strong.bad-text { color: #d92d4b; }
@media (max-width: 1400px) { .dashboard-probe-snapshot { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 700px) { .dashboard-probe-snapshot { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
