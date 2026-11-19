<script setup lang="ts">
import { isAxiosError } from 'axios'
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { loadMenuAccess, menuAccess } from '@/navigation/menu'
import { equipmentStatusTagType, formatDate, formatDateTime } from '@/utils/display-text'
import { clampPage, paginateRows } from '@/utils/pagination'
import { createClientRequestId, listInspectionDrafts, removeInspectionDraft, saveInspectionDraft, type OfflineInspectionDraft, type OfflineInspectionPayload } from '@/offline/inspectionDraftStore'
import { deriveInspectionMonitorStatus, monitorStatusLabel, sortInspectionMonitorDevices } from './inspectionMonitorStatus'
import { flyingProbePassRate, flyingProbePassRateFromCounts, useFlyingProbeHistory } from './useFlyingProbeHistory'
import type { Equipment, EquipmentTelemetryRecord, FlyingProbeRetryableProjection, GatewayHostStatus, FinishedTestInspectionRecord, InspectionTask, InspectionTemplate, QualitySummary, WorkOrder } from '@/types/qms'

const FinishedTestDeviceSummary = defineAsyncComponent(() => import('@/components/FinishedTestDeviceSummary.vue'))

const networkOnline = ref(navigator.onLine)
const backendReachable = ref(true)
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
const summaryLoading = ref(false)
const equipment = ref<Equipment[]>([])
const hostStatuses = ref<GatewayHostStatus[]>([])
const hostOfflineAfterSeconds = ref(300)
const finishedTestRecords = ref<FinishedTestInspectionRecord[]>([])
const finishedRecordPage = ref(1)
const finishedRecordPageSize = ref(10)
const finishedRecordTotal = ref(0)
const finishedRecordsLoading = ref(false)
const finishedRecordsUpdatedAt = ref<Date>()
const probeSnapshots = ref<Record<string, EquipmentTelemetryRecord>>({})
const probeFailedEquipmentIds = ref<Set<string>>(new Set())
const summaryError = ref('')
const equipmentError = ref('')
const hostStatusError = ref('')
const probeSnapshotsError = ref('')
const finishedRecordsError = ref('')
const recordSource = ref<'probe' | 'retryable' | 'fqc'>('probe')
const canBindFlyingProbe = computed(() => ['PLATFORM_ADMIN', 'TENANT_ADMIN', 'SUPERVISOR'].includes(menuAccess.value?.roleCode ?? ''))
const retryableRecords = ref<FlyingProbeRetryableProjection[]>([])
const retryableTotal = ref(0)
const retryablePage = ref(1)
const retryablePageSize = ref(10)
const retryableLoading = ref(false)
const retryableLoaded = ref(false)
const retryableError = ref('')
const retryableUpdatedAt = ref<Date>()
const probeWorkOrderNo = ref('')
const probeEquipmentId = ref('')
const probeCustomerOrderNo = ref('')
const probeInternalProductNo = ref('')
const appliedProbeSearch = ref<{ equipmentId?: string; workOrderNo?: string; customerOrderNo?: string; internalProductNo?: string }>({})
const appliedFinishedSearch = ref<{ equipmentAssetNo?: string; workOrderNo?: string; customerOrderNo?: string; internalProductNo?: string }>({})
const bindingDialog = ref(false)
const bindingRecord = ref<FlyingProbeRetryableProjection>()
const bindingKeyword = ref('')
const bindingWorkOrders = ref<WorkOrder[]>([])
const bindingWorkOrderNo = ref('')
const bindingSearchLoading = ref(false)
const bindingSaving = ref(false)
const bindingError = ref('')
let bindingSearchVersion = 0
const {
  records: flyingProbeRecords,
  page: flyingProbePage,
  pageSize: flyingProbePageSize,
  total: flyingProbeTotal,
  loading: flyingProbeLoading,
  loaded: flyingProbeLoaded,
  error: flyingProbeError,
  updatedAt: flyingProbeUpdatedAt,
  refresh: refreshFlyingProbeRecords,
  changePage: changeFlyingProbePage
} = useFlyingProbeHistory((query) => qmsApi.listEquipmentTelemetryHistory(query))
const recordPanelUpdatedAt = computed(() => recordSource.value === 'probe' ? flyingProbeUpdatedAt.value
  : recordSource.value === 'retryable' ? retryableUpdatedAt.value : finishedRecordsUpdatedAt.value)
