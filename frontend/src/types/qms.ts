export type ApiResponse<T> = { data: T }

export type Batch = {
  id: string
  batchNo: string
  plannedQuantity: number
  status: string
}

export type WorkOrder = {
  id: string
  workOrderNo: string
  productCode: string
  productName?: string
  specification?: string
  currentProcessCode: string
  currentProcessName: string
  plannedQuantity: number
  completedQuantity: number
  priority?: string
  processRouteVersion?: string
  miVersion?: string
  qualityStatus?: string
  sourceSystem: string
  sourceKey?: string
  sourceVersion?: string
  status: string
  customerCode?: string
  internalProductNo?: string
  customerPartNo?: string
  processTechnology?: string
  material?: string
  boardThickness?: string
  copperThickness?: string
  warehouseStatus?: string
  releasedAt?: string
  materialInputDate?: string
  pcsLength?: number
  pcsWidth?: number
  materialNo?: string
  materialName?: string
  materialSpecification?: string
  standardMaterialConsumption?: number
  materialInputQuantity?: number
  cumulativeWarehouseQuantity?: number
  cumulativeScrapQuantity?: number
  cumulativeDefectQuantity?: number
  requiredDeliveryDate?: string
  productionNoticeNo?: string
  inputDate?: string
  singlePcsArea?: number
  pnlLength?: number
  pnlWidth?: number
  aBoardLength?: number
  aBoardWidth?: number
  bBoardLength?: number
  bBoardWidth?: number
  formingMethod?: string
  layerCount?: number
  productionNoticeQuantity?: number
  productionNoticeOrderQuantity?: number
  customerOrder?: string
  customerOrderNo?: string
  customerOrderQuantity?: number
  materialArea?: number
  productionNoticeArea?: number
  customerOrderArea?: number
  unreceivedQuantity?: number
  lastWarehouseDate?: string
  totalPanelQuantity?: number
  aBoardPanelQuantity?: number
  bBoardPanelQuantity?: number
  panelizationNo?: string
  estimatedYieldRate?: number
  plannedMaterialIssueQuantity?: number
  updatedAt: string
  batches: Batch[]
}

export type WorkOrderQuery = {
  keyword?: string
  batchNo?: string
  customerCode?: string
  currentProcess?: string
  status?: string
  qualityStatus?: string
  warehouseStatus?: string
  priority?: string
  inputDateFrom?: string
  inputDateTo?: string
  requiredDeliveryDateFrom?: string
  requiredDeliveryDateTo?: string
  page?: number
  pageSize?: number
}

export type WorkOrderPage = {
  records: WorkOrder[]
  total: number
  page: number
  pageSize: number
}

export type InspectionTask = {
  id: string
  taskNo: string
  workOrderNo: string
  batchNo?: string
  processCode: string
  processName: string
  taskType: string
  templateId?: string
  sourceType: string
  status: string
  assigneeName?: string
  dueAt?: string
}

export type ProcessInspectionRecord = {
  id: string
  taskNo: string
  processCode: string
  processName: string
  workOrderNo: string
  batchNo?: string
  internalProductNo?: string
  pcbPartNo?: string
  inspectionQuantity: number
  goodQuantity: number
  defectiveQuantity: number
  templateName: string
  templateVersionNo?: number
  inspectedBy?: string
  verdict: 'PASS' | 'FAIL'
  followUpStatus: string
  ncrNo?: string
  defectSummary?: string
  defectLocation?: string
  results: Record<string, unknown>
  attachmentUrls: string[]
  inspectedAt?: string
}

export type FinishedTestInspectionRecord = {
  id: string
  taskNo: string
  inspectedAt?: string
  equipmentAssetNo?: string
  workOrderNo: string
  batchNo?: string
  internalProductNo?: string
  productCode?: string
  inspectionQuantity: number
  goodQuantity: number
  defectiveQuantity: number
  verdict: 'PASS' | 'FAIL'
  defectSummary?: string
  defectLocation?: string
  inspectedBy?: string
  results: Record<string, unknown>
}

export type InspectionTemplateItem = {
  id?: string
  sortOrder: number
  itemCode: string
  itemName: string
  inputType: 'TEXT' | 'NUMBER' | 'SELECT' | 'BOOLEAN'
  required: boolean
  lowerLimit?: number
  upperLimit?: number
  unit?: string
  options: string[]
  helpText?: string
}

export type InspectionTemplate = {
  id: string
  templateCode: string
  name: string
  productCode?: string
  processCode: string
  processName: string
  taskType: string
  versionNo: number
  status: 'ACTIVE' | 'INACTIVE'
  items: InspectionTemplateItem[]
}

