<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { loadMenuAccess, menuAccess } from '@/navigation/menu'
import { equipmentStatusTagType, formatDate, formatDateTime, statusText, taskTypeText } from '@/utils/display-text'
import { clampPage, paginateRows } from '@/utils/pagination'
import { formatHostCapacity, formatHostUptime } from '@/utils/host-status'
import { createClientRequestId, listInspectionDrafts, removeInspectionDraft, saveInspectionDraft, type OfflineInspectionDraft, type OfflineInspectionPayload } from '@/offline/inspectionDraftStore'
import { deriveInspectionMonitorStatus, inspectionDeviceOnline, monitorStatusLabel } from './inspectionMonitorStatus'
import { flyingProbePassRate, useTelemetryHistory } from './useTelemetryHistory'
import { groupFinishedTestSummary, type SummaryDimension } from './finishedTestSummary'
import type { Equipment, EquipmentRuntimeStatus, EquipmentTelemetryRecord, FlyingProbeRetryableProjection, GatewayHostStatus, FinishedTestDeviceSummary, FinishedTestSettlement, InspectionTask, InspectionTemplate, WorkOrder } from '@/types/qms'

const networkOnline = ref(navigator.onLine)
const backendReachable = ref(true)
const tasks = ref<InspectionTask[]>([])
const loading = ref(false)
const recordDialog = ref(false)
const selectedTask = ref<InspectionTask>()
const selectedTemplate = ref<InspectionTemplate>()
const templateLoading = ref(false)
const recordFormRef = ref<FormInstance>()
const saving = ref(false)
const recordForm = reactive({ verdict: 'PASS', defectiveQuantity: 0, defectSummary: '', defectLocation: '', measurement: '', lowerLimit: '', upperLimit: '' })
const recordValues = reactive<Record<string, string | number | undefined>>({})
const inspectionPhotoFiles = ref<UploadUserFile[]>([])
const uploadedAttachmentUrls = ref<string[]>([])
const offlineDrafts = ref<OfflineInspectionDraft[]>([])
const syncingOfflineDrafts = ref(false)
const route = useRoute()
const isSummaryRoute = computed(() => route.meta.finishedTestSummary === true)
const activePanel = ref<'monitor' | 'records' | 'summary'>(isSummaryRoute.value ? 'summary' : route.query.panel === 'records' ? 'records' : 'monitor')
const monitoringLoading = ref(false)
const equipment = ref<Equipment[]>([])
const runtimeStatuses = ref<EquipmentRuntimeStatus[]>([])
const hostStatuses = ref<GatewayHostStatus[]>([])
const hostStatusError = ref('')
const offlineAfterSeconds = ref(300)
const finishedTestRecords = ref<EquipmentTelemetryRecord[]>([])
const finishedRecordsNextCursor = ref<string>()
const finishedRecordsLoading = ref(false)
const finishedRecordsUpdatedAt = ref<Date>()
const probeSnapshots = ref<Record<string, EquipmentTelemetryRecord>>({})
const probeFailedEquipmentIds = ref<Set<string>>(new Set())
const summaryError = ref('')
const settlementError = ref('')
const equipmentError = ref('')
const runtimeError = ref('')
const probeSnapshotsError = ref('')
const finishedRecordsError = ref('')
const recordSource = ref<'probe' | 'zhongbo' | 'retryable' | 'all'>('probe')
const recordMaterialPrompt = computed(() => `搜索${recordSource.value === 'probe' || recordSource.value === 'retryable' ? '设备料号' : 'PCB料号'}`)
const zhongboUnmatchedOnly = ref(false)
const recordDateRange = ref<string[]>([])
const appliedRecordDateRange = ref<string[]>([])
const canBindFlyingProbe = computed(() => ['PLATFORM_ADMIN', 'TENANT_ADMIN', 'SUPERVISOR'].includes(menuAccess.value?.roleCode ?? ''))
const retryableRecords = ref<FlyingProbeRetryableProjection[]>([])
const retryableTotal = ref(0)
const retryablePage = ref(1)
const retryablePageSize = 50
const retryableLoading = ref(false)
const retryableLoaded = ref(false)
const retryableError = ref('')
const retryableUpdatedAt = ref<Date>()
const selectedRetryableRecords = ref<FlyingProbeRetryableProjection[]>([])
const selectedProbeRecords = ref<EquipmentTelemetryRecord[]>([])
const selectedZhongboRecords = ref<EquipmentTelemetryRecord[]>([])
const selectedAllRecords = ref<EquipmentTelemetryRecord[]>([])
const selectedDeviceRecords = computed(() => recordSource.value === 'probe' ? selectedProbeRecords.value
  : recordSource.value === 'zhongbo' ? selectedZhongboRecords.value : selectedAllRecords.value)
const recordWorkOrderNo = ref('')
const recordMaterialNo = ref('')
const recordFactoryInternalNo = ref('')
const appliedRecordSearch = ref<{ workOrderNo?: string; internalProductNo?: string; factoryInternalNo?: string }>({})
const bindingDialog = ref(false)
const bindingRecord = ref<FlyingProbeRetryableProjection & { eventType?: string }>()
const bindingBatchRecords = ref<Array<FlyingProbeRetryableProjection | EquipmentTelemetryRecord>>([])
const bindingBatchSource = ref<'retryable' | 'records'>('retryable')
const bindingKeyword = ref('')
const bindingWorkOrders = ref<WorkOrder[]>([])
const bindingWorkOrderNo = ref('')
const bindingSearchLoading = ref(false)
const bindingSaving = ref(false)
const bindingError = ref('')
let bindingSearchVersion = 0
const {
  records: flyingProbeRecords,
  nextCursor: flyingProbeNextCursor,
  loading: flyingProbeLoading,
  loaded: flyingProbeLoaded,
  error: flyingProbeError,
  updatedAt: flyingProbeUpdatedAt,
  refresh: refreshFlyingProbeRecords,
  loadMore: loadMoreFlyingProbeRecords
} = useTelemetryHistory((query) => qmsApi.listEquipmentTelemetryHistory(query), 'FLYING_PROBE_TEST_COUNTERS', '飞针机采集记录')
const {
  records: zhongboReports,
  nextCursor: zhongboNextCursor,
  loading: zhongboLoading,
  loaded: zhongboLoaded,
  error: zhongboError,
  updatedAt: zhongboUpdatedAt,
  refresh: refreshZhongboReports,
  loadMore: loadMoreZhongboReports
} = useTelemetryHistory((query) => qmsApi.listEquipmentTelemetryHistory(query), 'ZHONGBO_TEST_REPORT', '众博信测试报告')
const recordPanelUpdatedAt = computed(() => recordSource.value === 'probe' ? flyingProbeUpdatedAt.value
  : recordSource.value === 'zhongbo' ? zhongboUpdatedAt.value
  : recordSource.value === 'retryable' ? retryableUpdatedAt.value : finishedRecordsUpdatedAt.value)
const recordPanelLoading = computed(() => recordSource.value === 'probe' ? flyingProbeLoading.value
  : recordSource.value === 'zhongbo' ? zhongboLoading.value
  : recordSource.value === 'retryable' ? retryableLoading.value : finishedRecordsLoading.value)
watch([isSummaryRoute, () => route.query.panel], ([summaryRoute, panel]) => {
  activePanel.value = summaryRoute ? 'summary' : panel === 'records' ? 'records' : 'monitor'
  void refreshWorkbench()
})
const deviceSummary = ref<FinishedTestDeviceSummary[]>([])
const settlements = ref<FinishedTestSettlement[]>([])
const summaryDateRange = ref<string[]>([])
const summaryWorkOrder = ref('')
const summaryPcbMaterial = ref('')
const summaryDimension = ref<SummaryDimension>('workOrderNo')
const selectedDeviceId = ref('')
const taskListDeviceAssetNo = ref('')
const recordEquipmentAssetNo = ref('')
const taskKeyword = ref('')
const recordKeyword = ref('')
const updatedAt = ref<Date>()
const finishedTestEquipmentType = '成品测试设备'

type MonitorDevice = Equipment & {
  runtime?: EquipmentRuntimeStatus
  host?: GatewayHostStatus
  probe?: EquipmentTelemetryRecord
  probeError: boolean
}
const recordRules: FormRules = {
  verdict: [{ required: true, message: '请选择结论', trigger: 'change' }],
  defectiveQuantity: [{ required: true, type: 'number', min: 0, message: '请输入不良数量', trigger: 'change' }]
}

const fqcTasks = computed(() => tasks.value.filter((task) =>
  task.taskType?.toUpperCase() === 'FINISHED' || task.processCode?.toUpperCase() === 'FQC'))

const monitorDevices = computed<MonitorDevice[]>(() => {
  const runtimeByEquipmentId = new Map(runtimeStatuses.value.map((status) => [status.equipmentId, status]))
  const hostByEquipmentId = new Map([...hostStatuses.value]
    .sort((a, b) => Date.parse(a.receivedAt) - Date.parse(b.receivedAt))
    .map((status) => [status.equipmentId, status]))
  return equipment.value.filter((item) => item.equipmentType === finishedTestEquipmentType).map((device) => ({
    ...device,
    runtime: runtimeByEquipmentId.get(device.id),
    host: hostByEquipmentId.get(device.id),
    probe: probeSnapshots.value[device.id],
    probeError: probeFailedEquipmentIds.value.has(device.id)
  }))
})
const monitorPage = ref(1)
const monitorPageSize = ref(10)
const pagedMonitorDevices = computed(() => paginateRows(monitorDevices.value, monitorPage.value, monitorPageSize.value))
watch(() => monitorDevices.value.length, (count) => {
  monitorPage.value = clampPage(monitorPage.value, count, monitorPageSize.value)
})
watch(pagedMonitorDevices, (devices) => {
  const first = devices[0]
  if (first && !devices.some((device) => device.assetNo === selectedDeviceId.value)) {
    selectedDeviceId.value = first.assetNo
  }
})