const recordPanelLoading = computed(() => recordSource.value === 'probe' ? flyingProbeLoading.value
  : recordSource.value === 'retryable' ? retryableLoading.value : finishedRecordsLoading.value)
watch([isSummaryRoute, () => route.query.panel], ([summaryRoute, panel], [wasSummaryRoute]) => {
  activePanel.value = summaryRoute ? 'summary' : panel === 'records' ? 'records' : 'monitor'
  if (wasSummaryRoute && !summaryRoute) void refreshWorkbench()
  if (activePanel.value === 'records' && !flyingProbeLoaded.value) void refreshFlyingProbeRecords()
})
const qualitySummary = ref<QualitySummary>({ totalRecords: 0, passRecords: 0, failRecords: 0, firstPassRate: 0, byProcess: [], defectRanking: [], dailyTrend: [] })
const selectedDeviceId = ref('')
const recordEquipmentAssetNo = ref('')
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
const pagedMonitorDevices = computed(() => paginateRows(
  sortInspectionMonitorDevices(monitorDevices.value, deviceMonitorStatus), monitorPage.value, monitorPageSize.value))
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
    ? probe.workOrderNo || '未关联'
    : '未关联'
})
const fqcTotal = computed(() => qualitySummary.value.totalRecords)
const fqcPass = computed(() => qualitySummary.value.passRecords)
const fqcFail = computed(() => qualitySummary.value.failRecords)
const fqcPassRate = computed(() => qualitySummary.value.firstPassRate)
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

async function loadCompletedFinishedRecords(page = 1, pageSize = finishedRecordPageSize.value, includeTotal = true) {
  if (finishedRecordsLoading.value) return false
  finishedRecordsLoading.value = true
  finishedRecordsError.value = ''
  try {
    const result = await qmsApi.pageCompletedFinishedTestRecords(page, pageSize, includeTotal, appliedFinishedSearch.value)
    finishedTestRecords.value = result.items
    if (includeTotal) finishedRecordTotal.value = result.total ?? 0
    finishedRecordPage.value = page
    finishedRecordPageSize.value = pageSize
    if (includeTotal) finishedRecordsUpdatedAt.value = new Date()
    return true
  } catch {
    finishedRecordsError.value = '已提交检验记录读取失败，请重试。'
    return false
  } finally {
    finishedRecordsLoading.value = false
  }
}

function changeFinishedRecordPage(page: number, size: number) {
  void loadCompletedFinishedRecords(page, size, false)
}

async function loadRetryableProjections(page = retryablePage.value, pageSize = retryablePageSize.value) {
  if (retryableLoading.value) return
  retryableLoading.value = true
  retryableError.value = ''
  try {
    const result = await qmsApi.listRetryableFlyingProbeProjections(page, pageSize, appliedProbeSearch.value)
    retryableRecords.value = result.records
    retryableTotal.value = result.total
    retryablePage.value = result.page
    retryablePageSize.value = result.pageSize
    retryableLoaded.value = true
    retryableUpdatedAt.value = new Date()
  } catch (error) {
    retryableError.value = error instanceof Error ? error.message : '待匹配记录读取失败，请重试。'
  } finally {
    retryableLoading.value = false
  }
}

let monitoringVersion = 0
let hostStatusPollInFlight = false
let hostStatusPollTimer: number | undefined
let componentMounted = false