export type InspectionRule = {
  id: string
  name: string
  productCode?: string
  processCode: string
  processName: string
  taskType: string
  templateId?: string
  enabled: boolean
  priority: number
}

export type ProductMaster = {
  id: string
  productCode: string
  productName: string
  specification?: string
  status: 'ACTIVE' | 'INACTIVE'
}

export type ProcessMaster = {
  id: string
  processCode: string
  processName: string
  sequenceNo: number
  status: 'ACTIVE' | 'INACTIVE'
}

export type UserAccess = {
  id: string
  userName: string
  displayName?: string
  roleCode: 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'SUPERVISOR' | 'INSPECTOR'
  status: 'ACTIVE' | 'INACTIVE'
  processCodes: string[]
}

export type PasswordLogin = {
  accessToken: string
  expiresIn: number
  tenantCode: string
  userName: string
  displayName: string
  roleCode: 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'SUPERVISOR' | 'INSPECTOR'
}

export type LoginTenant = {
  code: string
  name: string
}

export type TenantManagement = {
  id: string
  code: string
  name: string
  status: 'ACTIVE' | 'INACTIVE'
  adminUserName?: string
  adminDisplayName?: string
  loginPinned: boolean
  activeMenuCodes: string[]
  createdAt: string
}

export type DashboardSummary = {
  activeWorkOrders: number
  pendingInspectionTasks: number
  completedInspectionTasks: number
  openNonconformities: number
  firstPassRate: number
}

export type ScanLookup = {
  workOrder: WorkOrder
  availableInspectionTaskNos: string[]
}

export type Nonconformity = {
  id: string
  ncrNo: string
  workOrderNo: string
  batchNo?: string
  status: string
  initiatedBy: string
  createdAt: string
  detectionDate?: string
  shiftName?: string
  processCode?: string
  processName?: string
  responsibleDepartment?: string
  remark?: string
  dispositionMethod?: string
  details: NonconformityDetail[]
}

export type NonconformityDetail = {
  id: string
  lineNo: number
  workOrderNo: string
  internalProductNo?: string
  processName: string
  unit: string
  singlePcsArea?: number
  scrapQuantity: number
  scrapArea?: number
  scrapReason?: string
  remark?: string
  status: string
}

export type ManualNonconformityInput = {
  detectionDate: string
  shiftName?: string
  processCode: string
  processName: string
  responsibleDepartment?: string
  remark?: string
  dispositionMethod: string
  batchNo?: string
  details: Array<{
    workOrderNo: string
    unit: string
    scrapQuantity: number
    scrapReason?: string
    remark?: string
  }>
}

export type Capa = {
  id: string
  capaNo: string
  ncrNo: string
  status: string
  problemDescription: string
  teamMembers?: string
  containmentAction?: string
  rootCause?: string
  correctiveAction?: string
  preventiveAction?: string
  verificationResult?: string
  recognitionNotes?: string
  initiatedBy: string
  approvedBy?: string
  createdAt: string
  closedAt?: string
}

export type Equipment = {
  id: string
  assetNo: string
  name: string
  equipmentType: string
  manufacturer?: string
  model?: string
  processCode?: string
  processName?: string
  status: string
  responsibleName?: string
  calibrationDueAt?: string
  calibrationStatus: 'NORMAL' | 'DUE_SOON' | 'OVERDUE' | 'NOT_SET'
}

export type EquipmentQuery = {
  keyword?: string
  equipmentType?: string
  status?: string
  page?: number
  pageSize?: number
}

export type EquipmentPage = {
  records: Equipment[]
  total: number
  page: number
  pageSize: number
}

export type EquipmentGatewayMapping = {
  id: string
  equipmentId?: string
  equipmentCode?: string
  equipmentName?: string
  username?: string
  machineName?: string
  updatedAt: string
}

export type GatewayHostStatus = {
  hostName: string
  equipmentId?: string
  equipmentCode?: string
  equipmentName?: string
  sampledAt: string
  receivedAt: string
  cpuPercent?: number | null
  memoryTotalBytes: number
  memoryAvailableBytes: number
  systemDiskTotalBytes: number
  systemDiskFreeBytes: number
  uptimeSeconds: number
}

export type EquipmentGatewayUnmatchedMessage = {
  id: string
  sourceUsername?: string
  machineName?: string
  lastDeviceTime?: string
  lastReceivedAt: string
  occurrenceCount: number
  rawPayload: string
}

export type EquipmentRuntimeStatus = {
  equipmentId: string
  equipmentCode: string
  equipmentName: string
  processCode?: string
  reportedStatus?: string
  effectiveStatus: string
  online: boolean
  programNo?: string
  operatorName?: string
  progressPercent?: number
  spindleStatus?: string
  alarmCode?: string
  alarmMessage?: string
  machiningCount?: number
  lastDeviceTime?: string
  lastReceivedAt?: string
}