const selectedDevice = computed(() => monitorDevices.value.find((device) => device.assetNo === selectedDeviceId.value) ?? monitorDevices.value[0])
const selectedProbeRecord = computed(() => selectedDevice.value?.probe)
const selectedAssociation = computed(() => {
  const probe = selectedProbeRecord.value
  return probe?.fqcProjectionStatus === 'PROCESSED'
    ? `${probe.workOrderNo || '未关联'} / ${probe.batchNo || '未关联'}`
    : '未关联 / 未关联'
})
const fqcTotal = computed(() => deviceSummary.value.reduce((sum, row) => sum + row.totalCount, 0))
const fqcPass = computed(() => deviceSummary.value.reduce((sum, row) => sum + row.goodCount, 0))
const fqcFail = computed(() => deviceSummary.value.reduce((sum, row) => sum + row.badCount, 0))
const fqcPassRate = computed(() => fqcTotal.value ? (fqcPass.value / fqcTotal.value * 100).toFixed(2) : '0.00')
const filteredDeviceSummary = computed(() => deviceSummary.value.filter((row) => {
  const [from, to] = summaryDateRange.value ?? []
  const workOrder = summaryWorkOrder.value.trim().toLowerCase()
  const material = summaryPcbMaterial.value.trim().toLowerCase()
  return (!from || row.inspectionDate >= from) && (!to || row.inspectionDate <= to)
    && (!workOrder || row.workOrderNo.toLowerCase().includes(workOrder))
    && (!material || (row.pcbMaterialNo ?? '').toLowerCase().includes(material))
}))
const groupedDeviceSummary = computed(() => groupFinishedTestSummary(filteredDeviceSummary.value, summaryDimension.value))
const filteredSettlements = computed(() => settlements.value.filter((row) => {
  const [from, to] = summaryDateRange.value ?? []
  const date = row.lastDetectionAt.slice(0, 10)
  const workOrder = summaryWorkOrder.value.trim().toLowerCase()
  const material = summaryPcbMaterial.value.trim().toLowerCase()
  return (!from || date >= from) && (!to || date <= to)
    && (!workOrder || row.workOrderNo.toLowerCase().includes(workOrder))
    && (!material || row.materialKey.toLowerCase().includes(material))
}))
const summaryDimensionLabel = computed(() => ({ workOrderNo: '生产指令', internalProductNo: '厂内编号', dateEquipmentOrder: '检测日期＋设备编号＋生产指令' })[summaryDimension.value])
const filteredSummaryTotals = computed(() => filteredDeviceSummary.value.reduce((totals, row) => ({
  tested: totals.tested + row.totalCount,
  good: totals.good + row.goodCount,
  bad: totals.bad + row.badCount
}), { tested: 0, good: 0, bad: 0 }))
const summaryFilterKey = computed(() => JSON.stringify([summaryDimension.value, summaryDateRange.value, summaryWorkOrder.value, summaryPcbMaterial.value]))

function resetSummaryFilters() {
  summaryDateRange.value = []
  summaryWorkOrder.value = ''
  summaryPcbMaterial.value = ''
}
const internalNo = (record: EquipmentTelemetryRecord) => record.internalProductNo
  || deviceSummary.value.find((row) => row.workOrderNo === record.workOrderNo)?.internalProductNo || '—'
const filteredTasks = computed(() => {
  const keyword = taskKeyword.value.trim().toLowerCase()
  if (!keyword) return fqcTasks.value
  return fqcTasks.value.filter((task) => [task.taskNo, task.workOrderNo, task.processName, task.assigneeName]
    .some((value) => String(value ?? '').toLowerCase().includes(keyword)))
})
const filteredFinishedTestRecords = computed(() => {
  const keyword = recordKeyword.value.trim().toLowerCase()
  if (!keyword) return finishedTestRecords.value
  return finishedTestRecords.value.filter((record) => [
    record.equipmentCode, record.workOrderNo, record.materialNo, record.internalProductNo,
    record.sourceRecordNo, record.machineName
  ].some((value) => String(value ?? '').toLowerCase().includes(keyword)))
})
const updatedAtLabel = computed(() => updatedAt.value ? formatDateTime(updatedAt.value) : '尚未成功读取')
const workstationConnectionLabel = computed(() => {
  if (!networkOnline.value) return '本机网络已断开'
  return backendReachable.value ? 'QMS 服务已连接' : 'QMS 服务不可达'
})
const workstationConnectionClass = computed(() => ({
  'workstation-connection--offline': !networkOnline.value,
  'workstation-connection--degraded': networkOnline.value && !backendReachable.value
}))

function currentTenantCode() {
  return localStorage.getItem('qms.tenantCode') ?? 'demo-pcb'
}

function currentActor() {
  return localStorage.getItem('qms.actor') ?? 'demo.inspector'
}

async function loadOfflineDrafts() {
  offlineDrafts.value = await listInspectionDrafts(currentTenantCode(), currentActor())
}

function dynamicResults() {
  return selectedTemplate.value
    ? Object.fromEntries(selectedTemplate.value.items.map((item) => [item.itemCode, {
        itemName: item.itemName,
        value: recordValues[item.itemCode] ?? '',
        lowerLimit: item.lowerLimit,
        upperLimit: item.upperLimit,
        unit: item.unit
      }]))
    : {
        measurement: recordForm.measurement,
        lowerLimit: recordForm.lowerLimit,
        upperLimit: recordForm.upperLimit
      }
}

function isNetworkFailure(error: unknown) {
  return !navigator.onLine || (isAxiosError(error) && !error.response)
}

async function promptManualNcr(verdict: string, inspectionRecordId: string, ncrNo?: string) {
  if (verdict !== 'FAIL' || ncrNo) return ncrNo
  try {
    await ElMessageBox.confirm('当前未开启自动建单。是否立即为本条不合格记录发起不良品处置单？', '发起不良品处置', {
      confirmButtonText: '立即发起', cancelButtonText: '稍后处理', type: 'warning'
    })
    return (await qmsApi.createNonconformity(inspectionRecordId)).ncrNo
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.warning('检验已提交，但不良品处置单创建失败；请稍后在不良品处置模块重试。')
    }
    return undefined
  }
}

async function queueInspectionDraft(payload: OfflineInspectionPayload) {
  if (!selectedTask.value) return
  const photos: OfflineInspectionDraft['photos'] = []
  for (const uploadFile of inspectionPhotoFiles.value) {
    if (uploadFile.raw) {
      photos.push({ name: uploadFile.raw.name, type: uploadFile.raw.type, blob: uploadFile.raw })
    }
  }
  await saveInspectionDraft({
    id: payload.clientRequestId,
    tenantCode: currentTenantCode(),
    actor: currentActor(),
    taskId: selectedTask.value.id,
    taskNo: selectedTask.value.taskNo,
    processName: selectedTask.value.processName,
    payload,
    photos,
    createdAt: new Date().toISOString()
  })
  await loadOfflineDrafts()
}

async function syncOfflineDraft(draft: OfflineInspectionDraft) {
  const attachmentUrls = await Promise.all(draft.photos.map(async (photo) => {
    const file = new File([photo.blob], photo.name, { type: photo.type })
    return (await qmsApi.uploadInspectionAttachment(file)).url
  }))
  const result = await qmsApi.submitInspectionRecord(draft.taskId, { ...draft.payload, attachmentUrls })
  await removeInspectionDraft(draft.id)
  const ncrNo = await promptManualNcr(draft.payload.verdict, result.id, result.ncrNo)
  ElMessage.success(ncrNo ? `${draft.taskNo} 已补传，已创建 ${ncrNo}` : `${draft.taskNo} 已补传`)
}

async function syncOfflineDrafts() {
  if (syncingOfflineDrafts.value) return
  if (!navigator.onLine) {
    ElMessage.warning('当前无网络，暂存记录会在恢复网络后自动补传。')
    return
  }
  syncingOfflineDrafts.value = true
  let uploadedCount = 0
  try {
    for (const draft of [...offlineDrafts.value]) {
      try {
        await syncOfflineDraft(draft)
        uploadedCount++
      } catch {
        ElMessage.warning(`${draft.taskNo} 补传失败，已保留在本机暂存中。`)
        break
      }
    }
    await loadOfflineDrafts()
    if (uploadedCount) await refreshWorkbench()
  } finally {
    syncingOfflineDrafts.value = false
  }
}

function handleOnline() {
  networkOnline.value = true
  if (offlineDrafts.value.length) {
    ElMessage.info(`网络已恢复，正在补传 ${offlineDrafts.value.length} 条现场检验记录。`)
    void syncOfflineDrafts()
  }
  void refreshWorkbench()
}

function handleOffline() {
  networkOnline.value = false
  backendReachable.value = false
}

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await qmsApi.listInspectionTasks('PENDING')
    backendReachable.value = true
  } catch {
    backendReachable.value = false
    ElMessage.error('待检任务读取失败')
  } finally {
    loading.value = false
  }
}

async function loadProbeSnapshots(devices: Equipment[]) {
  const snapshots: Record<string, EquipmentTelemetryRecord> = {}
  const failedIds = new Set<string>()
  for (let index = 0; index < devices.length; index += 4) {
    const batch = devices.slice(index, index + 4)
    const results = await Promise.allSettled(batch.map((device) => qmsApi.listEquipmentTelemetryHistory({
      equipmentId: device.id, eventType: 'FLYING_PROBE_TEST_COUNTERS', limit: 1
    })))
    results.forEach((result, offset) => {
      const equipmentId = batch[offset].id
      if (result.status === 'rejected') failedIds.add(equipmentId)
      else if (result.value.items[0]) snapshots[equipmentId] = result.value.items[0]
    })
  }
  probeSnapshots.value = snapshots
  probeFailedEquipmentIds.value = failedIds
  probeSnapshotsError.value = failedIds.size ? `${failedIds.size} 台设备的飞针计数快照读取失败，请刷新重试。` : ''
  return failedIds.size === 0
}