async function pollHostStatuses() {
  if (activePanel.value === 'summary' || monitoringLoading.value || hostStatusPollInFlight) return
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
  summaryLoading.value = true
  summaryError.value = ''
  monitoringVersion++
  const summaryResult = qmsApi.getQualitySummary('2026-10-20', undefined, undefined, true)
    .then((summary) => { qualitySummary.value = summary })
    .catch(() => { summaryError.value = 'FQC 质量汇总读取失败，请重试。' })
  let fastRequestsSucceeded = false
  try {
    const [equipmentResult, hostStatusResult, snapshotsResult, offlineThresholdResult] = await Promise.allSettled([
      qmsApi.listEquipment(),
      qmsApi.listGatewayHostStatuses(),
      qmsApi.listLatestFlyingProbeSnapshots(),
      qmsApi.getTenantParameter('equipment.monitor.offline-after-seconds')
    ])
    backendReachable.value = [equipmentResult, hostStatusResult, snapshotsResult]
      .some((result) => result.status === 'fulfilled')
    equipmentError.value = equipmentResult.status === 'rejected' ? '成品测试设备台账读取失败，请重试。' : ''
    hostStatusError.value = hostStatusResult.status === 'rejected' ? '设备电脑心跳读取失败，请重试。' : ''
    if (equipmentResult.status === 'fulfilled') equipment.value = equipmentResult.value
    else equipment.value = []
    if (hostStatusResult.status === 'fulfilled') hostStatuses.value = hostStatusResult.value
    else hostStatuses.value = []
    if (offlineThresholdResult.status === 'fulfilled') {
      const configured = Number.parseInt(offlineThresholdResult.value.value, 10)
      if (Number.isFinite(configured)) hostOfflineAfterSeconds.value = Math.min(86_400, Math.max(30, configured))
    }
    probeSnapshots.value = snapshotsResult.status === 'fulfilled'
      ? Object.fromEntries(snapshotsResult.value.map((record) => [record.equipmentId, record])) : {}
    probeFailedEquipmentIds.value = snapshotsResult.status === 'rejected'
      ? new Set(equipment.value.map((device) => device.id)) : new Set()
    probeSnapshotsError.value = snapshotsResult.status === 'rejected' ? '飞针计数快照读取失败，请刷新重试。' : ''
    fastRequestsSucceeded = [equipmentResult, hostStatusResult, snapshotsResult]
      .every((result) => result.status === 'fulfilled')
  } finally {
    monitoringLoading.value = false
  }
  await summaryResult
  summaryLoading.value = false
  if (!summaryError.value) backendReachable.value = true
  if (fastRequestsSucceeded && !summaryError.value) updatedAt.value = new Date()
}

async function refreshWorkbench() {
  if (monitoringLoading.value || summaryLoading.value) return
  await Promise.all([loadMonitoringData(), loadOfflineDrafts()])
  if (!monitorDevices.value.some((device) => device.assetNo === selectedDeviceId.value)) {
    selectedDeviceId.value = monitorDevices.value[0]?.assetNo ?? ''
  }
}

function showPanel(panel: 'monitor' | 'records') {
  activePanel.value = panel
  if (panel === 'records') {
    recordSource.value = 'probe'
    if (!flyingProbeLoaded.value) void refreshFlyingProbeRecords()
  }
}

function showRecordSource(source: 'probe' | 'retryable' | 'fqc') {
  if (recordPanelLoading.value) return
  recordSource.value = source
  if (source === 'probe') void refreshFlyingProbeRecords(appliedProbeSearch.value)
  if (source === 'retryable') void loadRetryableProjections(1)
  if (source === 'fqc') void loadCompletedFinishedRecords()
}

