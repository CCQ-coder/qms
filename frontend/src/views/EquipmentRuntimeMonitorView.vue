<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime, legacyStateText, statusText } from '@/utils/display-text'
import { clampPage, paginateRows } from '@/utils/pagination'
import { formatHostCapacity, formatHostUptime, isHostOnline } from '@/utils/host-status'
import type { Equipment, EquipmentGatewayMapping, EquipmentGatewayUnmatchedMessage, EquipmentRuntimeStatus, EquipmentTelemetryRecord, GatewayHostStatus } from '@/types/qms'

const parameterKeys = {
  refreshSeconds: 'equipment.monitor.auto-refresh-seconds',
  offlineSeconds: 'equipment.monitor.offline-after-seconds',
  displayColumns: 'equipment.monitor.display-columns',
  displayRows: 'equipment.monitor.display-rows',
  historyLimit: 'equipment.monitor.history-page-size',
  historyDays: 'equipment.monitor.history-days',
  gatewayHost: 'equipment.btf.gateway-host',
  gatewayPort: 'equipment.btf.gateway-port',
  gatewayPath: 'equipment.btf.gateway-path',
  gatewayMethod: 'equipment.btf.gateway-method',
  reportIntervalSeconds: 'equipment.btf.report-interval-seconds'
} as const

const loading = ref(true)
const refreshing = ref(false)
const configurationLoading = ref(false)
const configurationSaving = ref(false)
const configurationDialog = ref(false)
const mappingDialog = ref(false)
const mappingSaving = ref(false)
const mappingFormRef = ref<FormInstance>()
const selectedEquipmentId = ref('')
const selectedRecord = ref<EquipmentTelemetryRecord>()
const selectedUnmatchedMessage = ref<EquipmentGatewayUnmatchedMessage>()
const rawPayloadDialog = ref(false)
const unmatchedPayloadDialog = ref(false)
const runtimeStatuses = ref<EquipmentRuntimeStatus[]>([])
const telemetryRecords = ref<EquipmentTelemetryRecord[]>([])
const hostStatuses = ref<GatewayHostStatus[]>([])
const hostStatusError = ref(false)
const mappings = ref<EquipmentGatewayMapping[]>([])
const unmatchedMessages = ref<EquipmentGatewayUnmatchedMessage[]>([])
const equipment = ref<Equipment[]>([])
const mappingForm = reactive({ equipmentId: '', machineName: '' })
const config = reactive({
  refreshSeconds: 5,
  offlineSeconds: 300,
  displayColumns: 2,
  displayRows: 2,
  historyLimit: 100,
  historyDays: 7,
  gatewayHost: window.location.hostname,
  gatewayPort: '8080',
  gatewayPath: '/hessian/monitor',
  gatewayMethod: 'Monitor',
  reportIntervalSeconds: 1
})
let refreshTimer: number | undefined

const mappingRules: FormRules = {
  equipmentId: [{ required: true, message: '请选择 QMS 设备', trigger: 'change' }],
  machineName: [{ required: true, message: '请输入设备名或电脑名', trigger: 'blur' }]
}

const equipmentOptions = computed(() => runtimeStatuses.value.map((item) => ({
  id: item.equipmentId,
  label: `${item.equipmentCode} · ${item.equipmentName}`
})))
const visibleRuntimeStatuses = computed(() => selectedEquipmentId.value
  ? runtimeStatuses.value.filter((item) => item.equipmentId === selectedEquipmentId.value)
  : runtimeStatuses.value)
// History is returned newest-first. The data-flow cards are a live equipment overview,
// so show only the newest record for each device; the full sequence remains below.
const flowRecords = computed(() => {
  const displayedEquipmentIds = new Set<string>()
  return telemetryRecords.value
    .filter((record) => {
      if (displayedEquipmentIds.has(record.equipmentId)) return false
      displayedEquipmentIds.add(record.equipmentId)
      return true
    })
})
const flowPage = ref(1)
const flowPageSize = computed(() => config.displayRows * config.displayColumns)
const visibleFlowRecords = computed(() => paginateRows(flowRecords.value, flowPage.value, flowPageSize.value))
watch([() => flowRecords.value.length, flowPageSize], ([count, size]) => {
  flowPage.value = clampPage(flowPage.value, count, size)
})
const onlineCount = computed(() => runtimeStatuses.value.filter((item) => item.online).length)
const alarmCount = computed(() => runtimeStatuses.value.filter((item) => item.effectiveStatus === 'ALARM').length)
const endpoint = computed(() => {
  const host = config.gatewayHost.trim() || window.location.hostname
  const port = config.gatewayPort.trim() || '8080'
  return `http://${host}:${port}${normalizePath(config.gatewayPath)}`
})
const sourceTimeWindow = computed(() => `最近 ${config.historyDays} 天`)