function recordTimeFilter() {
  const [from, to] = appliedRecordDateRange.value ?? []
  return from && to ? { fromTime: `${from}T00:00:00`, toTime: `${to}T23:59:59` } : {}
}

function refreshFilteredProbeRecords() {
  return refreshFlyingProbeRecords({ ...appliedRecordSearch.value, ...recordTimeFilter() })
}

function refreshFilteredZhongboReports() {
  return refreshZhongboReports({ ...appliedRecordSearch.value, ...recordTimeFilter(),
    ...(zhongboUnmatchedOnly.value ? { unmatchedOnly: true } : {}) })
}

async function loadDeviceRecords(cursor?: string) {
  if (finishedRecordsLoading.value) return false
  finishedRecordsLoading.value = true
  finishedRecordsError.value = ''
  try {
    const page = await qmsApi.listEquipmentTelemetryHistory({ eventType: 'FINISHED_TEST', limit: 100,
      ...appliedRecordSearch.value, ...recordTimeFilter(), ...(cursor ? { cursor } : {}) })
    finishedTestRecords.value = cursor ? [...finishedTestRecords.value, ...page.items] : page.items
    finishedRecordsNextCursor.value = page.nextCursor ?? undefined
    if (!cursor) finishedRecordsUpdatedAt.value = new Date()
    return true
  } catch {
    finishedRecordsError.value = cursor
      ? '更早的设备检测记录加载失败，请重试。'
      : '设备检测记录读取失败，请重试。'
    if (!cursor) {
      finishedTestRecords.value = []
      finishedRecordsNextCursor.value = undefined
      finishedRecordsUpdatedAt.value = undefined
    }
    return false
  } finally {
    finishedRecordsLoading.value = false
  }
}

async function loadRetryableProjections(page = retryablePage.value) {
  if (retryableLoading.value) return
  retryableLoading.value = true
  retryableError.value = ''
  try {
    const result = await qmsApi.listRetryableFlyingProbeProjections(page, retryablePageSize,
      { ...appliedRecordSearch.value, ...recordTimeFilter() })
    retryableRecords.value = result.records
    selectedRetryableRecords.value = []
    retryableTotal.value = result.total
    retryablePage.value = result.page
    retryableLoaded.value = true
    retryableUpdatedAt.value = new Date()
  } catch (error) {
    retryableError.value = error instanceof Error ? error.message : '待匹配记录读取失败，请重试。'
  } finally {
    retryableLoading.value = false
  }
}

let monitoringVersion = 0
let runtimePollInFlight = false
let runtimePollTimer: number | undefined
let componentMounted = false

async function pollRuntimeStatuses() {
  if (activePanel.value !== 'monitor' || monitoringLoading.value || runtimePollInFlight) return
  runtimePollInFlight = true
  const version = monitoringVersion
  try {
    const [runtimeResult, hostResult] = await Promise.allSettled([
      qmsApi.listEquipmentRuntimeStatuses(), qmsApi.listGatewayHostStatuses()
    ])
    if (version !== monitoringVersion || !componentMounted) return
    runtimeStatuses.value = runtimeResult.status === 'fulfilled' ? runtimeResult.value : []
    runtimeError.value = runtimeResult.status === 'rejected' ? '设备实时状态读取失败，请重试。' : ''
    hostStatuses.value = hostResult.status === 'fulfilled' ? hostResult.value : []
    hostStatusError.value = hostResult.status === 'rejected' ? '电脑状态读取失败，请重试。' : ''
  } finally {
    runtimePollInFlight = false
  }
}

async function loadMonitoringData() {
  monitoringLoading.value = true
  monitoringVersion++
  try {
    const [summaryResult, equipmentResult, runtimeResult, hostResult, thresholdResult] = await Promise.allSettled([
      qmsApi.listFinishedTestDeviceSummary(),
      qmsApi.listEquipment(),
      qmsApi.listEquipmentRuntimeStatuses(),
      qmsApi.listGatewayHostStatuses(),
      qmsApi.getTenantParameter('equipment.monitor.offline-after-seconds')
    ])
    summaryError.value = summaryResult.status === 'rejected' ? '设备检测汇总读取失败，请重试。' : ''
    equipmentError.value = equipmentResult.status === 'rejected' ? '成品测试设备台账读取失败，请重试。' : ''
    runtimeError.value = runtimeResult.status === 'rejected' ? '设备实时状态读取失败，请重试。' : ''
    hostStatuses.value = hostResult.status === 'fulfilled' ? hostResult.value : []
    hostStatusError.value = hostResult.status === 'rejected' ? '电脑状态读取失败，请重试。' : ''
    if (thresholdResult.status === 'fulfilled') {
      const seconds = Number.parseInt(thresholdResult.value.value, 10)
      offlineAfterSeconds.value = Number.isFinite(seconds) ? Math.min(86_400, Math.max(30, seconds)) : 300
    }
    if (summaryResult.status === 'fulfilled') deviceSummary.value = summaryResult.value
    if (equipmentResult.status === 'fulfilled') equipment.value = equipmentResult.value
    else equipment.value = []
    if (runtimeResult.status === 'fulfilled') runtimeStatuses.value = runtimeResult.value
    else runtimeStatuses.value = []
    const probesLoaded = equipmentResult.status === 'fulfilled'
      ? await loadProbeSnapshots(equipmentResult.value.filter((item) => item.equipmentType === finishedTestEquipmentType))
      : false
    if (equipmentResult.status === 'rejected') {
      probeSnapshots.value = {}
      probeFailedEquipmentIds.value = new Set()
      probeSnapshotsError.value = ''
    }
    if (probesLoaded && [summaryResult, equipmentResult, runtimeResult, hostResult].every((result) => result.status === 'fulfilled')) {
      updatedAt.value = new Date()
    }
  } finally {
    monitoringLoading.value = false
  }
}

async function refreshWorkbench() {
  if (activePanel.value === 'records') { await Promise.all([refreshRecordPanel(), loadOfflineDrafts()]); return }
  if (activePanel.value === 'summary') {
    const [summary, snapshot] = await Promise.allSettled([
      qmsApi.listFinishedTestDeviceSummary(), qmsApi.listFinishedTestSettlements()
    ])
    if (summary.status === 'fulfilled') {
      deviceSummary.value = summary.value
      summaryError.value = ''
      updatedAt.value = new Date()
    } else summaryError.value = '设备检测汇总读取失败，请重试。'
    if (snapshot.status === 'fulfilled') {
      settlements.value = snapshot.value
      settlementError.value = ''
    } else settlementError.value = '结算快照读取失败，请重试。'
    return
  }
  if (monitoringLoading.value) return
  await Promise.all([loadMonitoringData(), loadOfflineDrafts()])
  if (!monitorDevices.value.some((device) => device.assetNo === selectedDeviceId.value)) {
    selectedDeviceId.value = monitorDevices.value[0]?.assetNo ?? ''
  }
}

function showPanel(panel: 'monitor' | 'records') {
  activePanel.value = panel
  taskListDeviceAssetNo.value = ''
  if (panel === 'records') recordSource.value = 'probe'
  void refreshWorkbench()
}

function showRecordSource(source: 'probe' | 'zhongbo' | 'retryable' | 'all') {
  recordSource.value = source
  selectedProbeRecords.value = []
  selectedZhongboRecords.value = []
  selectedAllRecords.value = []
  if (source === 'zhongbo') zhongboUnmatchedOnly.value = false
  if (source === 'probe') void refreshFilteredProbeRecords()
  if (source === 'zhongbo') void refreshFilteredZhongboReports()
  if (source === 'retryable') void loadRetryableProjections(1)
  if (source === 'all') void loadDeviceRecords()
}

function showUnmatchedZhongbo() {
  zhongboUnmatchedOnly.value = true
  void refreshFilteredZhongboReports()
}

function canSelectRetryableRecord(record: FlyingProbeRetryableProjection) {
  return !record.workOrderNo
}

function updateRetryableSelection(records: FlyingProbeRetryableProjection[]) {
  selectedRetryableRecords.value = records
}

function openBatchBindingDialog() {
  const records = selectedRetryableRecords.value
  if (!records.length) return
  const material = records[0].materialNo?.trim().toUpperCase()
  if (!material || records.some((record) => record.materialNo?.trim().toUpperCase() !== material)) {
    ElMessage.warning('请选择同一设备料号的待匹配文件。')
    return
  }
  bindingBatchSource.value = 'retryable'
  openBindingDialog(records[0], records)
}

function openSelectedRecordBindingDialog() {
  const records = selectedDeviceRecords.value
  if (!records.length) return
  const material = records[0].materialNo?.trim().toUpperCase()
  if (!material || records.some((record) => record.materialNo?.trim().toUpperCase() !== material)) {
    ElMessage.warning('批量绑定请选择同一设备料号的记录。')
    return
  }
  bindingBatchSource.value = 'records'
  openBindingDialog(records[0], records)
}

function describeBatchReasons(reasons: string[], records: EquipmentTelemetryRecord[]) {
  const labels = new Map(records.map((record) => [record.id,
    `${formatDateTime(record.deviceTime)} · ${record.equipmentCode || record.machineName || '设备'} · ${record.materialNo || '无料号'}`]))
  return reasons.map((reason) => {
    const separator = reason.indexOf('：')
    const id = reason.slice(0, separator)
    return separator >= 0 && labels.has(id) ? `${labels.get(id)}：${reason.slice(separator + 1)}` : reason
  }).join('； ')
}