function searchProbeRecords() {
  if (recordPanelLoading.value) return
  const equipmentId = probeEquipmentId.value
  const workOrderNo = probeWorkOrderNo.value.trim()
  const customerOrderNo = probeCustomerOrderNo.value.trim()
  const internalProductNo = probeInternalProductNo.value.trim()
  appliedProbeSearch.value = {
    ...(equipmentId ? { equipmentId } : {}),
    ...(workOrderNo ? { workOrderNo } : {}),
    ...(customerOrderNo ? { customerOrderNo } : {}),
    ...(internalProductNo ? { internalProductNo } : {})
  }
  appliedFinishedSearch.value = {
    ...(equipmentId ? { equipmentAssetNo: equipment.value.find((device) => device.id === equipmentId)?.assetNo } : {}),
    ...(workOrderNo ? { workOrderNo } : {}),
    ...(customerOrderNo ? { customerOrderNo } : {}),
    ...(internalProductNo ? { internalProductNo } : {})
  }
  if (recordSource.value === 'retryable') void loadRetryableProjections(1)
  else if (recordSource.value === 'fqc') void loadCompletedFinishedRecords(1)
  else void refreshFlyingProbeRecords(appliedProbeSearch.value)
}

async function refreshRecordPanel() {
  if (recordSource.value === 'probe') await refreshFlyingProbeRecords()
  else if (recordSource.value === 'retryable') await loadRetryableProjections()
  else await loadCompletedFinishedRecords()
}

function deviceMonitorStatus(device: MonitorDevice) {
  if (hostStatusError.value) return '状态不可用'
  return deriveInspectionMonitorStatus(
    device.hostStatus?.receivedAt,
    device.probe?.receivedAt,
    Date.now(),
    hostOfflineAfterSeconds.value
  )
}

function deviceStatusLabel(device: MonitorDevice) {
  return monitorStatusLabel(deviceMonitorStatus(device), device.status)
}

function deviceStatusTag(device: MonitorDevice): 'success' | 'warning' | 'info' | 'danger' {
  if (hostStatusError.value) return 'info'
  const status = deviceMonitorStatus(device)
  if (status === '未接入') return equipmentStatusTagType(device.status)
  if (status === '测试中') return 'success'
  return status === '空闲' ? 'warning' : 'info'
}

function deviceComputerStatusLabel(device: MonitorDevice) {
  if (hostStatusError.value) return '读取失败'
  const status = deviceMonitorStatus(device)
  if (status === '测试中' || status === '空闲') return '在线'
  return status === '未接入' ? '尚未上报' : status
}