function integerValue(value: string, fallback: number, minimum: number, maximum: number) {
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) ? Math.min(maximum, Math.max(minimum, parsed)) : fallback
}

function normalizePath(value: string) {
  const path = value.trim() || '/hessian/monitor'
  return path.startsWith('/') ? path : `/${path}`
}

function localDateTimeBefore(days: number) {
  const time = new Date()
  time.setDate(time.getDate() - days)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${time.getFullYear()}-${pad(time.getMonth() + 1)}-${pad(time.getDate())}T${pad(time.getHours())}:${pad(time.getMinutes())}:${pad(time.getSeconds())}`
}

function statusType(status?: string) {
  return ({ ALARM: 'danger', PROCESSING: 'success', STOPPED: 'warning', OFFLINE: 'info' })[status ?? ''] ?? 'info'
}

function runtimeStatusText(item: EquipmentRuntimeStatus) {
  const latest = telemetryRecords.value.find((record) => record.equipmentId === item.equipmentId)
  return latest?.legacyState ? legacyStateText(latest.legacyState) : statusText(item.effectiveStatus)
}

function telemetryStatusText(item: EquipmentTelemetryRecord) {
  return item.legacyState ? legacyStateText(item.legacyState) : statusText(item.status)
}

function formatRawPayload(item?: { rawPayload?: string }) {
  if (!item?.rawPayload) return '暂无原始报文。'
  try {
    return JSON.stringify(JSON.parse(item.rawPayload), null, 2)
  } catch {
    return item.rawPayload
  }
}

async function loadConfiguration() {
  configurationLoading.value = true
  try {
    const values = await Promise.all(Object.values(parameterKeys).map(async (key) => (await qmsApi.getTenantParameter(key)).value))
    const [refreshSeconds, offlineSeconds, displayColumns, displayRows, historyLimit, historyDays,
      gatewayHost, gatewayPort, gatewayPath, gatewayMethod, reportIntervalSeconds] = values
    Object.assign(config, {
      refreshSeconds: integerValue(refreshSeconds, 5, 3, 60),
      offlineSeconds: integerValue(offlineSeconds, 300, 30, 86_400),
      displayColumns: integerValue(displayColumns, 2, 1, 4),
      displayRows: integerValue(displayRows, 2, 1, 6),
      historyLimit: integerValue(historyLimit, 100, 20, 300),
      historyDays: integerValue(historyDays, 7, 1, 90),
      gatewayHost: gatewayHost || window.location.hostname,
      gatewayPort: gatewayPort || '8080',
      gatewayPath: gatewayPath || '/hessian/monitor',
      gatewayMethod: gatewayMethod || 'Monitor',
      reportIntervalSeconds: integerValue(reportIntervalSeconds, 1, 1, 600)
    })
  } catch {
    ElMessage.warning('未读取到设备接入自定义配置，已使用默认值')
  } finally {
    configurationLoading.value = false
  }
}

async function loadMappings() {
  try {
    const [mappingRows, equipmentRows, unmatchedRows] = await Promise.all([
      qmsApi.listEquipmentGatewayMappings(), qmsApi.listEquipment(), qmsApi.listEquipmentGatewayUnmatchedMessages()
    ])
    mappings.value = mappingRows
    equipment.value = equipmentRows
    unmatchedMessages.value = unmatchedRows
  } catch {
    // Operators may view the data flow without administrator permission to alter mappings.
    mappings.value = []
    equipment.value = []
    unmatchedMessages.value = []
  }
}

async function loadLiveData(initial = false) {
  if (initial) loading.value = true
  else refreshing.value = true
  try {
    const [statuses, history] = await Promise.all([
      qmsApi.listEquipmentRuntimeStatuses(),
      qmsApi.listEquipmentTelemetryHistory({
        equipmentId: selectedEquipmentId.value || undefined,
        fromTime: localDateTimeBefore(config.historyDays),
        limit: config.historyLimit
      })
    ])
    runtimeStatuses.value = statuses
    telemetryRecords.value = history.items
    selectedRecord.value = selectedRecord.value
      ? history.items.find((item) => item.id === selectedRecord.value?.id) ?? history.items[0]
      : history.items[0]
  } catch {
    if (initial) ElMessage.error('精雕设备数据读取失败，请确认接收服务已启动')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function loadHostStatuses() {
  try {
    hostStatuses.value = await qmsApi.listGatewayHostStatuses()
    hostStatusError.value = false
  } catch {
    hostStatusError.value = true
  }
}

async function refreshAll() {
  await Promise.all([loadLiveData(), loadMappings(), loadHostStatuses()])
}

function restartAutoRefresh() {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => { void Promise.all([loadLiveData(), loadHostStatuses()]) }, config.refreshSeconds * 1000)
}

async function saveConfiguration() {
  configurationSaving.value = true
  try {
    config.gatewayPath = normalizePath(config.gatewayPath)
    await Promise.all([
      qmsApi.updateTenantParameter(parameterKeys.refreshSeconds, String(config.refreshSeconds)),
      qmsApi.updateTenantParameter(parameterKeys.offlineSeconds, String(config.offlineSeconds)),
      qmsApi.updateTenantParameter(parameterKeys.displayColumns, String(config.displayColumns)),
      qmsApi.updateTenantParameter(parameterKeys.displayRows, String(config.displayRows)),
      qmsApi.updateTenantParameter(parameterKeys.historyLimit, String(config.historyLimit)),
      qmsApi.updateTenantParameter(parameterKeys.historyDays, String(config.historyDays)),
      qmsApi.updateTenantParameter(parameterKeys.gatewayHost, config.gatewayHost.trim() || window.location.hostname),
      qmsApi.updateTenantParameter(parameterKeys.gatewayPort, config.gatewayPort.trim() || '8080'),
      qmsApi.updateTenantParameter(parameterKeys.gatewayPath, config.gatewayPath),
      qmsApi.updateTenantParameter(parameterKeys.gatewayMethod, config.gatewayMethod.trim() || 'Monitor'),
      qmsApi.updateTenantParameter(parameterKeys.reportIntervalSeconds, String(config.reportIntervalSeconds))
    ])
    restartAutoRefresh()
    configurationDialog.value = false
    await loadLiveData()
    ElMessage.success('设备数据接入配置已保存并生效')
  } catch {
    ElMessage.error('配置保存失败，请确认当前账号为租户管理员')
  } finally {
    configurationSaving.value = false
  }
}

function openMapping(machineName = '') {
  Object.assign(mappingForm, { equipmentId: equipment.value[0]?.id ?? '', machineName })
  mappingDialog.value = true
}

async function saveMapping() {
  const valid = await mappingFormRef.value?.validate().catch(() => false)
  if (!valid) return
  mappingSaving.value = true
  try {
    await qmsApi.createEquipmentGatewayMapping({ equipmentId: mappingForm.equipmentId, machineName: mappingForm.machineName.trim() })
    mappingDialog.value = false
    await loadMappings()
    ElMessage.success('设备映射已保存，下一次现场上报将自动进入对应设备的数据流')
  } catch {
    ElMessage.error('设备映射保存失败，请检查上报设备名是否重复')
  } finally {
    mappingSaving.value = false
  }
}

function inspectRecord(record: EquipmentTelemetryRecord) {
  selectedRecord.value = record
  rawPayloadDialog.value = true
}

function inspectUnmatchedMessage(message: EquipmentGatewayUnmatchedMessage) {
  selectedUnmatchedMessage.value = message
  unmatchedPayloadDialog.value = true
}

watch(selectedEquipmentId, () => { flowPage.value = 1; void loadLiveData() })

onMounted(async () => {
  await loadConfiguration()
  await Promise.all([loadLiveData(true), loadMappings(), loadHostStatuses()])
  restartAutoRefresh()
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
})
</script>

<template>
  <section class="page-stack equipment-data-workbench" v-loading="loading">
    <div class="hero-card data-workbench-hero">
      <div><p class="eyebrow">现场接入 · 实时可追溯</p><h2>精雕/锣机设备数据流工作台</h2><p>查看每一条现场数据如何经过接收、设备映射并进入 QMS；支持在本页直接维护运行与接入配置。</p></div>
      <div class="data-workbench-actions"><el-button plain @click="openMapping">新增设备映射</el-button><el-button type="primary" @click="configurationDialog = true">接入配置</el-button></div>
    </div>

    <el-card shadow="never" class="content-card data-flow-card">
      <template #header><div class="card-header"><span>实时数据流</span><div class="data-flow-toolbar"><el-select v-model="selectedEquipmentId" clearable filterable placeholder="全部映射设备" style="width: 240px"><el-option v-for="item in equipmentOptions" :key="item.id" :label="item.label" :value="item.id" /></el-select><el-button :loading="refreshing" @click="refreshAll">刷新</el-button></div></div></template>
      <el-alert title="数据来自现场旧监控软件的 Hessian Monitor 上报；只有已配置映射的设备会入库。来源 IP 白名单由部署安全配置维护。" type="info" :closable="false" show-icon />
      <div class="flow-overview">
        <article class="flow-stage"><span class="flow-stage-number">1</span><div><small>现场精雕/锣机</small><strong>{{ telemetryRecords.length }} 条近期上报</strong><em>上报周期：{{ config.reportIntervalSeconds }} 秒</em></div></article><span class="flow-arrow">→</span>
        <article class="flow-stage"><span class="flow-stage-number">2</span><div><small>QMS 接收端</small><strong>{{ endpoint }}</strong><em>方法：{{ config.gatewayMethod }}</em></div></article><span class="flow-arrow">→</span>
        <article class="flow-stage"><span class="flow-stage-number">3</span><div><small>设备映射</small><strong>{{ mappings.length }} 台已配置 · {{ unmatchedMessages.length }} 台待配置</strong><em>将 machinename 匹配到设备台账</em></div></article><span class="flow-arrow">→</span>
        <article class="flow-stage"><span class="flow-stage-number">4</span><div><small>QMS 业务状态</small><strong>{{ onlineCount }} 台在线 · {{ alarmCount }} 台告警</strong><em>离线阈值：{{ config.offlineSeconds }} 秒</em></div></article>
      </div>
      <div class="flow-record-heading"><strong>各设备最新数据</strong><span>展示 {{ visibleFlowRecords.length }} 台设备的最新上报；历史查询范围：{{ sourceTimeWindow }}</span></div>
      <div v-if="visibleFlowRecords.length" class="live-flow-grid" :style="{ gridTemplateColumns: `repeat(${config.displayColumns}, minmax(0, 1fr))` }">
        <button v-for="record in visibleFlowRecords" :key="record.id" class="live-flow-record" type="button" @click="inspectRecord(record)"><div class="live-flow-record-head"><strong>{{ record.machineName || '未命名设备' }}</strong><el-tag size="small" :type="statusType(record.status)">{{ telemetryStatusText(record) }}</el-tag></div><div class="live-flow-route"><span>{{ record.sourceUsername || '现场监控' }}</span><b>→</b><span>{{ record.equipmentCode }} · {{ record.equipmentName }}</span><b>→</b><span>QMS</span></div><dl><div><dt>设备时间</dt><dd>{{ formatDateTime(record.deviceTime) }}</dd></div><div><dt>接收时间</dt><dd>{{ formatDateTime(record.receivedAt) }}</dd></div><div><dt>程序 / 工单</dt><dd>{{ record.programNo || '—' }}</dd></div><div><dt>进度 / 完成数</dt><dd>{{ record.progressPercent == null ? '—' : `${record.progressPercent}%` }} / {{ record.machiningCount ?? '—' }}</dd></div></dl><span class="inspect-hint">点击查看原始报文与解析结果</span></button>
      </div>
      <div v-if="flowRecords.length" class="monitor-pagination"><el-pagination v-model:current-page="flowPage" :page-size="flowPageSize" :total="flowRecords.length" background layout="total, prev, pager, next" /></div>
      <el-empty v-else description="暂无已入库现场数据；请先添加设备映射并确认旧监控软件正在上报" :image-size="72" />
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>设备端与采集电脑状态</span><small>飞针机按配置的设备编号区分，SCBGX 代表监听电脑；超过 {{ config.offlineSeconds }} 秒未收到新状态显示离线。</small></div></template>
      <el-alert v-if="hostStatusError" title="电脑状态读取失败，请检查 QMS 接收接口" type="warning" :closable="false" show-icon />
      <paged-table :data="hostStatuses" empty-text="暂无电脑状态上报">
        <el-table-column prop="hostName" label="来源标识" min-width="125" />
        <el-table-column label="QMS 设备" min-width="170"><template #default="scope">{{ scope.row.equipmentCode ? `${scope.row.equipmentCode} · ${scope.row.equipmentName}` : '未映射' }}</template></el-table-column>
        <el-table-column label="状态" min-width="95"><template #default="scope"><el-tag :type="isHostOnline(scope.row.receivedAt, Date.now(), config.offlineSeconds) ? 'success' : 'info'">{{ isHostOnline(scope.row.receivedAt, Date.now(), config.offlineSeconds) ? '在线' : '离线' }}</el-tag></template></el-table-column>
        <el-table-column label="CPU" min-width="90"><template #default="scope">{{ scope.row.cpuPercent == null ? '采样中' : `${scope.row.cpuPercent}%` }}</template></el-table-column>
        <el-table-column label="可用 / 总内存" min-width="160"><template #default="scope">{{ formatHostCapacity(scope.row.memoryAvailableBytes, scope.row.memoryTotalBytes) }}</template></el-table-column>
        <el-table-column label="系统盘剩余 / 总量" min-width="175"><template #default="scope">{{ formatHostCapacity(scope.row.systemDiskFreeBytes, scope.row.systemDiskTotalBytes) }}</template></el-table-column>
        <el-table-column label="开机时长" min-width="125"><template #default="scope">{{ formatHostUptime(scope.row.uptimeSeconds) }}</template></el-table-column>
        <el-table-column label="最后接收时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.receivedAt) }}</template></el-table-column>
      </paged-table>
    </el-card>

    <el-card v-if="unmatchedMessages.length" shadow="never" class="content-card unmatched-message-card">
      <template #header><div class="card-header"><span>已接收、待映射的现场数据</span><small>这些数据已安全到达 QMS；选择“配置映射”即可让后续上报自动进入设备台账。</small></div></template>
      <paged-table :data="unmatchedMessages" empty-text="暂无待映射现场数据">
        <el-table-column prop="machineName" label="现场设备名" min-width="150" />
        <el-table-column prop="sourceUsername" label="来源用户" min-width="130" />
        <el-table-column label="最后设备时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.lastDeviceTime) }}</template></el-table-column>
        <el-table-column label="最后接收时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.lastReceivedAt) }}</template></el-table-column>
        <el-table-column prop="occurrenceCount" label="已接收次数" min-width="110" />
        <el-table-column label="操作" width="180" fixed="right"><template #default="scope"><el-button type="primary" link @click="inspectUnmatchedMessage(scope.row)">查看报文</el-button><el-button type="success" link @click="openMapping(scope.row.machineName)">配置映射</el-button></template></el-table-column>
      </paged-table>
    </el-card>

    <el-card shadow="never" class="content-card"><template #header><div class="card-header"><span>设备实时状态</span><small>按配置的离线阈值实时判定；修改阈值后无需重启服务。</small></div></template><paged-table :key="selectedEquipmentId" :data="visibleRuntimeStatuses" empty-text="暂无设备实时状态"><el-table-column prop="equipmentCode" label="设备编号" min-width="125" /><el-table-column prop="equipmentName" label="设备名称" min-width="150" /><el-table-column label="状态" min-width="105"><template #default="scope"><el-tag :type="statusType(scope.row.effectiveStatus)">{{ runtimeStatusText(scope.row) }}</el-tag></template></el-table-column><el-table-column prop="programNo" label="当前程序" min-width="160" show-overflow-tooltip /><el-table-column prop="operatorName" label="操作员" min-width="115" /><el-table-column prop="machiningCount" label="加工数量" min-width="105" /><el-table-column label="进度" min-width="90"><template #default="scope">{{ scope.row.progressPercent == null ? '—' : `${scope.row.progressPercent}%` }}</template></el-table-column><el-table-column prop="spindleStatus" label="主轴状态" min-width="105" /><el-table-column label="最后接收时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.lastReceivedAt) }}</template></el-table-column></paged-table></el-card>

    <el-card shadow="never" class="content-card"><template #header><div class="card-header"><span>近期上报明细</span><small>每条数据均可展开查看来源字段、解析后的结构化值和原始 JSON。</small></div></template><paged-table :key="selectedEquipmentId" :data="telemetryRecords" empty-text="暂无设备上报历史"><el-table-column label="接收时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.receivedAt) }}</template></el-table-column><el-table-column prop="sourceUsername" label="来源用户" min-width="110" /><el-table-column prop="machineName" label="上报设备名" min-width="145" /><el-table-column prop="equipmentCode" label="QMS 设备" min-width="125" /><el-table-column label="状态" min-width="100"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ telemetryStatusText(scope.row) }}</el-tag></template></el-table-column><el-table-column prop="eventType" label="事件类型" min-width="170" show-overflow-tooltip /><el-table-column prop="programNo" label="程序" min-width="150" show-overflow-tooltip /><el-table-column label="进度 / 完成数" min-width="135"><template #default="scope">{{ scope.row.progressPercent == null ? '—' : `${scope.row.progressPercent}%` }} / {{ scope.row.machiningCount ?? '—' }}</template></el-table-column><el-table-column label="操作" width="100" fixed="right"><template #default="scope"><el-button type="primary" link @click="inspectRecord(scope.row)">查看数据</el-button></template></el-table-column></paged-table></el-card>

    <el-dialog v-model="configurationDialog" title="精雕/锣机接入配置" width="min(780px, 94vw)" :close-on-click-modal="false" v-loading="configurationLoading"><el-alert title="“现场上报地址”用于复制到旧监控客户端；QMS 服务监听端口由部署配置管理，保存地址不会更改服务器端口。刷新、离线判定、展示数量和历史范围会立即生效。" type="warning" :closable="false" show-icon /><el-tabs style="margin-top: 18px"><el-tab-pane label="数据展示与判定"><el-form label-position="top" class="config-form-grid"><el-form-item label="自动刷新间隔（秒）"><el-input-number v-model="config.refreshSeconds" :min="3" :max="60" :step="1" /></el-form-item><el-form-item label="设备离线阈值（秒）"><el-input-number v-model="config.offlineSeconds" :min="30" :max="86400" :step="30" /></el-form-item><el-form-item label="实时数据展示列数"><el-input-number v-model="config.displayColumns" :min="1" :max="4" :step="1" /></el-form-item><el-form-item label="实时数据展示行数"><el-input-number v-model="config.displayRows" :min="1" :max="6" :step="1" /></el-form-item><el-form-item label="上报明细读取条数"><el-input-number v-model="config.historyLimit" :min="20" :max="300" :step="10" /></el-form-item><el-form-item label="历史查询范围（天）"><el-input-number v-model="config.historyDays" :min="1" :max="90" :step="1" /></el-form-item></el-form></el-tab-pane><el-tab-pane label="现场上报参数"><el-form label-position="top" class="config-form-grid"><el-form-item label="QMS 服务地址"><el-input v-model="config.gatewayHost" placeholder="例如：192.168.1.71" /></el-form-item><el-form-item label="QMS 服务端口"><el-input v-model="config.gatewayPort" inputmode="numeric" placeholder="8080" /></el-form-item><el-form-item label="接口路径"><el-input v-model="config.gatewayPath" placeholder="/hessian/monitor" /></el-form-item><el-form-item label="调用方法"><el-input v-model="config.gatewayMethod" placeholder="Monitor" /></el-form-item><el-form-item label="现场上报间隔（秒）"><el-input-number v-model="config.reportIntervalSeconds" :min="1" :max="600" :step="1" /></el-form-item></el-form><div class="endpoint-preview"><span>供现场端填写</span><code>{{ endpoint }}</code><small>方法：{{ config.gatewayMethod }}；建议上报间隔：{{ config.reportIntervalSeconds }} 秒</small></div></el-tab-pane></el-tabs><template #footer><el-button @click="configurationDialog = false">取消</el-button><el-button type="primary" :loading="configurationSaving" @click="saveConfiguration">保存配置</el-button></template></el-dialog>

    <el-dialog v-model="mappingDialog" title="新增现场设备映射" width="min(560px, 94vw)" :close-on-click-modal="false"><el-alert title="填写现场上报的 machinename、飞针机配置中的 device_id，或 SCBGX 电脑的 hostName；电脑状态会关联到对应的 QMS 设备。" type="info" :closable="false" show-icon /><el-form ref="mappingFormRef" :model="mappingForm" :rules="mappingRules" label-position="top" style="margin-top: 18px"><el-form-item label="QMS 设备" prop="equipmentId"><el-select v-model="mappingForm.equipmentId" filterable class="full-width" placeholder="请选择设备台账"><el-option v-for="item in equipment" :key="item.id" :label="`${item.assetNo} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item label="来源标识（machinename / device_id / hostName）" prop="machineName"><el-input v-model="mappingForm.machineName" maxlength="160" show-word-limit placeholder="例如：飞针机01 或 SCBGX" /><div class="field-help">名称会自动忽略英文大小写和首尾空格；同一名称不可映射到多个设备。</div></el-form-item></el-form><template #footer><el-button @click="mappingDialog = false">取消</el-button><el-button type="primary" :loading="mappingSaving" @click="saveMapping">保存映射</el-button></template></el-dialog>

    <el-dialog v-model="rawPayloadDialog" title="设备上报数据详情" width="min(920px, 96vw)"><div v-if="selectedRecord" class="record-detail-grid"><div><span>数据流</span><strong>{{ selectedRecord.sourceUsername || '现场监控' }} → {{ selectedRecord.machineName || '未命名设备' }} → {{ selectedRecord.equipmentCode }} · {{ selectedRecord.equipmentName }}</strong></div><div><span>解析状态</span><strong>{{ telemetryStatusText(selectedRecord) }} · {{ selectedRecord.eventType || '—' }}</strong></div><div><span>设备时间</span><strong>{{ formatDateTime(selectedRecord.deviceTime) }}</strong></div><div><span>QMS 接收时间</span><strong>{{ formatDateTime(selectedRecord.receivedAt) }}</strong></div></div><el-table v-if="selectedRecord" :data="[selectedRecord]" border style="margin-top: 18px"><el-table-column prop="programNo" label="程序" min-width="160" /><el-table-column prop="operatorName" label="操作员" min-width="120" /><el-table-column prop="machiningCount" label="完成数量" min-width="110" /><el-table-column label="进度" min-width="95"><template #default="scope">{{ scope.row.progressPercent == null ? '—' : `${scope.row.progressPercent}%` }}</template></el-table-column><el-table-column prop="spindleStatus" label="主轴状态" min-width="120" /><el-table-column prop="screenText" label="屏幕/报警文字" min-width="180" show-overflow-tooltip /></el-table><div class="raw-payload"><span>原始入库报文（已脱敏、结构化保存）</span><pre>{{ formatRawPayload(selectedRecord) }}</pre></div><template #footer><el-button type="primary" @click="rawPayloadDialog = false">关闭</el-button></template></el-dialog>
    <el-dialog v-model="unmatchedPayloadDialog" title="待映射现场报文" width="min(860px, 96vw)"><div v-if="selectedUnmatchedMessage" class="record-detail-grid"><div><span>来源用户</span><strong>{{ selectedUnmatchedMessage.sourceUsername || '—' }}</strong></div><div><span>现场设备名</span><strong>{{ selectedUnmatchedMessage.machineName || '—' }}</strong></div><div><span>最后设备时间</span><strong>{{ formatDateTime(selectedUnmatchedMessage.lastDeviceTime) }}</strong></div><div><span>已接收次数</span><strong>{{ selectedUnmatchedMessage.occurrenceCount }}</strong></div></div><div class="raw-payload"><span>原始接收报文</span><pre>{{ formatRawPayload(selectedUnmatchedMessage) }}</pre></div><template #footer><el-button @click="unmatchedPayloadDialog = false">关闭</el-button><el-button type="primary" @click="unmatchedPayloadDialog = false; openMapping(selectedUnmatchedMessage?.machineName)">使用此设备名配置映射</el-button></template></el-dialog>
  </section>
</template>

<style scoped>
.data-workbench-hero, .data-workbench-actions, .data-flow-toolbar, .flow-overview, .live-flow-record-head, .live-flow-route, .flow-record-heading, .record-detail-grid, .endpoint-preview { display: flex; }
.data-workbench-hero { align-items: center; justify-content: space-between; gap: 24px; }.data-workbench-actions, .data-flow-toolbar { flex-wrap: wrap; gap: 10px; }.flow-overview { align-items: stretch; gap: 12px; margin: 20px 0; overflow-x: auto; }.flow-stage { display: flex; flex: 1 0 190px; gap: 11px; min-width: 190px; padding: 14px; border: 1px solid #cfe4f5; border-radius: 10px; background: #f8fcff; }.flow-stage-number { display: grid; flex: 0 0 27px; place-items: center; width: 27px; height: 27px; border-radius: 50%; color: #fff; font-weight: 700; background: #2f93e8; }.flow-stage div { display: grid; gap: 4px; min-width: 0; }.flow-stage small, .flow-stage em { color: #718096; font-size: 12px; font-style: normal; }.flow-stage strong { overflow: hidden; color: #214e78; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.flow-arrow { display: grid; place-items: center; flex: 0 0 auto; color: #70b7e9; font-size: 24px; }
.flow-record-heading { align-items: center; justify-content: space-between; gap: 12px; margin: 22px 0 12px; color: #6c849a; font-size: 12px; }.flow-record-heading strong { color: #214e78; font-size: 14px; }.live-flow-grid { display: grid; gap: 12px; }.live-flow-record { display: grid; gap: 11px; min-width: 0; padding: 15px; border: 1px solid #d5e8f6; border-radius: 10px; color: inherit; text-align: left; background: #fbfdff; cursor: pointer; transition: border-color .18s ease, box-shadow .18s ease; }.live-flow-record:hover { border-color: #55a9e6; box-shadow: 0 6px 16px rgb(47 147 232 / 12%); }.live-flow-record-head { align-items: center; justify-content: space-between; gap: 10px; }.live-flow-record-head strong { overflow: hidden; color: #1f4f7d; text-overflow: ellipsis; white-space: nowrap; }.live-flow-route { flex-wrap: wrap; align-items: center; gap: 6px; color: #4c789d; font-size: 12px; }.live-flow-route b { color: #2f93e8; }.live-flow-record dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 0; }.live-flow-record dl div { min-width: 0; }.live-flow-record dt { color: #8295a8; font-size: 11px; }.live-flow-record dd { overflow: hidden; margin: 3px 0 0; color: #315b80; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.inspect-hint { color: #2f93e8; font-size: 12px; }
.config-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }.config-form-grid :deep(.el-input-number) { width: 100%; }.endpoint-preview { flex-direction: column; gap: 8px; padding: 14px; border: 1px solid #cfe4f5; border-radius: 9px; background: #f7fcff; }.endpoint-preview span, .endpoint-preview small { color: #718096; font-size: 12px; }.endpoint-preview code { color: #176eaf; font-weight: 650; }.record-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }.record-detail-grid > div { display: grid; gap: 4px; padding: 11px; border-radius: 8px; background: #f7fbfe; }.record-detail-grid span { color: #718096; font-size: 12px; }.record-detail-grid strong { color: #315b80; font-size: 13px; }.raw-payload { display: grid; gap: 8px; margin-top: 18px; }.raw-payload > span { color: #496f90; font-size: 13px; font-weight: 650; }.raw-payload pre { max-height: 330px; margin: 0; padding: 14px; overflow: auto; border-radius: 8px; color: #d6ecff; background: #102a43; font: 12px/1.55 Consolas, 'Courier New', monospace; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 900px) { .data-workbench-hero { align-items: flex-start; flex-direction: column; }.flow-arrow { display: none; }.flow-overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); overflow: visible; }.flow-stage { min-width: 0; }.config-form-grid, .record-detail-grid { grid-template-columns: 1fr; } } @media (max-width: 600px) { .flow-overview { grid-template-columns: 1fr; }.live-flow-record dl { grid-template-columns: 1fr; } }
</style>