async function cancelSelectedRecordBindings() {
  const records = selectedDeviceRecords.value.filter((record) => record.workOrderNo)
  if (!records.length) return
  try {
    await ElMessageBox.confirm(`取消当前勾选的 ${records.length} 条记录的生产指令关联？已有检验任务、记录或处置单的文件会跳过。`,
      '确认批量取消关联', { type: 'warning' })
  } catch { return }
  try {
    const result = await qmsApi.batchManageTelemetryWorkOrder(records.map((record) => record.id), 'CANCEL')
    ElMessage.success(`已取消 ${result.changed} 条；跳过 ${result.skipped} 条，失败 ${result.failed} 条`)
    if (result.reasons.length) await ElMessageBox.alert(describeBatchReasons(result.reasons, records), '跳过记录原因')
    selectedProbeRecords.value = []
    selectedZhongboRecords.value = []
    selectedAllRecords.value = []
    await Promise.all([refreshRecordPanel(), loadMonitoringData()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量取消关联失败')
  }
}

function searchRecordFilters() {
  if (recordPanelLoading.value) return
  const workOrderNo = recordWorkOrderNo.value.trim()
  const internalProductNo = recordMaterialNo.value.trim()
  const factoryInternalNo = recordFactoryInternalNo.value.trim()
  appliedRecordSearch.value = {
    ...(workOrderNo ? { workOrderNo } : {}),
    ...(internalProductNo ? { internalProductNo } : {}),
    ...(factoryInternalNo ? { factoryInternalNo } : {})
  }
  appliedRecordDateRange.value = [...(recordDateRange.value ?? [])]
  selectedProbeRecords.value = []
  selectedZhongboRecords.value = []
  selectedAllRecords.value = []
  if (recordSource.value === 'retryable') void loadRetryableProjections(1)
  else void refreshRecordPanel()
}

async function refreshRecordPanel() {
  if (recordSource.value === 'probe') await refreshFilteredProbeRecords()
  else if (recordSource.value === 'zhongbo') await refreshFilteredZhongboReports()
  else if (recordSource.value === 'retryable') await loadRetryableProjections()
  else await loadDeviceRecords()
}

function resetRecordDateFilter() {
  recordDateRange.value = []
  appliedRecordDateRange.value = []
  void refreshRecordPanel()
}

function deviceOnline(device: MonitorDevice) {
  return inspectionDeviceOnline(device.runtime?.online, device.host?.receivedAt, Date.now(), offlineAfterSeconds.value)
}

function deviceStatusLabel(device: MonitorDevice) {
  if (runtimeError.value && !device.host) return '状态不可用'
  return monitorStatusLabel(deriveInspectionMonitorStatus(deviceOnline(device), device.probe?.receivedAt), device.status)
}

function deviceStatusTag(device: MonitorDevice): 'success' | 'warning' | 'info' | 'danger' {
  if (runtimeError.value && !device.host) return 'info'
  const status = deriveInspectionMonitorStatus(deviceOnline(device), device.probe?.receivedAt)
  if (status === '未接入') return equipmentStatusTagType(device.status)
  if (status === '测试中') return 'success'
  return status === '空闲' ? 'warning' : 'info'
}

const zhongboStatusLabels: Record<string, string> = { PROCESSING: '运行中', IDLE: '待机', ALARM: '故障报警' }

function zhongboStatusText(status?: string) {
  return zhongboStatusLabels[status ?? ''] ?? statusText(status)
}

function fqcProjectionLabel(record: EquipmentTelemetryRecord) {
  if (record.fqcProjectionStatus === 'PROCESSED') {
    if (record.fqcInspectionRecordId) return '已生成检验记录'
    if (record.fqcProjectionReason?.includes('基线')) return '已作基线'
    return record.fqcDeltaTested === 0 ? '无新增板' : '已处理'
  }
  if (record.fqcProjectionStatus === 'RETRYABLE') return '待匹配'
  if (record.fqcProjectionStatus === 'UNBOUND') return '已取消关联'
  if (record.fqcProjectionStatus === 'SKIPPED') return '已跳过'
  if (record.fqcProjectionStatus === 'PENDING') return '待处理'
  return record.fqcProjectionStatus || '待处理'
}

function fqcProjectionTag(record: EquipmentTelemetryRecord): 'success' | 'warning' | 'info' {
  if (record.fqcInspectionRecordId) return 'success'
  if (record.fqcProjectionStatus === 'RETRYABLE') return 'warning'
  return 'info'
}

async function searchBindingWorkOrders() {
  const keyword = bindingKeyword.value.trim()
  const version = ++bindingSearchVersion
  bindingWorkOrderNo.value = ''
  bindingWorkOrders.value = []
  bindingError.value = ''
  bindingSearchLoading.value = false
  if (!keyword) {
    bindingError.value = '请输入生产指令号或厂内编号。'
    return
  }
  bindingSearchLoading.value = true
  try {
    const page = await qmsApi.listWorkOrders({ keyword, page: 1, pageSize: 50 })
    if (version === bindingSearchVersion) {
      bindingWorkOrders.value = page.records
      if (bindingRecord.value?.workOrderNo && page.records.some((order) => order.workOrderNo === bindingRecord.value?.workOrderNo)) {
        bindingWorkOrderNo.value = bindingRecord.value.workOrderNo
      }
    }
  } catch (error) {
    if (version === bindingSearchVersion) bindingError.value = error instanceof Error ? error.message : '生产指令读取失败，请重试。'
  } finally {
    if (version === bindingSearchVersion) bindingSearchLoading.value = false
  }
}

function openBindingDialog(record: FlyingProbeRetryableProjection | EquipmentTelemetryRecord,
                           batchRecords: Array<FlyingProbeRetryableProjection | EquipmentTelemetryRecord> = []) {
  bindingSearchVersion++
  bindingBatchRecords.value = [...batchRecords]
  bindingRecord.value = {
    id: record.id, equipmentCode: record.equipmentCode, machineName: record.machineName,
    materialNo: record.materialNo, deviceTime: record.deviceTime, workOrderNo: record.workOrderNo,
    sourceFile: 'sourceFile' in record ? record.sourceFile : undefined,
    eventType: 'eventType' in record ? record.eventType : undefined
  }
  bindingKeyword.value = record.workOrderNo || record.materialNo || ''
  bindingWorkOrders.value = []
  bindingWorkOrderNo.value = ''
  bindingError.value = ''
  bindingSearchLoading.value = false
  bindingDialog.value = true
}

async function submitBinding() {
  const record = bindingRecord.value
  const selected = bindingWorkOrders.value.find((order) => order.workOrderNo === bindingWorkOrderNo.value)
  if (!record || !selected || bindingSaving.value) return
  const batchRecords = bindingBatchRecords.value
  try {
    await ElMessageBox.confirm(
      `${batchRecords.length ? `将勾选的 ${batchRecords.length} 条设备记录` : '将这条设备记录'}关联到生产指令 ${selected.workOrderNo}（厂内编号 ${selected.internalProductNo || '—'}，下达日期 ${formatDate(selected.releasedAt)}）。状态已变化或已有下游记录的文件会跳过。确定继续吗？`,
      '确认手动绑定',
      { confirmButtonText: '确认绑定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  bindingSaving.value = true
  try {
    if (batchRecords.length) {
      const result = bindingBatchSource.value === 'retryable'
        ? await qmsApi.batchBindFlyingProbeWorkOrder(batchRecords.map((item) => item.id), selected.workOrderNo)
        : await qmsApi.batchManageTelemetryWorkOrder(batchRecords.map((item) => item.id), 'BIND', selected.workOrderNo)
      const message = `已绑定 ${'bound' in result ? result.bound : result.changed} 条，跳过 ${result.skipped} 条，失败 ${result.failed} 条`
      if (result.skipped || result.failed) ElMessage.warning(message)
      else ElMessage.success(message)
      if ('reasons' in result && result.reasons.length) await ElMessageBox.alert(
        describeBatchReasons(result.reasons, batchRecords as EquipmentTelemetryRecord[]), '跳过记录原因')
    } else if (record.eventType === 'ZHONGBO_TEST_REPORT') {
      await qmsApi.bindZhongboWorkOrder(record.id, selected.workOrderNo)
    } else {
      await qmsApi.bindFlyingProbeWorkOrder(record.id, selected.workOrderNo)
    }
    bindingDialog.value = false
    if (!batchRecords.length) ElMessage.success('生产指令已绑定')
    await Promise.all([
      refreshFilteredProbeRecords(),
      refreshFilteredZhongboReports(),
      loadDeviceRecords(),
      loadMonitoringData(),
      ...(retryableLoaded.value ? [loadRetryableProjections()] : [])
    ])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '手动绑定失败，请重试。')
  } finally {
    bindingSaving.value = false
  }
}

function selectDevice(assetNo: string) {
  selectedDeviceId.value = assetNo
}

function openNextTask() {
  const nextTask = filteredTasks.value[0]
  if (!nextTask) {
    ElMessage.info('当前没有待处理的成品测试任务。')
    return
  }
  void openRecord(nextTask)
}

function handleKeyboardShortcut(event: KeyboardEvent) {
  if (event.ctrlKey && event.key === 'Enter' && recordDialog.value && !saving.value) {
    event.preventDefault()
    void submitRecord()
  }
}

async function openRecord(task: InspectionTask) {
  selectedTask.value = task
  recordEquipmentAssetNo.value = taskListDeviceAssetNo.value
  selectedTemplate.value = undefined
  Object.assign(recordForm, { verdict: 'PASS', defectiveQuantity: 0, defectSummary: '', defectLocation: '', measurement: '', lowerLimit: '', upperLimit: '' })
  Object.keys(recordValues).forEach((key) => delete recordValues[key])
  inspectionPhotoFiles.value = []
  uploadedAttachmentUrls.value = []
  recordDialog.value = true
  if (!task.templateId) return
  templateLoading.value = true
  try {
    selectedTemplate.value = await qmsApi.getInspectionTemplate(task.templateId)
    selectedTemplate.value.items.forEach((item) => { recordValues[item.itemCode] = '' })
  } catch {
    ElMessage.warning('检验模板读取失败，将使用通用检验记录。')
  } finally {
    templateLoading.value = false
  }
}

function displayRange(lowerLimit?: number, upperLimit?: number, unit?: string) {
  if (lowerLimit === undefined && upperLimit === undefined) return ''
  return `${lowerLimit ?? '—'} ~ ${upperLimit ?? '—'}${unit ? ` ${unit}` : ''}`
}

function exceededNumberItems() {
  return selectedTemplate.value?.items.filter((item) => {
    if (item.inputType !== 'NUMBER') return false
    const rawValue = recordValues[item.itemCode]
    if (rawValue === undefined || rawValue === '') return false
    const value = Number(rawValue)
    return Number.isFinite(value) && ((item.lowerLimit !== undefined && value < item.lowerLimit)
      || (item.upperLimit !== undefined && value > item.upperLimit))
  }) ?? []
}

async function submitRecord() {
  const valid = await recordFormRef.value?.validate().catch(() => false)
  if (!valid || !selectedTask.value) return
  const missingItem = selectedTemplate.value?.items.find((item) => item.required && !String(recordValues[item.itemCode] ?? '').trim())
  if (missingItem) {
    ElMessage.warning(`请填写必填检验项：${missingItem.itemName}`)
    return
  }
  const exceededItems = exceededNumberItems()
  if (recordForm.verdict === 'PASS' && exceededItems.length) {
    recordForm.verdict = 'FAIL'
    ElMessage.warning(`“${exceededItems.map((item) => item.itemName).join('、')}”超出控制范围，已自动切换为不合格`)
  }
  const payload: OfflineInspectionPayload = {
    verdict: recordForm.verdict,
    defectiveQuantity: recordForm.defectiveQuantity,
    defectSummary: recordForm.defectSummary,
    defectLocation: recordForm.defectLocation,
    results: dynamicResults(),
    equipmentAssetNo: recordEquipmentAssetNo.value || undefined,
    clientRequestId: createClientRequestId()
  }
  saving.value = true
  try {
    if (!uploadedAttachmentUrls.value.length && inspectionPhotoFiles.value.length) {
      const files = inspectionPhotoFiles.value.map((file) => file.raw).filter(Boolean) as File[]
      uploadedAttachmentUrls.value = (await Promise.all(files.map((file) => qmsApi.uploadInspectionAttachment(file)))).map((attachment) => attachment.url)
    }
    const result = await qmsApi.submitInspectionRecord(selectedTask.value.id, {
      ...payload,
      attachmentUrls: uploadedAttachmentUrls.value
    })
    recordDialog.value = false
    const ncrNo = await promptManualNcr(payload.verdict, result.id, result.ncrNo)
    ElMessage.success(ncrNo ? `检验已提交，已创建 ${ncrNo}` : '检验记录已提交')
    await refreshWorkbench()
  } catch (error) {
    if (!isNetworkFailure(error)) {
      ElMessage.error('检验记录提交失败')
      return
    }
    try {
      await queueInspectionDraft(payload)
      recordDialog.value = false
      ElMessage.warning('网络不可用，检验记录和照片已安全暂存到本机；恢复网络后将自动补传。')
    } catch {
      ElMessage.error('网络不可用且本机暂存失败，请勿关闭当前页面后重试。')
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  componentMounted = true
  void loadMenuAccess()
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('keydown', handleKeyboardShortcut)
  runtimePollTimer = window.setInterval(() => { void pollRuntimeStatuses() }, 30_000)
  await refreshWorkbench()
  if (!componentMounted) return
  if (navigator.onLine && offlineDrafts.value.length) void syncOfflineDrafts()
})

onBeforeUnmount(() => {
  componentMounted = false
  window.clearInterval(runtimePollTimer)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('keydown', handleKeyboardShortcut)
})
</script>

<template>
  <section class="page-stack finished-test-page" v-loading="monitoringLoading">
    <div v-if="!isSummaryRoute && offlineDrafts.length" class="offline-draft-banner">
      <el-alert type="warning" show-icon :closable="false" :title="`本机暂存 ${offlineDrafts.length} 条待补传检验记录`" description="暂存内容包含检验表单与照片，并按当前客户和用户归类；网络恢复后会自动补传。" />
      <el-button type="warning" :loading="syncingOfflineDrafts" @click="syncOfflineDrafts">立即补传</el-button>
    </div>

    <section v-if="!isSummaryRoute" class="finished-test-hero">
      <div>
        <p class="eyebrow">成品检测</p>
        <h2>成品测试实时监控工作台</h2>
        <p>集中查看成品测试工位和质量结果；检测记录可在记录列表查看。</p>
      </div>
      <div class="finished-test-hero-actions">
        <span class="workstation-connection" :class="workstationConnectionClass"><i></i>{{ workstationConnectionLabel }}</span>
        <span class="live-status"><i></i>数据更新 {{ updatedAtLabel }}</span>
        <el-button plain @click="refreshWorkbench">刷新数据</el-button>
      </div>
    </section>

    <nav v-if="!isSummaryRoute" class="finished-test-tabs" aria-label="成品测试页面模块">
      <button :class="{ active: activePanel === 'monitor' }" type="button" @click="showPanel('monitor')">实时监控</button>
      <button :class="{ active: activePanel === 'records' }" type="button" @click="showPanel('records')">设备检测记录</button>
    </nav>

    <template v-if="activePanel === 'monitor'">
      <section class="monitor-panel">
        <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" style="margin-bottom: 12px" />
        <el-alert v-if="equipmentError" type="error" :closable="false" :title="equipmentError" style="margin-bottom: 12px" />
        <el-alert v-if="runtimeError" type="error" :closable="false" :title="runtimeError" style="margin-bottom: 12px" />
        <el-alert v-if="hostStatusError" type="error" :closable="false" :title="hostStatusError" style="margin-bottom: 12px" />
        <el-alert v-if="probeSnapshotsError" type="error" :closable="false" :title="probeSnapshotsError" style="margin-bottom: 12px" />
        <div class="section-heading">
          <div>
            <h3>成品测试多设备实时监控</h3>
            <p>仅展示设备台账中类型为“{{ finishedTestEquipmentType }}”的设备；检测数量按已关联生产指令的设备记录统计。</p>
          </div>
          <div v-if="!summaryError" class="shift-summary">
            <span>检测总数 <strong>{{ fqcTotal }}</strong></span>
            <span>合格 <strong class="good-text">{{ fqcPass }}</strong></span>
            <span>不合格 <strong class="bad-text">{{ fqcFail }}</strong></span>
            <span>一次合格率 <strong class="good-text">{{ fqcPassRate }}%</strong></span>
          </div>
        </div>

        <div v-if="!equipmentError && monitorDevices.length" class="monitor-device-grid">
          <button
            v-for="device in pagedMonitorDevices"
            :key="device.assetNo"
            class="monitor-device-card"
            :class="{ 'monitor-device-card--selected': selectedDevice?.assetNo === device.assetNo, 'monitor-device-card--offline': !deviceOnline(device) }"
            type="button"
            @click="selectDevice(device.assetNo)"
          >
            <span class="device-card-head">
              <strong>{{ device.name }}</strong>
              <el-tag size="small" :type="deviceStatusTag(device)">{{ deviceStatusLabel(device) }}</el-tag>
            </span>
            <span class="device-card-type">{{ device.assetNo }} · {{ device.equipmentType }}</span>
            <span class="device-task">
              <small>采集状态</small>
              <b>{{ runtimeError && !device.host ? '读取失败' : deviceOnline(device) ? '在线' : deviceOnline(device) === false ? '离线' : '尚未上报' }}</b>
            </span>
            <span v-if="device.host" class="device-task">
              <small>电脑状态 · {{ device.host.hostName }}</small>
              <em>CPU：{{ device.host.cpuPercent == null ? '采样中' : `${device.host.cpuPercent}%` }}</em>
              <em>内存可用 / 总量：{{ formatHostCapacity(device.host.memoryAvailableBytes, device.host.memoryTotalBytes) }}</em>
              <em>系统盘剩余 / 总量：{{ formatHostCapacity(device.host.systemDiskFreeBytes, device.host.systemDiskTotalBytes) }}</em>
              <em>开机时长：{{ formatHostUptime(device.host.uptimeSeconds) }}</em>
            </span>
            <span class="device-card-footer"><span>飞针已测 {{ device.probeError ? '—' : (device.probe?.machiningCount ?? '—') }}</span><span>检测明细见记录列表</span></span>
          </button>
        </div>
        <div v-if="!equipmentError && monitorDevices.length" class="monitor-pagination">
          <el-pagination v-model:current-page="monitorPage" v-model:page-size="monitorPageSize" :total="monitorDevices.length" :page-sizes="[10, 20, 50, 100]" background layout="total, sizes, prev, pager, next, jumper" @size-change="monitorPage = 1" />
        </div>
        <el-empty v-if="!equipmentError && !monitorDevices.length" description="暂无成品测试设备，请先在设备台账新增并选择“成品测试设备”类型。" />

        <div v-if="selectedDevice && !equipmentError" class="selected-device-bar">
          <div><span>当前设备</span><strong>{{ selectedDevice.name }}（{{ selectedDevice.assetNo }}）</strong></div>
          <div v-if="selectedDevice.probeError"><span>飞针计数快照</span><strong>读取失败，请刷新重试</strong></div>
          <template v-else>
            <div><span>最新飞针料号</span><strong>{{ selectedProbeRecord?.materialNo || (selectedProbeRecord ? '未上报料号' : '暂无飞针采集') }}</strong></div>
            <div><span>飞针已测 / 好板 / 坏板</span><strong>{{ selectedProbeRecord?.machiningCount ?? '—' }} / {{ selectedProbeRecord?.goodBoardCount ?? '—' }} / {{ selectedProbeRecord?.badBoardCount ?? '—' }}</strong></div>
            <div><span>飞针设备检测时间</span><strong>{{ formatDateTime(selectedProbeRecord?.deviceTime) }}</strong></div>
            <div><span>关联生产指令 / 批次</span><strong>{{ selectedAssociation }}</strong></div>
          </template>
        </div>
      </section>

    </template>

    <el-card v-if="activePanel === 'records'" shadow="never" class="content-card finished-records-card">
      <template #header>
        <div class="card-header">
          <div><span>设备检测记录</span><small>按设备厂商或全部设备查看原始测试数据。</small></div>
          <div class="task-queue-actions"><span class="live-status"><i></i>{{ recordPanelUpdatedAt ? `更新于 ${formatDateTime(recordPanelUpdatedAt)}` : '尚未读取' }}</span><el-button link type="primary" :disabled="recordPanelLoading" @click="refreshRecordPanel">刷新</el-button></div>
        </div>
      </template>
      <div class="task-queue-actions" role="group" aria-label="记录来源">
        <el-button :type="recordSource === 'probe' || recordSource === 'retryable' ? 'primary' : 'default'" @click="showRecordSource('probe')">旺通达飞针机</el-button>
        <el-button :type="recordSource === 'zhongbo' ? 'primary' : 'default'" @click="showRecordSource('zhongbo')">众博信飞针机</el-button>
        <el-button :type="recordSource === 'all' ? 'primary' : 'default'" @click="showRecordSource('all')">全部设备记录</el-button>
      </div>
      <div class="report-filter finished-record-date-filter">
        <span>检测日期</span>
        <el-date-picker v-model="recordDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" clearable />
        <el-button :disabled="recordPanelLoading" @click="resetRecordDateFilter">重置</el-button>
      </div>
      <div class="probe-record-filters">
        <div v-if="recordSource === 'probe' || recordSource === 'retryable'" class="task-queue-actions" role="group" aria-label="旺通达记录匹配状态">
          <el-button :type="recordSource === 'probe' ? 'primary' : 'default'" :disabled="recordPanelLoading" @click="showRecordSource('probe')">全部记录</el-button>
          <el-button v-if="canBindFlyingProbe" :type="recordSource === 'retryable' ? 'primary' : 'default'" :disabled="recordPanelLoading" @click="showRecordSource('retryable')">未匹配</el-button>
        </div>
        <div v-if="recordSource === 'zhongbo'" class="task-queue-actions" role="group" aria-label="众博信记录匹配状态">
          <el-button :type="zhongboUnmatchedOnly ? 'default' : 'primary'" :disabled="recordPanelLoading" @click="showRecordSource('zhongbo')">全部记录</el-button>
          <el-button :type="zhongboUnmatchedOnly ? 'primary' : 'default'" :disabled="recordPanelLoading" @click="showUnmatchedZhongbo">未匹配</el-button>
        </div>
        <el-input v-model="recordWorkOrderNo" clearable placeholder="搜索生产指令" aria-label="搜索生产指令" @keyup.enter="searchRecordFilters" />
        <el-input v-model="recordMaterialNo" clearable :placeholder="recordMaterialPrompt" :aria-label="recordMaterialPrompt" @keyup.enter="searchRecordFilters" />
        <el-input v-model="recordFactoryInternalNo" clearable placeholder="搜索厂内编号" aria-label="搜索厂内编号" @keyup.enter="searchRecordFilters" />
        <el-button type="primary" :disabled="recordPanelLoading" @click="searchRecordFilters">查询</el-button>
      </div>
      <template v-if="recordSource === 'probe'">
        <p class="summary-footnote">按设备原始记录展示；未匹配或关联错误的飞针文件可手动绑定生产指令。</p>
        <el-alert v-if="flyingProbeError" type="error" :closable="false" :title="flyingProbeError" style="margin-top: 12px" />
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="task-queue-toolbar">
          <span>已加载 {{ flyingProbeRecords.length }} 条飞针机采集记录</span>
          <div v-if="canBindFlyingProbe" class="task-queue-actions finished-record-batch-actions">
            <el-button :type="selectedProbeRecords.length ? 'primary' : 'default'" :disabled="!selectedProbeRecords.length" @click="openSelectedRecordBindingDialog">批量绑定/更正（{{ selectedProbeRecords.length }}）</el-button>
            <el-button :disabled="!selectedProbeRecords.some((record) => record.workOrderNo)" @click="cancelSelectedRecordBindings">批量取消关联</el-button>
          </div>
        </div>
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="finished-records-table-wrap finished-records-table-wrap--probe">
          <paged-table :key="flyingProbeUpdatedAt?.getTime()" v-loading="flyingProbeLoading" :data="flyingProbeRecords" :has-more="!!flyingProbeNextCursor" :load-error="flyingProbeError" row-key="id" size="small" empty-text="暂无飞针机采集记录" @load-more="loadMoreFlyingProbeRecords" @selection-change="selectedProbeRecords = $event">
            <el-table-column v-if="canBindFlyingProbe" type="selection" width="55" reserve-selection :selectable="(row: EquipmentTelemetryRecord) => ['RETRYABLE', 'PROCESSED', 'UNBOUND'].includes(row.fqcProjectionStatus || '')" />
            <el-table-column label="设备检测时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.deviceTime) }}</template></el-table-column>
            <el-table-column prop="equipmentCode" label="检测设备" min-width="125" show-overflow-tooltip />
            <el-table-column label="生产指令" min-width="145" show-overflow-tooltip><template #default="scope">{{ scope.row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="设备料号" min-width="155" show-overflow-tooltip><template #default="scope">{{ scope.row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="厂内编号" min-width="150" show-overflow-tooltip><template #default="scope">{{ internalNo(scope.row) }}</template></el-table-column>
            <el-table-column label="检测总数" min-width="105" align="right"><template #default="scope">{{ scope.row.machiningCount ?? '—' }}</template></el-table-column>
            <el-table-column label="好板" min-width="100" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="坏板" min-width="100" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.badBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="实时合格率" min-width="120" align="right"><template #default="scope">{{ flyingProbePassRate(scope.row) }}</template></el-table-column>
            <el-table-column label="处理状态" width="200">
              <template #default="scope">
                <el-tooltip :content="scope.row.fqcProjectionReason || ''" :disabled="!scope.row.fqcProjectionReason" placement="top">
                  <div class="fqc-projection-state">
                    <el-tag size="small" :type="fqcProjectionTag(scope.row)">{{ fqcProjectionLabel(scope.row) }}</el-tag>
                    <span v-if="scope.row.fqcProjectionReason" class="fqc-projection-reason">{{ scope.row.fqcProjectionReason }}</span>
                  </div>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="120" fixed="right">
              <template #default="scope">
                <el-button v-if="canBindFlyingProbe && ['RETRYABLE', 'PROCESSED', 'UNBOUND'].includes(scope.row.fqcProjectionStatus)" link type="primary" @click="openBindingDialog(scope.row)">{{ scope.row.workOrderNo ? '更正关联' : '手动绑定' }}</el-button>
              </template>
            </el-table-column>
          </paged-table>
        </div>
      </template>
      <template v-else-if="recordSource === 'zhongbo'">
        <p class="summary-footnote">累计板数为设备上报原值；汇总和处置单使用相邻快照的新增数量。</p>
        <el-alert v-if="zhongboError" type="error" :closable="false" :title="zhongboError" style="margin-top: 12px" />
        <div v-if="zhongboLoaded || zhongboLoading" class="task-queue-toolbar">
          <span>已加载 {{ zhongboReports.length }} 条众博信测试报告</span>
          <div v-if="canBindFlyingProbe" class="task-queue-actions finished-record-batch-actions">
            <el-button :type="selectedZhongboRecords.length ? 'primary' : 'default'" :disabled="!selectedZhongboRecords.length" @click="openSelectedRecordBindingDialog">批量绑定/更正（{{ selectedZhongboRecords.length }}）</el-button>
            <el-button :disabled="!selectedZhongboRecords.some((record) => record.workOrderNo)" @click="cancelSelectedRecordBindings">批量取消关联</el-button>
          </div>
        </div>
        <div v-if="zhongboLoaded || zhongboLoading" class="finished-records-table-wrap finished-records-table-wrap--zhongbo">
          <paged-table :key="zhongboUpdatedAt?.getTime()" v-loading="zhongboLoading" :data="zhongboReports" :has-more="!!zhongboNextCursor" :load-error="zhongboError" row-key="id" size="small" empty-text="暂无众博信测试报告" @load-more="loadMoreZhongboReports" @selection-change="selectedZhongboRecords = $event">
            <el-table-column v-if="canBindFlyingProbe" type="selection" width="55" reserve-selection />
            <el-table-column type="expand">
              <template #default="scope">
                <el-descriptions :column="3" border size="small">
                  <el-descriptions-item label="设备标识">{{ scope.row.machineName || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="来源编号">{{ scope.row.sourceRecordNo || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="PortNumber">{{ scope.row.zhongboTestReport?.portNumber || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="生产指令">{{ scope.row.workOrderNo || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="批次">{{ scope.row.batchNo || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="测试总点数">{{ scope.row.zhongboTestReport?.totalPoints ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="开路数">{{ scope.row.zhongboTestReport?.openErrors ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="短路数">{{ scope.row.zhongboTestReport?.shortErrors ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="24 小时产量">{{ scope.row.zhongboTestReport?.counts ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="稼动率原值">{{ scope.row.zhongboTestReport?.utilizationRate ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="测板时长原值">{{ scope.row.zhongboTestReport?.utilizationDuration ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="探针打击数">{{ scope.row.zhongboTestReport?.hit ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="开路阈值">{{ scope.row.zhongboTestReport?.openThresh ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="短路阈值">{{ scope.row.zhongboTestReport?.shortThresh ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="故障码">{{ scope.row.alarmCode || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="故障说明">{{ scope.row.alarmMessage || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="QMS 接收时间">{{ formatDateTime(scope.row.receivedAt) }}</el-descriptions-item>
                </el-descriptions>
              </template>
            </el-table-column>
            <el-table-column label="设备检测时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.deviceTime) }}</template></el-table-column>
            <el-table-column prop="equipmentCode" label="检测设备" width="125" show-overflow-tooltip />
            <el-table-column label="生产指令" width="145" show-overflow-tooltip><template #default="scope">{{ scope.row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="PCB料号" width="160" show-overflow-tooltip><template #default="scope">{{ scope.row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="厂内编号" width="160" show-overflow-tooltip><template #default="scope">{{ internalNo(scope.row) }}</template></el-table-column>
            <el-table-column label="累计已测板数" width="120" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.panelsTested ?? '—' }}</template></el-table-column>
            <el-table-column label="累计好板数" width="120" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.panelsTested != null && scope.row.zhongboTestReport?.errorTested != null ? Math.max(0, scope.row.zhongboTestReport.panelsTested - scope.row.zhongboTestReport.errorTested) : '—' }}</template></el-table-column>
            <el-table-column label="累计错误板数" width="120" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.errorTested ?? '—' }}</template></el-table-column>
            <el-table-column label="合格率(%)" width="115" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.panelsTested > 0 && scope.row.zhongboTestReport?.errorTested != null ? `${((scope.row.zhongboTestReport.panelsTested - scope.row.zhongboTestReport.errorTested) / scope.row.zhongboTestReport.panelsTested * 100).toFixed(2)}%` : '—' }}</template></el-table-column>
            <el-table-column label="测试总点数" width="115" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.totalPoints ?? '—' }}</template></el-table-column>
            <el-table-column label="开路数" width="95" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.openErrors ?? '—' }}</template></el-table-column>
            <el-table-column label="短路数" width="95" align="right"><template #default="scope">{{ scope.row.zhongboTestReport?.shortErrors ?? '—' }}</template></el-table-column>
            <el-table-column label="设备状态" min-width="120"><template #default="scope">{{ zhongboStatusText(scope.row.status) }}</template></el-table-column>
            <el-table-column label="操作" width="110" fixed="right"><template #default="scope"><el-button v-if="canBindFlyingProbe" link type="primary" @click="openBindingDialog(scope.row)">{{ scope.row.workOrderNo ? '更正关联' : '手动绑定' }}</el-button></template></el-table-column>
          </paged-table>
        </div>
      </template>
      <template v-else-if="recordSource === 'retryable' && canBindFlyingProbe">
        <p class="summary-footnote">按飞针文件汇总待匹配快照；原始采集记录过期后，仍可在这里绑定或重试。</p>
        <el-alert v-if="retryableError" type="error" :closable="false" :title="retryableError" style="margin-top: 12px" />
        <div v-if="retryableLoaded || retryableLoading" class="task-queue-toolbar">
          <span>共 {{ retryableTotal }} 份待匹配飞针文件</span>
          <el-button type="primary" :disabled="!selectedRetryableRecords.length || retryableLoading" @click="openBatchBindingDialog">手动批量绑定（{{ selectedRetryableRecords.length }}）</el-button>
        </div>
        <div v-if="retryableLoaded || retryableLoading" class="finished-records-table-wrap finished-records-table-wrap--retryable">
          <el-table v-loading="retryableLoading" :data="retryableRecords" size="small" empty-text="暂无待匹配飞针文件" @selection-change="updateRetryableSelection">
            <el-table-column type="selection" width="55" :selectable="canSelectRetryableRecord" />
            <el-table-column label="设备检测时间" width="170"><template #default="{ row }">{{ formatDateTime(row.deviceTime) }}</template></el-table-column>
            <el-table-column label="检测设备" width="135" show-overflow-tooltip><template #default="{ row }">{{ row.equipmentCode || row.machineName || '—' }}</template></el-table-column>
            <el-table-column label="生产指令" width="145" show-overflow-tooltip><template #default="{ row }">{{ row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="设备料号" width="145" show-overflow-tooltip><template #default="{ row }">{{ row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="来源文件" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ row.sourceFile || '—' }}</template></el-table-column>
            <el-table-column label="待匹配原因" min-width="280" show-overflow-tooltip><template #default="{ row }">{{ row.reason || '—' }}</template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openBindingDialog(row)">{{ row.workOrderNo ? '重试处理' : '手动绑定' }}</el-button></template></el-table-column>
          </el-table>
        </div>
        <div v-if="retryableLoaded && retryableTotal > retryablePageSize" class="task-queue-toolbar">
          <el-pagination :current-page="retryablePage" :page-size="retryablePageSize" :total="retryableTotal" background layout="prev, pager, next, jumper" @current-change="loadRetryableProjections" />
        </div>
      </template>
      <template v-else>
        <el-alert v-if="finishedRecordsError" type="error" :closable="false" :title="finishedRecordsError" style="margin-top: 12px" />
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="task-queue-toolbar">
          <el-input v-model="recordKeyword" clearable placeholder="筛选已加载记录：检测设备、生产指令、厂内编号或PCB料号" />
          <span>已加载 {{ finishedTestRecords.length }} 条{{ recordKeyword.trim() ? `，其中匹配 ${filteredFinishedTestRecords.length} 条` : '' }}</span>
          <div v-if="canBindFlyingProbe" class="task-queue-actions finished-record-batch-actions">
            <el-button :type="selectedAllRecords.length ? 'primary' : 'default'" :disabled="!selectedAllRecords.length" @click="openSelectedRecordBindingDialog">批量绑定/更正（{{ selectedAllRecords.length }}）</el-button>
            <el-button :disabled="!selectedAllRecords.some((record) => record.workOrderNo)" @click="cancelSelectedRecordBindings">批量取消关联</el-button>
          </div>
        </div>
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="finished-records-table-wrap">
          <paged-table :key="`${recordKeyword}:${finishedRecordsUpdatedAt?.getTime() ?? 0}`" v-loading="finishedRecordsLoading" :data="filteredFinishedTestRecords" :has-more="!!finishedRecordsNextCursor && !recordKeyword.trim()" :load-error="finishedRecordsError" row-key="id" size="small" empty-text="暂无设备检测记录" @load-more="loadDeviceRecords(finishedRecordsNextCursor)" @selection-change="selectedAllRecords = $event">
            <el-table-column v-if="canBindFlyingProbe" type="selection" width="55" reserve-selection :selectable="(row: EquipmentTelemetryRecord) => row.eventType === 'ZHONGBO_TEST_REPORT' || ['RETRYABLE', 'PROCESSED', 'UNBOUND'].includes(row.fqcProjectionStatus || '')" />
            <el-table-column label="检测时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.deviceTime) }}</template></el-table-column>
            <el-table-column prop="equipmentCode" label="检测设备" min-width="130" />
            <el-table-column label="生产指令" min-width="145"><template #default="scope">{{ scope.row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="厂内编号" min-width="150"><template #default="scope">{{ internalNo(scope.row) }}</template></el-table-column>
            <el-table-column label="PCB料号" min-width="140"><template #default="scope">{{ scope.row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="检测总数" min-width="105" align="right"><template #default="scope">{{ scope.row.eventType === 'ZHONGBO_TEST_REPORT' ? (scope.row.zhongboTestReport?.panelsTested ?? '—') : (scope.row.machiningCount ?? '—') }}</template></el-table-column>
            <el-table-column label="好板" min-width="90" align="right"><template #default="scope"><span class="good-text">{{ scope.row.eventType === 'ZHONGBO_TEST_REPORT' ? (scope.row.zhongboTestReport?.panelsTested != null && scope.row.zhongboTestReport?.errorTested != null ? Math.max(0, scope.row.zhongboTestReport.panelsTested - scope.row.zhongboTestReport.errorTested) : '—') : (scope.row.goodBoardCount ?? '—') }}</span></template></el-table-column>
            <el-table-column label="坏板" min-width="90" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.eventType === 'ZHONGBO_TEST_REPORT' ? (scope.row.zhongboTestReport?.errorTested ?? '—') : (scope.row.badBoardCount ?? '—') }}</span></template></el-table-column>
            <el-table-column label="操作" width="115" fixed="right"><template #default="scope"><el-button v-if="canBindFlyingProbe && (scope.row.eventType === 'ZHONGBO_TEST_REPORT' || ['RETRYABLE', 'PROCESSED', 'UNBOUND'].includes(scope.row.fqcProjectionStatus))" link type="primary" @click="openBindingDialog(scope.row)">{{ scope.row.workOrderNo ? '更正关联' : '手动绑定' }}</el-button></template></el-table-column>
          </paged-table>
        </div>
      </template>
    </el-card>

    <el-card v-if="activePanel === 'summary'" shadow="never" class="content-card summary-table-card">
      <template #header>
        <div class="card-header">
          <div><span>成品测试汇总表</span><small>按生产指令、厂内编号或检测日期＋设备编号＋生产指令查看汇总；未关联生产指令的记录暂不计入。</small></div>
          <span class="live-status"><i></i>更新于 {{ updatedAtLabel }}</span>
        </div>
      </template>
      <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" style="margin-bottom: 12px" />
      <div role="group" aria-label="汇总维度" class="finished-summary-dimensions">
        <el-button :type="summaryDimension === 'workOrderNo' ? 'primary' : 'default'" :aria-pressed="summaryDimension === 'workOrderNo'" @click="summaryDimension = 'workOrderNo'">生产指令</el-button>
        <el-button :type="summaryDimension === 'internalProductNo' ? 'primary' : 'default'" :aria-pressed="summaryDimension === 'internalProductNo'" @click="summaryDimension = 'internalProductNo'">厂内编号</el-button>
        <el-button :type="summaryDimension === 'dateEquipmentOrder' ? 'primary' : 'default'" :aria-pressed="summaryDimension === 'dateEquipmentOrder'" @click="summaryDimension = 'dateEquipmentOrder'">检测日期＋设备编号＋生产指令</el-button>
      </div>
      <div class="report-filter finished-summary-filters">
        <span>检测日期</span>
        <el-date-picker v-model="summaryDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" clearable />
        <el-input v-model="summaryWorkOrder" clearable placeholder="筛选生产指令" aria-label="筛选生产指令" />
        <el-input v-model="summaryPcbMaterial" clearable placeholder="筛选 PCB 料号" aria-label="筛选 PCB 料号" />
        <el-button @click="resetSummaryFilters">重置</el-button>
      </div>
      <paged-table v-if="!summaryError" :key="summaryFilterKey" :data="groupedDeviceSummary" empty-text="当前筛选条件下暂无设备检测记录">
        <el-table-column v-if="summaryDimension === 'dateEquipmentOrder'" prop="inspectionDate" label="检测日期" min-width="140" />
        <el-table-column v-if="summaryDimension === 'dateEquipmentOrder'" prop="equipmentCode" label="设备编号" min-width="135" />
        <el-table-column v-if="summaryDimension !== 'internalProductNo'" prop="workOrderNo" label="生产指令" min-width="150" />
        <el-table-column prop="internalProductNo" label="厂内编号" min-width="160" />
        <el-table-column v-if="summaryDimension !== 'internalProductNo'" prop="pcbMaterialNo" label="料号" min-width="165" show-overflow-tooltip><template #default="scope">{{ scope.row.pcbMaterialNo || '—' }}</template></el-table-column>
        <el-table-column prop="totalCount" label="检测总数" min-width="130" align="right" />
        <el-table-column prop="goodCount" label="合格" min-width="110" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodCount }}</span></template></el-table-column>
        <el-table-column prop="badCount" label="不合格" min-width="110" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.badCount }}</span></template></el-table-column>
        <el-table-column label="合格率" min-width="190"><template #default="scope"><el-progress :percentage="scope.row.passRate" :stroke-width="9" :color="scope.row.passRate >= 98.5 ? '#12b76a' : '#f79009'" /></template></el-table-column>
      </paged-table>
      <div v-if="!summaryError" class="summary-footnote">当前筛选结果：{{ filteredSummaryTotals.tested }} 板，合格 {{ filteredSummaryTotals.good }} 板，不合格 {{ filteredSummaryTotals.bad }} 板（按{{ summaryDimensionLabel }}汇总，共 {{ groupedDeviceSummary.length }} 组）。</div>
    </el-card>

    <el-card v-if="activePanel === 'summary'" shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <div><span>12 小时静默结算快照</span><small>同料号所有设备连续 12 小时没有新增已测板后自动生成；显示最近 100 轮，快照不重复计入上方汇总。</small></div>
          <el-button @click="refreshWorkbench">刷新</el-button>
        </div>
      </template>
      <el-alert v-if="settlementError" type="error" :closable="false" :title="settlementError" style="margin-bottom: 12px" />
      <paged-table v-else :key="summaryFilterKey" :data="filteredSettlements" empty-text="当前筛选条件下暂无已结算的检测轮次">
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

    <el-dialog v-model="bindingDialog" :title="bindingBatchRecords.length ? '手动批量绑定生产指令' : '手动绑定生产指令'" width="min(760px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!bindingSaving" :show-close="!bindingSaving">
      <p v-if="bindingRecord && bindingBatchRecords.length">已选 {{ bindingBatchRecords.length }} 条设备记录 · 设备料号 {{ bindingRecord.materialNo }}</p>
      <p v-else-if="bindingRecord">{{ bindingRecord.equipmentCode || bindingRecord.machineName || '未关联设备' }} · 料号 {{ bindingRecord.materialNo || '未上报' }} · 检测时间 {{ formatDateTime(bindingRecord.deviceTime) }}<span v-if="bindingRecord.sourceFile"> · {{ bindingRecord.sourceFile }}</span></p>
      <el-alert type="warning" :closable="false" :title="bindingRecord?.eventType === 'ZHONGBO_TEST_REPORT' ? '可更正未审核设备记录的生产指令；已审核处置单的来源关联不可更改。' : '同一飞针文件统一关联生产指令；已有已审核或逐条创建的处置单时不能更正。'" />
      <el-alert v-if="bindingRecord?.workOrderNo && bindingRecord.eventType !== 'ZHONGBO_TEST_REPORT'" type="info" :closable="false" :title="`该文件当前绑定工单 ${bindingRecord.workOrderNo}；更正将调整同文件记录。`" style="margin-top: 10px" />
      <div class="binding-order-search">
        <el-input v-model="bindingKeyword" clearable placeholder="输入生产指令号或厂内编号，支持模糊查询" @keyup.enter="searchBindingWorkOrders" />
        <el-button :loading="bindingSearchLoading" @click="searchBindingWorkOrders">查询工单</el-button>
      </div>
      <el-alert v-if="bindingError" type="error" :closable="false" :title="bindingError" />
      <el-table v-loading="bindingSearchLoading" :data="bindingWorkOrders" size="small" max-height="300" empty-text="输入关键字后点击查询工单，支持模糊匹配">
        <el-table-column label="选择" width="70"><template #default="{ row }"><input type="radio" name="device-binding-order" :checked="bindingWorkOrderNo === row.workOrderNo" :aria-label="`选择工单 ${row.workOrderNo}`" @change="bindingWorkOrderNo = row.workOrderNo" /></template></el-table-column>
        <el-table-column prop="workOrderNo" label="生产指令号" min-width="145" />
        <el-table-column label="厂内编号" min-width="180"><template #default="{ row }">{{ row.internalProductNo || '—' }}</template></el-table-column>
        <el-table-column label="下达日期" min-width="130"><template #default="{ row }">{{ formatDate(row.releasedAt) }}</template></el-table-column>
      </el-table>
      <p class="summary-footnote">最多显示前 50 条；如未看到目标工单，请输入更完整的工单号查询。</p>
      <template #footer><el-button :disabled="bindingSaving" @click="bindingDialog = false">取消</el-button><el-button type="primary" :loading="bindingSaving" :disabled="!bindingWorkOrderNo" @click="submitBinding">{{ bindingBatchRecords.length ? '确认批量绑定' : bindingRecord?.workOrderNo ? (bindingRecord.workOrderNo === bindingWorkOrderNo ? '重试处理' : '更正关联') : '确认绑定' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="recordDialog" class="inspection-record-dialog" title="提交检验记录" width="min(920px, 94vw)" :close-on-click-modal="false">
      <el-alert :title="selectedTask ? `${selectedTask.taskNo} · ${selectedTask.processName}` : ''" type="info" :closable="false" class="dialog-alert" />
      <el-form ref="recordFormRef" :model="recordForm" :rules="recordRules" label-position="top" v-loading="templateLoading">
        <div class="form-grid">
          <el-form-item label="检测设备">
            <el-select v-model="recordEquipmentAssetNo" clearable class="full-width" placeholder="未关联设备（可选）">
              <el-option v-for="device in monitorDevices" :key="device.assetNo" :label="`${device.name}（${device.assetNo}）`" :value="device.assetNo" />
            </el-select>
          </el-form-item>
          <el-form-item label="检验结论" prop="verdict"><el-radio-group v-model="recordForm.verdict"><el-radio-button value="PASS">合格</el-radio-button><el-radio-button value="FAIL">不合格</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="不良数量" prop="defectiveQuantity"><el-input-number v-model="recordForm.defectiveQuantity" :min="0" class="full-width" /></el-form-item>
        </div>
        <template v-if="selectedTemplate">
          <el-divider content-position="left">{{ selectedTemplate.name }} · V{{ selectedTemplate.versionNo }}</el-divider>
          <div class="form-grid">
            <el-form-item v-for="item in selectedTemplate.items" :key="item.itemCode" :label="`${item.itemName}${item.required ? ' *' : ''}`">
              <el-input v-if="item.inputType === 'TEXT'" v-model="recordValues[item.itemCode]" :placeholder="item.helpText || '填写检验结果'" />
              <el-input-number v-else-if="item.inputType === 'NUMBER'" v-model="recordValues[item.itemCode]" class="full-width" :placeholder="item.helpText" />
              <el-select v-else-if="item.inputType === 'SELECT'" v-model="recordValues[item.itemCode]" class="full-width" :placeholder="item.helpText || '请选择'">
                <el-option v-for="option in item.options" :key="option" :label="option" :value="option" />
              </el-select>
              <el-radio-group v-else v-model="recordValues[item.itemCode]"><el-radio-button value="true">是</el-radio-button><el-radio-button value="false">否</el-radio-button></el-radio-group>
              <div v-if="displayRange(item.lowerLimit, item.upperLimit, item.unit) || item.helpText" class="field-help">
                <span v-if="displayRange(item.lowerLimit, item.upperLimit, item.unit)">控制范围：{{ displayRange(item.lowerLimit, item.upperLimit, item.unit) }}</span>
                <span v-if="item.helpText">{{ item.helpText }}</span>
              </div>
            </el-form-item>
          </div>
        </template>
        <div v-else class="form-grid">
          <el-form-item label="测量值"><el-input v-model="recordForm.measurement" placeholder="例如：1.58 mm" /></el-form-item>
          <el-form-item label="规格上下限"><div class="range-input"><el-input v-model="recordForm.lowerLimit" placeholder="下限" /><span>至</span><el-input v-model="recordForm.upperLimit" placeholder="上限" /></div></el-form-item>
        </div>
        <template v-if="recordForm.verdict === 'FAIL'"><el-form-item label="缺陷说明"><el-input v-model="recordForm.defectSummary" type="textarea" :rows="3" placeholder="填写缺陷类型与处置建议" /></el-form-item><el-form-item label="缺陷位置"><el-input v-model="recordForm.defectLocation" placeholder="例如：A 面左上角、板号 3、孔位 J12" /></el-form-item></template>
        <el-form-item label="检验照片"><el-upload v-model:file-list="inspectionPhotoFiles" class="inspection-photo-uploader" accept="image/jpeg,image/png,image/webp" :auto-upload="false" :limit="5" list-type="picture-card" :on-exceed="() => ElMessage.warning('一次最多上传 5 张检验照片')"><span>+</span><template #tip><div class="el-upload__tip">支持 JPG、PNG、WEBP，单张最大 10 MB；提交检验时上传并关联。</div></template></el-upload></el-form-item>
      </el-form>
      <template #footer><div class="inspection-dialog-footer"><span>快捷键：<kbd>Ctrl</kbd> + <kbd>Enter</kbd> 提交</span><div><el-button @click="recordDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitRecord">提交检验</el-button></div></div></template>
    </el-dialog>
  </section>
</template>