function isDeviceComputerOnline(device: MonitorDevice) {
  const status = deviceMonitorStatus(device)
  return status === '测试中' || status === '空闲'
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

function canManageProbeRecord(record: EquipmentTelemetryRecord) {
  return !record.workOrderNo || ['RETRYABLE', 'PROCESSED', 'SKIPPED'].includes(record.fqcProjectionStatus || '')
}

function openBindingDialog(record: FlyingProbeRetryableProjection | EquipmentTelemetryRecord) {
  bindingSearchVersion++
  bindingRecord.value = {
    id: record.id, equipmentCode: record.equipmentCode, machineName: record.machineName,
    materialNo: record.materialNo, deviceTime: record.deviceTime, workOrderNo: record.workOrderNo,
    sourceFile: 'sourceFile' in record ? record.sourceFile : undefined
  }
  bindingKeyword.value = record.workOrderNo || record.materialNo || ''
  bindingWorkOrders.value = []
  bindingWorkOrderNo.value = ''
  bindingError.value = ''
  bindingSearchLoading.value = false
  bindingDialog.value = true
  if (bindingKeyword.value) void searchBindingWorkOrders()
}

async function submitBinding() {
  const record = bindingRecord.value
  const selected = bindingWorkOrders.value.find((order) => order.workOrderNo === bindingWorkOrderNo.value)
  if (!record || !selected || bindingSaving.value) return
  const correcting = Boolean(record.workOrderNo && record.workOrderNo !== selected.workOrderNo)
  try {
    await ElMessageBox.confirm(
      `${correcting ? `将同一飞针文件从生产指令 ${record.workOrderNo} 更正到` : record.workOrderNo ? '按原生产指令重新处理：' : '将这条飞针记录绑定到'}生产指令 ${selected.workOrderNo}（厂内编号 ${selected.internalProductNo || '—'}，下达日期 ${formatDate(selected.releasedAt)}）。${correcting ? '同文件的历史 FQC 和设备记录将同步更正；已有处置单或结算数据时会拒绝更正。' : '待匹配快照会尝试生成 FQC。'}确定继续吗？`,
      correcting ? '确认更正关联' : '确认手动绑定',
      { confirmButtonText: correcting ? '确认更正' : '确认绑定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  bindingSaving.value = true
  try {
    await qmsApi.bindFlyingProbeWorkOrder(record.id, selected.workOrderNo)
    bindingDialog.value = false
    ElMessage.success(correcting ? '关联及历史 FQC 已更正' : '已提交绑定，FQC 正在处理')
    await Promise.all([
      refreshFlyingProbeRecords(),
      loadCompletedFinishedRecords(),
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

function handleKeyboardShortcut(event: KeyboardEvent) {
  if (event.ctrlKey && event.key === 'Enter' && recordDialog.value && !saving.value) {
    event.preventDefault()
    void submitRecord()
  }
}

async function openRecord(task: InspectionTask) {
  selectedTask.value = task
  recordEquipmentAssetNo.value = ''
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
  if (activePanel.value === 'summary') return
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
      </div>
      <div class="finished-test-hero-actions">
        <span class="workstation-connection" :class="workstationConnectionClass"><i></i>{{ workstationConnectionLabel }}</span>
        <span class="live-status"><i></i>数据更新 {{ updatedAtLabel }}</span>
        <el-button plain :loading="monitoringLoading || summaryLoading" @click="refreshWorkbench">刷新数据</el-button>
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
        <el-alert v-if="hostStatusError" type="error" :closable="false" :title="hostStatusError" style="margin-bottom: 12px" />
        <el-alert v-if="probeSnapshotsError" type="error" :closable="false" :title="probeSnapshotsError" style="margin-bottom: 12px" />
        <el-alert v-if="finishedRecordsError" type="error" :closable="false" :title="finishedRecordsError" style="margin-bottom: 12px" />
        <div class="section-heading">
          <div>
            <h3>成品测试多设备实时监控</h3>
          </div>
          <div v-if="!summaryError" class="shift-summary">
            <span>检测 <strong>{{ summaryLoading && !updatedAt ? '—' : fqcTotal }}</strong></span>
            <span>合格 <strong class="good-text">{{ summaryLoading && !updatedAt ? '—' : fqcPass }}</strong></span>
            <span>不合格 <strong class="bad-text">{{ summaryLoading && !updatedAt ? '—' : fqcFail }}</strong></span>
            <span>一次合格率 <strong class="good-text">{{ summaryLoading && !updatedAt ? '—' : `${fqcPassRate}%` }}</strong></span>
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
              <small>采集电脑{{ device.hostStatus ? ` · ${device.hostStatus.hostName}` : '' }}</small>
              <b>{{ deviceComputerStatusLabel(device) }}</b>
            </span>
            <span class="device-card-footer"><span>飞针已测 {{ device.probeError ? '—' : (device.probe?.machiningCount ?? '—') }}</span><span>检验见记录列表</span></span>
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
            <div><span>最新料号</span><strong>{{ selectedProbeRecord?.materialNo || (selectedProbeRecord ? '未上报料号' : '暂无飞针采集') }}</strong></div>
            <div><span>已测 / 好板 / 坏板</span><strong>{{ selectedProbeRecord?.machiningCount ?? '—' }} / {{ selectedProbeRecord?.goodBoardCount ?? '—' }} / {{ selectedProbeRecord?.badBoardCount ?? '—' }}</strong></div>
            <div><span>飞针设备检测时间</span><strong>{{ formatDateTime(selectedProbeRecord?.deviceTime) }}</strong></div>
            <div><span>关联生产指令</span><strong>{{ selectedAssociation }}</strong></div>
          </template>
        </div>
      </section>

    </template>

    <el-card v-if="activePanel === 'records'" shadow="never" class="content-card finished-records-card">
      <template #header>
        <div class="card-header">
          <div><span>设备检测记录</span></div>
          <div class="task-queue-actions"><span class="live-status"><i></i>{{ recordPanelUpdatedAt ? `更新于 ${formatDateTime(recordPanelUpdatedAt)}` : '尚未读取' }}</span><el-button link type="primary" :disabled="recordPanelLoading" @click="refreshRecordPanel">刷新</el-button></div>
        </div>
      </template>
      <div class="task-queue-actions" role="group" aria-label="记录来源">
        <el-button :type="recordSource !== 'fqc' ? 'primary' : 'default'" @click="showRecordSource('probe')">飞针机采集记录</el-button>
        <el-button :type="recordSource === 'fqc' ? 'primary' : 'default'" @click="showRecordSource('fqc')">已提交检验</el-button>
      </div>
      <div class="probe-record-filters">
        <div v-if="recordSource !== 'fqc'" class="task-queue-actions" role="group" aria-label="匹配状态">
          <el-button :type="recordSource === 'probe' ? 'primary' : 'default'" :disabled="recordPanelLoading" @click="showRecordSource('probe')">全部记录</el-button>
          <el-button v-if="canBindFlyingProbe" :type="recordSource === 'retryable' ? 'primary' : 'default'" :disabled="recordPanelLoading" @click="showRecordSource('retryable')">未匹配</el-button>
        </div>
        <el-select v-model="probeEquipmentId" clearable filterable placeholder="全部检测设备" aria-label="筛选检测设备">
          <el-option v-for="device in monitorDevices" :key="device.id" :label="`${device.assetNo} · ${device.name}`" :value="device.id" />
        </el-select>
        <el-input v-model="probeWorkOrderNo" clearable placeholder="搜索生产指令" aria-label="搜索生产指令" @keyup.enter="searchProbeRecords" />
        <el-input v-model="probeCustomerOrderNo" clearable placeholder="搜索订单编号" aria-label="搜索订单编号" @keyup.enter="searchProbeRecords" />
        <el-input v-model="probeInternalProductNo" clearable placeholder="搜索厂内编号" aria-label="搜索厂内编号" @keyup.enter="searchProbeRecords" />
        <el-button type="primary" :disabled="recordPanelLoading" @click="searchProbeRecords">查询</el-button>
      </div>
      <template v-if="recordSource === 'probe'">
        <el-alert v-if="flyingProbeError" type="error" :closable="false" :title="flyingProbeError" style="margin-top: 12px" />
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="task-queue-toolbar"><span>共 {{ flyingProbeTotal }} 条飞针机采集记录</span></div>
        <div v-if="flyingProbeLoaded || flyingProbeLoading" class="finished-records-table-wrap finished-records-table-wrap--probe">
          <paged-table v-loading="flyingProbeLoading" :data="flyingProbeRecords" :total="flyingProbeTotal" :page="flyingProbePage" :page-size="flyingProbePageSize" size="small" empty-text="暂无飞针机采集记录" @page-change="changeFlyingProbePage">
            <el-table-column label="设备检测时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.deviceTime) }}</template></el-table-column>
            <el-table-column prop="equipmentCode" label="检测设备" min-width="125" show-overflow-tooltip />
            <el-table-column label="生产指令" min-width="145" show-overflow-tooltip><template #default="scope">{{ scope.row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="销售订单号" min-width="150" show-overflow-tooltip><template #default="scope">{{ scope.row.customerOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="厂内编号" min-width="155" show-overflow-tooltip><template #default="scope">{{ scope.row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="已测" min-width="100" align="right"><template #default="scope">{{ scope.row.machiningCount ?? '—' }}</template></el-table-column>
            <el-table-column label="好板" min-width="100" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="坏板" min-width="100" align="right"><template #default="scope"><span class="bad-text">{{ scope.row.badBoardCount ?? '—' }}</span></template></el-table-column>
            <el-table-column label="实时合格率" min-width="120" align="right"><template #default="scope">{{ flyingProbePassRate(scope.row) }}</template></el-table-column>
            <el-table-column v-if="canBindFlyingProbe" label="操作" min-width="110" fixed="right"><template #default="scope"><el-button v-if="canManageProbeRecord(scope.row)" link type="primary" @click="openBindingDialog(scope.row)">{{ scope.row.workOrderNo ? '更换绑定' : '手动绑定' }}</el-button></template></el-table-column>
          </paged-table>
        </div>
      </template>
      <template v-else-if="recordSource === 'retryable' && canBindFlyingProbe">
        <el-alert v-if="retryableError" type="error" :closable="false" :title="retryableError" style="margin-top: 12px" />
        <div v-if="retryableLoaded || retryableLoading" class="task-queue-toolbar"><span>共 {{ retryableTotal }} 份待匹配飞针文件</span></div>
        <div v-if="retryableLoaded || retryableLoading" class="finished-records-table-wrap finished-records-table-wrap--retryable">
          <paged-table v-loading="retryableLoading" :data="retryableRecords" :total="retryableTotal" :page="retryablePage" :page-size="retryablePageSize" size="small" empty-text="暂无待匹配飞针文件" @page-change="loadRetryableProjections">
            <el-table-column label="设备检测时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.deviceTime) }}</template></el-table-column>
            <el-table-column label="检测设备" min-width="125" show-overflow-tooltip><template #default="{ row }">{{ row.equipmentCode || row.machineName || '—' }}</template></el-table-column>
            <el-table-column label="生产指令" min-width="145" show-overflow-tooltip><template #default="{ row }">{{ row.workOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="销售订单号" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.customerOrderNo || '—' }}</template></el-table-column>
            <el-table-column label="厂内编号" min-width="155" show-overflow-tooltip><template #default="{ row }">{{ row.materialNo || '—' }}</template></el-table-column>
            <el-table-column label="已测" min-width="100" align="right"><template #default="{ row }">{{ row.cumulativeTested ?? '—' }}</template></el-table-column>
            <el-table-column label="好板" min-width="100" align="right"><template #default="{ row }"><span class="good-text">{{ row.cumulativeGood ?? '—' }}</span></template></el-table-column>
            <el-table-column label="坏板" min-width="100" align="right"><template #default="{ row }"><span class="bad-text">{{ row.cumulativeBad ?? '—' }}</span></template></el-table-column>
            <el-table-column label="实时合格率" min-width="120" align="right"><template #default="{ row }">{{ flyingProbePassRateFromCounts(row.cumulativeTested, row.cumulativeGood) }}</template></el-table-column>
            <el-table-column label="待匹配原因" min-width="280" show-overflow-tooltip><template #default="{ row }">{{ row.reason || '—' }}</template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openBindingDialog(row)">{{ row.workOrderNo ? '更换绑定' : '手动绑定' }}</el-button></template></el-table-column>
          </paged-table>
        </div>
      </template>
      <template v-else>
        <el-alert v-if="finishedRecordsError" type="error" :closable="false" :title="finishedRecordsError" style="margin-top: 12px" />
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="task-queue-toolbar">
          <span>共 {{ finishedRecordTotal }} 条已提交检验</span>
        </div>
        <div v-if="!finishedRecordsError || finishedTestRecords.length" class="finished-records-table-wrap">
          <paged-table :data="finishedTestRecords" :total="finishedRecordTotal" :page="finishedRecordPage" :page-size="finishedRecordPageSize" size="small" empty-text="暂无已提交的检验记录；飞针数据请查看“飞针机采集记录”" @page-change="changeFinishedRecordPage">
            <el-table-column label="检验时间" min-width="170"><template #default="scope">{{ formatDateTime(scope.row.inspectedAt) }}</template></el-table-column>
            <el-table-column label="检测设备" min-width="130"><template #default="scope"><el-tag :type="scope.row.equipmentAssetNo ? 'success' : 'info'">{{ scope.row.equipmentAssetNo || '未关联设备' }}</el-tag></template></el-table-column>
            <el-table-column prop="workOrderNo" label="生产指令" min-width="145" />
            <el-table-column label="销售订单号" min-width="150" show-overflow-tooltip><template #default="scope">{{ scope.row.customerOrderNo || '—' }}</template></el-table-column>
            <el-table-column prop="internalProductNo" label="厂内编号" min-width="150"><template #default="scope">{{ scope.row.internalProductNo || '—' }}</template></el-table-column>
            <el-table-column prop="productCode" label="料号" min-width="140"><template #default="scope">{{ scope.row.productCode || '—' }}</template></el-table-column>
            <el-table-column prop="inspectionQuantity" label="检验数量" min-width="105" align="right" />
            <el-table-column prop="goodQuantity" label="良品" min-width="90" align="right"><template #default="scope"><span class="good-text">{{ scope.row.goodQuantity }}</span></template></el-table-column>
            <el-table-column prop="defectiveQuantity" label="不良" min-width="90" align="right"><template #default="scope"><span :class="{ 'bad-text': scope.row.defectiveQuantity > 0 }">{{ scope.row.defectiveQuantity }}</span></template></el-table-column>
            <el-table-column label="结果" min-width="90"><template #default="scope"><el-tag :type="scope.row.verdict === 'PASS' ? 'success' : 'danger'">{{ scope.row.verdict }}</el-tag></template></el-table-column>
          </paged-table>
        </div>
      </template>
    </el-card>

    <FinishedTestDeviceSummary v-if="activePanel === 'summary'" />

    <el-dialog v-model="bindingDialog" title="手动绑定飞针工单" width="min(760px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!bindingSaving" :show-close="!bindingSaving">
      <p v-if="bindingRecord">{{ bindingRecord.equipmentCode || bindingRecord.machineName || '未关联设备' }} · 料号 {{ bindingRecord.materialNo || '未上报' }} · 检测时间 {{ formatDateTime(bindingRecord.deviceTime) }}<span v-if="bindingRecord.sourceFile"> · {{ bindingRecord.sourceFile }}</span></p>
      <el-alert type="warning" :closable="false" title="更换绑定会同步同一飞针文件的历史 FQC；已有处置单或结算数据的文件不能更正。" />
      <el-alert v-if="bindingRecord?.workOrderNo" type="info" :closable="false" :title="`该文件当前绑定生产指令 ${bindingRecord.workOrderNo}。`" style="margin-top: 10px" />
      <div class="binding-order-search">
        <el-input v-model="bindingKeyword" clearable placeholder="输入生产指令号或厂内编号" @keyup.enter="searchBindingWorkOrders" />
        <el-button :loading="bindingSearchLoading" @click="searchBindingWorkOrders">查询工单</el-button>
      </div>
      <el-alert v-if="bindingError" type="error" :closable="false" :title="bindingError" />
      <el-table v-loading="bindingSearchLoading" :data="bindingWorkOrders" size="small" max-height="300" empty-text="未找到工单，请输入准确的生产指令号或厂内编号">
        <el-table-column label="选择" width="70"><template #default="{ row }"><el-radio v-model="bindingWorkOrderNo" :value="row.workOrderNo" :aria-label="`选择工单 ${row.workOrderNo}`" /></template></el-table-column>
        <el-table-column prop="workOrderNo" label="生产指令号" min-width="145" />
        <el-table-column label="厂内编号" min-width="180"><template #default="{ row }">{{ row.internalProductNo || '—' }}</template></el-table-column>
        <el-table-column label="下达日期" min-width="130"><template #default="{ row }">{{ formatDate(row.releasedAt) }}</template></el-table-column>
      </el-table>
      <p class="summary-footnote">最多显示前 50 条；如未看到目标工单，请输入更完整的工单号查询。</p>
      <template #footer><el-button :disabled="bindingSaving" @click="bindingDialog = false">取消</el-button><el-button type="primary" :loading="bindingSaving" :disabled="!bindingWorkOrderNo" @click="submitBinding">{{ bindingRecord?.workOrderNo ? (bindingRecord.workOrderNo === bindingWorkOrderNo ? '重试处理' : '更换绑定') : '确认绑定' }}</el-button></template>
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
