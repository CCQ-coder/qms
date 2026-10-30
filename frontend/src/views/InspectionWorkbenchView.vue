<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDateTime, taskTypeText } from '@/utils/display-text'
import { clampPage, paginateRows } from '@/utils/pagination'
import { createClientRequestId, listInspectionDrafts, removeInspectionDraft, saveInspectionDraft, type OfflineInspectionDraft, type OfflineInspectionPayload } from '@/offline/inspectionDraftStore'
import { deriveInspectionMonitorStatus } from './inspectionMonitorStatus'
import { useFlyingProbeHistory } from './useFlyingProbeHistory'
import type { Equipment, EquipmentTelemetryRecord, FinishedTestInspectionRecord, GatewayHostStatus, InspectionTask, InspectionTemplate, QualitySummary } from '@/types/qms'

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
const activePanel = ref<'monitor' | 'records' | 'tasks' | 'summary'>(isSummaryRoute.value ? 'summary' : route.query.panel === 'records' ? 'records' : 'monitor')
const monitoringLoading = ref(false)
const equipment = ref<Equipment[]>([])
const hostStatuses = ref<GatewayHostStatus[]>([])
const hostOfflineAfterSeconds = ref(300)
const finishedTestRecords = ref<FinishedTestInspectionRecord[]>([])
const finishedRecordsNextCursor = ref<string>()
const finishedRecordsLoading = ref(false)
const finishedRecordsUpdatedAt = ref<Date>()
const probeSnapshots = ref<Record<string, EquipmentTelemetryRecord>>({})
const probeFailedEquipmentIds = ref<Set<string>>(new Set())
const summaryError = ref('')
const equipmentError = ref('')
const hostStatusError = ref('')
const probeSnapshotsError = ref('')
const finishedRecordsError = ref('')
const recordSource = ref<'probe' | 'fqc'>('probe')
const {
  records: flyingProbeRecords,
  nextCursor: flyingProbeNextCursor,
  loading: flyingProbeLoading,
  loaded: flyingProbeLoaded,
  error: flyingProbeError,
  updatedAt: flyingProbeUpdatedAt,
  refresh: refreshFlyingProbeRecords,
  loadMore: loadMoreFlyingProbeRecords
} = useFlyingProbeHistory((query) => qmsApi.listEquipmentTelemetryHistory(query))
watch([isSummaryRoute, () => route.query.panel], ([summaryRoute, panel]) => {
  activePanel.value = summaryRoute ? 'summary' : panel === 'records' ? 'records' : 'monitor'
  if (activePanel.value === 'records' && !flyingProbeLoaded.value) void refreshFlyingProbeRecords()
})
const qualitySummary = ref<QualitySummary>({ totalRecords: 0, passRecords: 0, failRecords: 0, firstPassRate: 0, byProcess: [], defectRanking: [], dailyTrend: [] })
const selectedDeviceId = ref('')
const taskListDeviceAssetNo = ref('')
const recordEquipmentAssetNo = ref('')
const taskKeyword = ref('')
const recordKeyword = ref('')
const updatedAt = ref<Date>()
const finishedTestEquipmentType = '成品测试设备'