export type EquipmentTelemetryRecord = {
  id: string
  equipmentId: string
  equipmentCode: string
  equipmentName: string
  sourceUsername?: string
  machineName?: string
  deviceTime: string
  receivedAt: string
  eventType?: string
  status?: string
  programNo?: string
  materialNo?: string
  workOrderNo?: string
  batchNo?: string
  operatorName?: string
  progressPercent?: number
  spindleStatus?: string
  alarmCode?: string
  alarmMessage?: string
  machiningCount?: number
  goodBoardCount?: number
  badBoardCount?: number
  legacyState?: string
  legacyId?: string
  legacyMachineType?: string
  runtimeSeconds?: number
  routeLength?: number
  screenText?: string
  sourceRecordNo?: string
  rawPayload?: string
}

export type EquipmentTelemetryHistoryQuery = {
  equipmentId?: string
  eventType?: string
  fromTime?: string
  toTime?: string
  cursor?: string
  limit?: number
}

export type EquipmentTelemetryHistoryPage = {
  items: EquipmentTelemetryRecord[]
  nextCursor?: string
  pageSize: number
}

export type EquipmentMaintenance = {
  id: string
  recordNo: string
  equipmentAssetNo: string
  equipmentName: string
  maintenanceType: string
  scheduledAt?: string
  completedAt?: string
  status: string
  description: string
  result?: string
  executorName?: string
  verifierName?: string
  scheduleStatus: 'PENDING' | 'OVERDUE' | 'COMPLETED' | 'ALERT'
}

export type EquipmentType = {
  code: string
  name: string
}

export type EquipmentMaintenanceTemplateItem = {
  id: string
  equipmentType: string
  maintenanceType: 'INSPECTION' | 'MAINTENANCE' | 'REPAIR' | 'CALIBRATION'
  itemCode: string
  itemName: string
  lowerLimit?: number
  upperLimit?: number
  unit?: string
}

export type EquipmentMaintenanceItem = {
  id: string
  itemCode: string
  itemName: string
  lowerLimit?: number
  upperLimit?: number
  unit?: string
  measuredValue?: number
  judgement: 'PENDING' | 'PASS' | 'FAIL'
}

export type EquipmentAlert = {
  id: string
  alertNo: string
  equipmentAssetNo: string
  equipmentName: string
  title: string
  description: string
  status: 'OPEN' | 'RESOLVED'
  createdAt: string
  resolvedAt?: string
}

export type ProcessQualityMetric = {
  processCode: string
  processName: string
  totalRecords: number
  passRecords: number
  failRecords: number
  passRate: number
}

export type QualitySummary = {
  totalRecords: number
  passRecords: number
  failRecords: number
  firstPassRate: number
  byProcess: ProcessQualityMetric[]
  defectRanking: DefectRankingMetric[]
  dailyTrend: QualityTrendMetric[]
}

export type DefectRankingMetric = {
  defectSummary: string
  failedRecords: number
  defectiveQuantity: number
}

export type QualityTrendMetric = {
  inspectionDate: string
  totalRecords: number
  passRecords: number
  failRecords: number
  passRate: number
}

export type Traceability = {
  workOrder: WorkOrder
  batchNo: string
  inspectionRecords: Array<{
    taskNo: string
    processName: string
    taskType: string
    verdict: string
    defectiveQuantity: number
    defectSummary?: string
    defectLocation?: string
    inspectedBy: string
    inspectedAt: string
  }>
  nonconformities: Array<{
    ncrNo: string
    status: string
    initiatedBy: string
    createdAt: string
  }>
}

export type AdministrationCatalogType =
  | 'ROLE'
  | 'MENU'
  | 'ORGANIZATION'
  | 'DEPARTMENT'
  | 'SCRAP_REASON'
  | 'DICTIONARY'
  | 'INTERFACE'
  | 'APPROVAL_FLOW'
  | 'EQUIPMENT_TYPE'
  | 'EQUIPMENT_TEMPLATE'

export type AdministrationItem = {
  id: string
  catalogType: AdministrationCatalogType
  itemCode: string
  itemName: string
  parentCode?: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  sortOrder: number
  attributes: Record<string, string>
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export type AdministrationAuditLog = {
  id: string
  moduleCode: string
  operation: string
  targetCode: string
  summary: string
  actor: string
  createdAt: string
}

export type MenuAccess = {
  roleCode: string
  activeMenuCodes: string[]
  permittedMenuCodes: string[]
}