type MonitorDevice = Equipment & {
  hostStatus?: GatewayHostStatus
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
  const hostByEquipmentId = new Map<string, GatewayHostStatus>()
  hostStatuses.value.forEach((status) => {
    if (!status.equipmentId) return
    const existing = hostByEquipmentId.get(status.equipmentId)
    if (!existing || Date.parse(status.receivedAt) > Date.parse(existing.receivedAt)) {
      hostByEquipmentId.set(status.equipmentId, status)
    }
  })
  return equipment.value.filter((item) => item.equipmentType === finishedTestEquipmentType).map((device) => ({
    ...device,
    hostStatus: hostByEquipmentId.get(device.id),
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
const fqcTotal = computed(() => qualitySummary.value.totalRecords)
const fqcPass = computed(() => qualitySummary.value.passRecords)
const fqcFail = computed(() => qualitySummary.value.failRecords)
const fqcPassRate = computed(() => qualitySummary.value.firstPassRate)
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
    record.equipmentAssetNo, record.workOrderNo, record.internalProductNo,
    record.productCode, record.taskNo, record.inspectedBy, record.defectSummary
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

async function loadCompletedFinishedRecords(cursor?: string) {
  if (finishedRecordsLoading.value) return false
  finishedRecordsLoading.value = true
  finishedRecordsError.value = ''
  try {
    const page = await qmsApi.listCompletedFinishedTestRecords(100, cursor)
    finishedTestRecords.value = cursor ? [...finishedTestRecords.value, ...page.items] : page.items
    finishedRecordsNextCursor.value = page.nextCursor ?? undefined
    if (!cursor) finishedRecordsUpdatedAt.value = new Date()
    return true
  } catch {
    finishedRecordsError.value = cursor
      ? '更早的 FQC 检验记录加载失败，请重试。'
      : '已提交 FQC 检验记录读取失败，请重试。'
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

let monitoringVersion = 0
let hostStatusPollInFlight = false
let hostStatusPollTimer: number | undefined
let componentMounted = false

async function pollHostStatuses() {
  if (monitoringLoading.value || hostStatusPollInFlight) return
  hostStatusPollInFlight = true
  const version = monitoringVersion
  try {
    const statuses = await qmsApi.listGatewayHostStatuses()
    if (version !== monitoringVersion || !componentMounted) return
    hostStatuses.value = statuses
    hostStatusError.value = ''
  } catch {
    if (version !== monitoringVersion || !componentMounted) return
    hostStatuses.value = []
    hostStatusError.value = '设备电脑心跳读取失败，请重试。'
  } finally {
    hostStatusPollInFlight = false
  }
}

async function loadMonitoringData() {
  monitoringLoading.value = true
  monitoringVersion++
  try {
    const [summaryResult, equipmentResult, hostStatusResult, recordsResult, offlineThresholdResult] = await Promise.allSettled([
      qmsApi.getQualitySummary(undefined, undefined, undefined, true),
      qmsApi.listEquipment(),
      qmsApi.listGatewayHostStatuses(),
      loadCompletedFinishedRecords(),
      qmsApi.getTenantParameter('equipment.monitor.offline-after-seconds')
    ])
    summaryError.value = summaryResult.status === 'rejected' ? 'FQC 质量汇总读取失败，请重试。' : ''
    equipmentError.value = equipmentResult.status === 'rejected' ? '成品测试设备台账读取失败，请重试。' : ''
    hostStatusError.value = hostStatusResult.status === 'rejected' ? '设备电脑心跳读取失败，请重试。' : ''
    if (summaryResult.status === 'fulfilled') qualitySummary.value = summaryResult.value
    if (equipmentResult.status === 'fulfilled') equipment.value = equipmentResult.value
    else equipment.value = []
    if (hostStatusResult.status === 'fulfilled') hostStatuses.value = hostStatusResult.value
    else hostStatuses.value = []
    if (offlineThresholdResult.status === 'fulfilled') {
      const configured = Number.parseInt(offlineThresholdResult.value.value, 10)
      if (Number.isFinite(configured)) hostOfflineAfterSeconds.value = Math.min(86_400, Math.max(30, configured))
    }
    if (recordsResult.status === 'rejected') {
      finishedRecordsError.value = '已提交 FQC 检验记录读取失败，请重试。'
    }
    const probesLoaded = equipmentResult.status === 'fulfilled'
      ? await loadProbeSnapshots(equipmentResult.value.filter((item) => item.equipmentType === finishedTestEquipmentType))
      : false
    if (equipmentResult.status === 'rejected') {
      probeSnapshots.value = {}
      probeFailedEquipmentIds.value = new Set()
      probeSnapshotsError.value = ''
    }
    if (probesLoaded && recordsResult.status === 'fulfilled' && recordsResult.value
      && [summaryResult, equipmentResult, hostStatusResult].every((result) => result.status === 'fulfilled')) {
      updatedAt.value = new Date()
    }
  } finally {
    monitoringLoading.value = false
  }
}

async function refreshWorkbench() {
  if (monitoringLoading.value) return
  await Promise.all([loadTasks(), loadMonitoringData(), loadOfflineDrafts()])
  if (!monitorDevices.value.some((device) => device.assetNo === selectedDeviceId.value)) {
    selectedDeviceId.value = monitorDevices.value[0]?.assetNo ?? ''
  }
}

function showPanel(panel: 'monitor' | 'records' | 'tasks') {
  activePanel.value = panel
  taskListDeviceAssetNo.value = ''
  if (panel === 'records') {
    recordSource.value = 'probe'
    if (!flyingProbeLoaded.value) void refreshFlyingProbeRecords()
  }
}

function showRecordSource(source: 'probe' | 'fqc') {
  recordSource.value = source
  if (source === 'probe' && !flyingProbeLoaded.value) void refreshFlyingProbeRecords()
}

async function refreshRecordPanel() {
  if (recordSource.value === 'probe') await refreshFlyingProbeRecords()
  else await loadCompletedFinishedRecords()
}

function deviceStatusLabel(device: MonitorDevice) {
  if (hostStatusError.value) return '状态不可用'
  return deriveInspectionMonitorStatus(
    device.hostStatus?.receivedAt,
    device.probe?.receivedAt,
    Date.now(),
    hostOfflineAfterSeconds.value
  )
}

function deviceStatusTag(device: MonitorDevice): 'success' | 'warning' | 'info' {
  if (hostStatusError.value) return 'info'
  const status = deviceStatusLabel(device)
  if (status === '测试中') return 'success'
  return status === '空闲' ? 'warning' : 'info'
}

function deviceComputerStatusLabel(device: MonitorDevice) {
  if (hostStatusError.value) return '读取失败'
  const status = deviceStatusLabel(device)
  if (status === '测试中' || status === '空闲') return '在线'
  return status === '未接入' ? '尚未上报' : status
}

function isDeviceComputerOnline(device: MonitorDevice) {
  const status = deviceStatusLabel(device)
  return status === '测试中' || status === '空闲'
}

function fqcProjectionLabel(record: EquipmentTelemetryRecord) {
  if (record.fqcProjectionStatus === 'PROCESSED') {
    if (record.fqcInspectionRecordId) return '已生成 FQC'
    if (record.fqcProjectionReason?.includes('基线')) return '已作基线'
    return record.fqcDeltaTested === 0 ? '无新增板' : '已处理'
  }
  if (record.fqcProjectionStatus === 'RETRYABLE') return '待匹配'
  if (record.fqcProjectionStatus === 'SKIPPED') return '已跳过'
  if (record.fqcProjectionStatus === 'PENDING') return '待处理'
  return record.fqcProjectionStatus || '待处理'
}

function fqcProjectionTag(record: EquipmentTelemetryRecord): 'success' | 'warning' | 'info' {
  if (record.fqcInspectionRecordId) return 'success'
  if (record.fqcProjectionStatus === 'RETRYABLE') return 'warning'
  return 'info'
}

function openSelectedDeviceTask() {
  taskListDeviceAssetNo.value = selectedDevice.value?.assetNo ?? ''
  activePanel.value = 'tasks'
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
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('keydown', handleKeyboardShortcut)
  hostStatusPollTimer = window.setInterval(() => { void pollHostStatuses() }, 30_000)
  if (activePanel.value === 'records') void refreshFlyingProbeRecords()
  await refreshWorkbench()
  if (!componentMounted) return
  if (navigator.onLine && offlineDrafts.value.length) void syncOfflineDrafts()
})

onBeforeUnmount(() => {
  componentMounted = false
  window.clearInterval(hostStatusPollTimer)
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
        <p class="eyebrow">FQC 成品检测</p>
        <h2>成品测试实时监控工作台</h2>
        <p>集中查看成品测试工位、待检队列和质量结果；检测记录可通过扫码或任务列表直接处理。</p>
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
      <button :class="{ active: activePanel === 'tasks' }" type="button" @click="showPanel('tasks')">待检任务</button>
    </nav>

    <template v-if="activePanel === 'monitor'">
      <section class="monitor-panel">
        <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" style="margin-bottom: 12px" />
        <el-alert v-if="equipmentError" type="error" :closable="false" :title="equipmentError" style="margin-bottom: 12px" />
        <el-alert v-if="hostStatusError" type="error" :closable="false" :title="hostStatusError" style="margin-bottom: 12px" />
        <el-alert v-if="probeSnapshotsError" type="error" :closable="false" :title="probeSnapshotsError" style="margin-bottom: 12px" />
        <el-alert v-if="finishedRecordsError" type="error" :closable="false" :title="finishedRecordsError" style="margin-bottom: 12px" />
        <div class="section-heading">
          <div>
            <h3>成品测试多设备实时监控</h3>
            <p>仅展示设备台账中类型为“{{ finishedTestEquipmentType }}”的设备；设备上报与已提交 FQC 检验分别统计。</p>
          </div>
          <div v-if="!summaryError" class="shift-summary">
            <span>FQC 已提交 <strong>{{ fqcTotal }}</strong></span>
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
            :class="{ 'monitor-device-card--selected': selectedDevice?.assetNo === device.assetNo, 'monitor-device-card--offline': !isDeviceComputerOnline(device) }"
            type="button"
            @click="selectDevice(device.assetNo)"
          >
            <span class="device-card-head">
              <strong>{{ device.name }}</strong>
              <el-tag size="small" :type="deviceStatusTag(device)">{{ deviceStatusLabel(device) }}</el-tag>
            </span>
            <span class="device-card-type">{{ device.assetNo }} · {{ device.equipmentType }}</span>
            <span class="device-task">
              <small>最新飞针 QMS 接收</small>
              <b>{{ device.probeError ? '读取失败' : formatDateTime(device.probe?.receivedAt) }}</b>
              <em>电脑状态：{{ deviceComputerStatusLabel(device) }}</em>
            </span>
            <span class="device-card-footer"><span>飞针已测 {{ device.probeError ? '—' : (device.probe?.machiningCount ?? '—') }}</span><span>FQC 检验见记录列表</span></span>
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
            <div><span>QMS 接收时间</span><strong>{{ formatDateTime(selectedProbeRecord?.receivedAt) }}</strong></div>
            <div><span>关联生产指令 / 批次</span><strong>{{ selectedAssociation }}</strong></div>
          </template>
          <div><span>FQC 待检任务</span><strong>未关联待检任务，请从列表选择</strong></div>
          <el-button type="primary" @click="openSelectedDeviceTask">查看待检任务</el-button>
        </div>
      </section>

    </template>

    <el-card v-if="activePanel === 'tasks'" shadow="never" class="content-card task-queue-card">
      <template #header>
        <div class="card-header">
          <div><span>成品测试待检任务</span><small>任务按当前登录用户的工序权限返回，支持扫码定位后直接填写检验。</small></div>
          <div class="task-queue-actions"><el-button link type="primary" @click="refreshWorkbench">刷新</el-button><el-button type="primary" size="small" :disabled="!filteredTasks.length" @click="openNextTask">处理下一条</el-button></div>
        </div>
      </template>
      <div class="task-queue-toolbar">
        <el-input v-model="taskKeyword" clearable placeholder="任务号、工单号或检验员" />
        <span>共 {{ filteredTasks.length }} 条待处理任务</span>
      </div>
      <el-alert v-if="taskListDeviceAssetNo" type="info" :closable="false" :title="`从设备 ${taskListDeviceAssetNo} 进入：选择任务后将预填检测设备；任务未与设备绑定，请提交前核对。`" style="margin-bottom: 12px" />
      <paged-table :key="taskKeyword" v-loading="loading" :data="filteredTasks" size="small" empty-text="暂无成品测试待检任务" @row-dblclick="openRecord">
        <el-table-column prop="taskNo" label="任务号" min-width="150" />
        <el-table-column prop="workOrderNo" label="工单号" min-width="145" />
        <el-table-column prop="processName" label="工序" min-width="130" />
        <el-table-column label="类型" min-width="100"><template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template></el-table-column>
        <el-table-column prop="assigneeName" label="检验员" min-width="110" />
        <el-table-column label="计划完成" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.dueAt) }}</template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="scope"><el-button type="primary" link @click="openRecord(scope.row)">填写检验</el-button></template></el-table-column>
      </paged-table>
    </el-card>

    <el-card v-if="activePanel === 'records'" shadow="never" class="content-card finished-records-card">
      <template #header>
        <div class="card-header">
          <div><span>设备检测记录</span><small>飞针机采集计数与已提交的 FQC 检验分别查看。</small></div>
          <div class="task-queue-actions"><span class="live-status"><i></i>{{ recordSource === 'probe' ? (flyingProbeUpdatedAt ? `更新于 ${formatDateTime(flyingProbeUpdatedAt)}` : '尚未读取') : (finishedRecordsUpdatedAt ? `更新于 ${formatDateTime(finishedRecordsUpdatedAt)}` : '尚未读取') }}</span><el-button link type="primary" :disabled="recordSource === 'probe' ? flyingProbeLoading : finishedRecordsLoading" @click="refreshRecordPanel">刷新</el-button></div>
        </div>
      </template>
      <div class="task-queue-actions" role="group" aria-label="记录来源">
        <el-button :type="recordSource === 'probe' ? 'primary' : 'default'" @click="showRecordSource('probe')">飞针机采集记录</el-button>
        <el-button :type="recordSource === 'fqc' ? 'primary' : 'default'" @click="showRecordSource('fqc')">已提交 FQC 检验</el-button>
      </div>
      <template v-if="recordSource === 'probe'">
        <p class="summary-footnote">采集快照与自动生成的 FQC 检验分开展示；只有满足标准和唯一工单匹配的新增板数会生成检验记录。</p>
        <el-alert v-if="flyingProbeError" type="error" :closable="false" :title="flyingProbeError" style="margin-top: 12px" />
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="task-queue-toolbar"><span>已加载 {{ flyingProbeRecords.length }} 条飞针机采集记录</span></div>
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="finished-records-table-wrap finished-records-table-wrap--probe">
          <paged-table v-loading="flyingProbeLoading" :data="flyingProbeRecords" :fit="false" size="small" empty-text="暂无飞针机采集记录">
            <el-table-column label="QMS 接收时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.receivedAt) }}</template></el-table-column>
            <el-table-column label="设备检测时间" width="170"><template #default="scope">{{ formatDateTime(scope.row.deviceTime) }}</template></el-table-column>
            <el-table-column prop="equipmentCode" label="检测设备" width="125" show-overflow-tooltip />
            <el-table-column label="料号" width="155" show-overflow-tooltip><template #default="scope">{{ scope.row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="已测" width="100" align="right"><template #default="scope">{{ scope.row.machiningCount ?? '—' }}</template></el-table-column>
            <el-table-column label="好板" width="100" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="坏板" width="100" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.badBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="FQC 处理" width="200">
              <template #default="scope">
                <el-tooltip :content="scope.row.fqcProjectionReason || ''" :disabled="!scope.row.fqcProjectionReason" placement="top">
                  <div class="fqc-projection-state">
                    <el-tag size="small" :type="fqcProjectionTag(scope.row)">{{ fqcProjectionLabel(scope.row) }}</el-tag>
                    <span v-if="scope.row.fqcProjectionReason" class="fqc-projection-reason">{{ scope.row.fqcProjectionReason }}</span>
                  </div>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column prop="sourceRecordNo" label="来源记录号" width="260" show-overflow-tooltip />
          </paged-table>
        </div>
        <div v-if="flyingProbeNextCursor" class="task-queue-toolbar"><span>还有更早的记录</span><el-button :loading="flyingProbeLoading" @click="loadMoreFlyingProbeRecords">加载更多</el-button></div>
      </template>
      <template v-else>
        <el-alert v-if="finishedRecordsError" type="error" :closable="false" :title="finishedRecordsError" style="margin-top: 12px" />
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="task-queue-toolbar">
          <el-input v-model="recordKeyword" clearable placeholder="筛选已加载记录：设备、工单、料号、任务号或检验员" />
          <span>已加载 {{ finishedTestRecords.length }} 条{{ recordKeyword.trim() ? `，其中匹配 ${filteredFinishedTestRecords.length} 条` : '' }}</span>
        </div>
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="finished-records-table-wrap">
          <paged-table :key="recordKeyword" :data="filteredFinishedTestRecords" size="small" empty-text="暂无已提交的 FQC 检验记录；飞针数据请查看“飞针机采集记录”">
            <el-table-column label="检验时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.inspectedAt) }}</template></el-table-column>
            <el-table-column label="检测设备" min-width="130"><template #default="scope"><el-tag :type="scope.row.equipmentAssetNo ? 'success' : 'info'">{{ scope.row.equipmentAssetNo || '未关联设备' }}</el-tag></template></el-table-column>
            <el-table-column prop="workOrderNo" label="生产指令" min-width="145" />
            <el-table-column prop="internalProductNo" label="厂内编号" min-width="150"><template #default="scope">{{ scope.row.internalProductNo || '—' }}</template></el-table-column>
            <el-table-column prop="productCode" label="料号" min-width="140"><template #default="scope">{{ scope.row.productCode || '—' }}</template></el-table-column>
            <el-table-column prop="inspectionQuantity" label="检验数量" min-width="105" align="right" />
            <el-table-column prop="goodQuantity" label="良品" min-width="90" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodQuantity }}</span></template></el-table-column>
            <el-table-column prop="defectiveQuantity" label="不良" min-width="90" align="right"><template #default="scope"><span :class="{ 'bad-text': scope.row.defectiveQuantity > 0 }">{{ scope.row.defectiveQuantity }}</span></template></el-table-column>
            <el-table-column label="结果" min-width="90"><template #default="scope"><el-tag :type="scope.row.verdict === 'PASS' ? 'success' : 'danger'">{{ scope.row.verdict }}</el-tag></template></el-table-column>
            <el-table-column prop="defectSummary" label="缺陷说明" min-width="180"><template #default="scope">{{ scope.row.defectSummary || '—' }}</template></el-table-column>
            <el-table-column prop="inspectedBy" label="检验员" min-width="110"><template #default="scope">{{ scope.row.inspectedBy || '—' }}</template></el-table-column>
          </paged-table>
        </div>
        <div v-if="finishedRecordsNextCursor" class="task-queue-toolbar"><span>还有更早的 FQC 检验记录</span><el-button :loading="finishedRecordsLoading" @click="loadCompletedFinishedRecords(finishedRecordsNextCursor)">加载更多</el-button></div>
      </template>
    </el-card>

    <el-card v-if="activePanel === 'summary'" shadow="never" class="content-card summary-table-card">
      <template #header>
        <div class="card-header">
          <div><span>成品测试汇总表</span><small>已提交的成品检验记录按日期汇总；若当前权限内没有成品测试记录，将显示为空。</small></div>
          <span class="live-status"><i></i>更新于 {{ updatedAtLabel }}</span>
        </div>
      </template>
      <el-alert v-if="summaryError" type="error" :closable="false" :title="summaryError" style="margin-bottom: 12px" />
      <paged-table v-else :data="qualitySummary.dailyTrend" empty-text="暂无已提交的质量记录">
        <el-table-column prop="inspectionDate" label="检测日期" min-width="140" />
        <el-table-column prop="totalRecords" label="检测总数" min-width="130" align="right" />
        <el-table-column prop="passRecords" label="合格" min-width="110" align="right"><template #default="scope"><span class="good-text">{{ scope.row.passRecords }}</span></template></el-table-column>
        <el-table-column prop="failRecords" label="不合格" min-width="110" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.failRecords }}</span></template></el-table-column>
        <el-table-column label="一次合格率" min-width="190"><template #default="scope"><el-progress :percentage="scope.row.passRate" :stroke-width="9" :color="scope.row.passRate >= 98.5 ? '#12b76a' : '#f79009'" /></template></el-table-column>
      </paged-table>
      <div v-if="!summaryError" class="summary-footnote">当前成品检验累计：{{ fqcTotal }} 条，合格 {{ fqcPass }} 条，不合格 {{ fqcFail }} 条。</div>
    </el-card>

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
