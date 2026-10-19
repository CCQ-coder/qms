import { http } from '@/api/http'
import type {
  ApiResponse, Capa, DashboardSummary, Equipment, EquipmentAlert, EquipmentMaintenance, EquipmentMaintenanceItem, EquipmentMaintenanceTemplateItem, FinishedTestInspectionRecord, InspectionTask, ProcessInspectionRecord,
  InspectionRule, InspectionTemplate, Nonconformity, ProcessMaster, ProductMaster, QualitySummary,
  ScanLookup, Traceability, UserAccess, PasswordLogin, LoginTenant, TenantManagement, WorkOrder, WorkOrderPage, WorkOrderQuery, AdministrationAuditLog, AdministrationCatalogType, AdministrationItem, EquipmentType, EquipmentQuery, EquipmentPage, EquipmentGatewayMapping, EquipmentGatewayUnmatchedMessage, EquipmentRuntimeStatus, EquipmentTelemetryRecord, EquipmentTelemetryHistoryPage, EquipmentTelemetryHistoryQuery
  , MenuAccess
} from '@/types/qms'

export const qmsApi = {
  async listLoginTenants() {
    const { data } = await http.get<ApiResponse<LoginTenant[]>>('/api/v1/auth/tenants')
    return data.data
  },

  async loginWithPassword(payload: { tenantCode: string, userName: string, password: string }) {
    const { data } = await http.post<ApiResponse<PasswordLogin>>('/api/v1/auth/login', payload)
    return data.data
  },

  async listTenants() {
    const { data } = await http.get<ApiResponse<TenantManagement[]>>('/api/v1/platform/tenants')
    return data.data
  },

  async createTenant(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<TenantManagement>>('/api/v1/platform/tenants', payload)
    return data.data
  },

  async updateTenant(code: string, payload: Record<string, unknown>) {
    const { data } = await http.put<ApiResponse<TenantManagement>>(`/api/v1/platform/tenants/${encodeURIComponent(code)}`, payload)
    return data.data
  },

  async updateTenantLoginPinned(code: string, loginPinned: boolean) {
    const { data } = await http.put<ApiResponse<TenantManagement>>(
      `/api/v1/platform/tenants/${encodeURIComponent(code)}/login-pinned`, { loginPinned }
    )
    return data.data
  },

  async deleteTenant(code: string) {
    await http.delete(`/api/v1/platform/tenants/${encodeURIComponent(code)}`)
  },

  async getDashboardSummary() {
    const { data } = await http.get<ApiResponse<DashboardSummary>>('/api/v1/dashboard/summary')
    return data.data
  },

  async listWorkOrders(query: WorkOrderQuery = {}) {
    const { data } = await http.get<ApiResponse<WorkOrderPage>>('/api/v1/work-orders/query', { params: query })
    return data.data
  },

  async scan(code: string) {
    const { data } = await http.get<ApiResponse<ScanLookup>>(`/api/v1/work-orders/scan/${encodeURIComponent(code)}`)
    return data.data
  },

  async listInspectionTasks(status?: string) {
    const { data } = await http.get<ApiResponse<InspectionTask[]>>('/api/v1/inspection-tasks', { params: { status } })
    return data.data
  },

  async listCompletedProcessInspectionRecords() {
    const { data } = await http.get<ApiResponse<ProcessInspectionRecord[]>>('/api/v1/inspection-tasks/completed-process-records')
    return data.data
  },

  async listCompletedFinishedTestRecords() {
    const { data } = await http.get<ApiResponse<FinishedTestInspectionRecord[]>>('/api/v1/inspection-tasks/completed-finished-records')
    return data.data
  },

  async createManualInspectionTask(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<InspectionTask>>('/api/v1/inspection-tasks', payload)
    return data.data
  },

  async listInspectionTemplates() {
    const { data } = await http.get<ApiResponse<InspectionTemplate[]>>('/api/v1/inspection-templates')
    return data.data
  },

  async getInspectionTemplate(id: string) {
    const { data } = await http.get<ApiResponse<InspectionTemplate>>(`/api/v1/inspection-templates/${id}`)
    return data.data
  },

  async createInspectionTemplate(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<InspectionTemplate>>('/api/v1/inspection-templates', payload)
    return data.data
  },

  async updateInspectionTemplateStatus(id: string, status: 'ACTIVE' | 'INACTIVE') {
    const { data } = await http.patch<ApiResponse<InspectionTemplate>>(`/api/v1/inspection-templates/${id}/status`, { status })
    return data.data
  },

  async listInspectionRules() {
    const { data } = await http.get<ApiResponse<InspectionRule[]>>('/api/v1/inspection-rules')
    return data.data
  },

  async createInspectionRule(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<InspectionRule>>('/api/v1/inspection-rules', payload)
    return data.data
  },

  async listProducts() {
    const { data } = await http.get<ApiResponse<ProductMaster[]>>('/api/v1/master-data/products')
    return data.data
  },

  async createProduct(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<ProductMaster>>('/api/v1/master-data/products', payload)
    return data.data
  },

  async updateProductStatus(id: string, status: 'ACTIVE' | 'INACTIVE') {
    const { data } = await http.patch<ApiResponse<ProductMaster>>(`/api/v1/master-data/products/${id}/status`, { status })
    return data.data
  },

  async listProcesses() {
    const { data } = await http.get<ApiResponse<ProcessMaster[]>>('/api/v1/master-data/processes')
    return data.data
  },

  async createProcess(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<ProcessMaster>>('/api/v1/master-data/processes', payload)
    return data.data
  },

  async updateProcessStatus(id: string, status: 'ACTIVE' | 'INACTIVE') {
    const { data } = await http.patch<ApiResponse<ProcessMaster>>(`/api/v1/master-data/processes/${id}/status`, { status })
    return data.data
  },

  async listUserAccesses() {
    const { data } = await http.get<ApiResponse<UserAccess[]>>('/api/v1/access/users')
    return data.data
  },

  async createUserAccess(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<UserAccess>>('/api/v1/access/users', payload)
    return data.data
  },

  async updateUserAccess(id: string, payload: Record<string, unknown>) {
    const { data } = await http.put<ApiResponse<UserAccess>>(`/api/v1/access/users/${id}`, payload)
    return data.data
  },

  async listAdministrationItems(catalogType: AdministrationCatalogType) {
    const { data } = await http.get<ApiResponse<AdministrationItem[]>>(`/api/v1/administration/items/${catalogType}`)
    return data.data
  },

  async getMenuAccess() {
    const { data } = await http.get<ApiResponse<MenuAccess>>('/api/v1/administration/menu-access')
    return data.data
  },

  async createAdministrationItem(catalogType: AdministrationCatalogType, payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<AdministrationItem>>(`/api/v1/administration/items/${catalogType}`, payload)
    return data.data
  },

  async updateAdministrationItem(catalogType: AdministrationCatalogType, id: string, payload: Record<string, unknown>) {
    const { data } = await http.put<ApiResponse<AdministrationItem>>(`/api/v1/administration/items/${catalogType}/${id}`, payload)
    return data.data
  },

  async listAdministrationAuditLogs(module?: string) {
    const { data } = await http.get<ApiResponse<AdministrationAuditLog[]>>('/api/v1/administration/audit-logs', { params: { module, limit: 200 } })
    return data.data
  },

  async submitInspectionRecord(taskId: string, payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<{ id: string; ncrNo?: string }>>(`/api/v1/inspection-tasks/${taskId}/records`, payload)
    return data.data
  },

  async createNonconformity(inspectionRecordId: string) {
    const { data } = await http.post<ApiResponse<Nonconformity>>('/api/v1/nonconformities', { inspectionRecordId })
    return data.data
  },

  async uploadInspectionAttachment(image: File) {
    const form = new FormData()
    form.append('image', image)
    const { data } = await http.post<ApiResponse<{ id: string; originalFilename: string; url: string }>>('/api/v1/inspection-attachments', form)
    return data.data
  },

  async listNonconformities(status?: string) {
    const { data } = await http.get<ApiResponse<Nonconformity[]>>('/api/v1/nonconformities', { params: { status } })
    return data.data
  },

  async listCapas(status?: string) {
    const { data } = await http.get<ApiResponse<Capa[]>>('/api/v1/capas', { params: { status } })
    return data.data
  },

  async createCapa(ncrId: string, payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<Capa>>(`/api/v1/nonconformities/${ncrId}/capas`, payload)
    return data.data
  },

  async closeCapa(capaId: string, payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<Capa>>(`/api/v1/capas/${capaId}/close`, payload)
    return data.data
  },

  async listEquipment() {
    const { data } = await http.get<ApiResponse<Equipment[]>>('/api/v1/equipment')
    return data.data
  },

  async queryEquipment(query: EquipmentQuery = {}) {
    const { data } = await http.get<ApiResponse<EquipmentPage>>('/api/v1/equipment/query', { params: query })
    return data.data
  },

  async listEquipmentTypes() {
    const { data } = await http.get<ApiResponse<EquipmentType[]>>('/api/v1/equipment/types')
    return data.data
  },

  async createEquipment(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<Equipment>>('/api/v1/equipment', payload)
    return data.data
  },

  async updateEquipment(id: string, payload: Record<string, unknown>) {
    const { data } = await http.put<ApiResponse<Equipment>>(`/api/v1/equipment/${id}`, payload)
    return data.data
  },

  async deleteEquipment(id: string) {
    await http.delete(`/api/v1/equipment/${id}`)
  },

  async listEquipmentGatewayMappings() {
    const { data } = await http.get<ApiResponse<EquipmentGatewayMapping[]>>('/api/v1/equipment/gateway-mappings')
    return data.data
  },

  async createEquipmentGatewayMapping(payload: { equipmentId: string, machineName: string }) {
    const { data } = await http.post<ApiResponse<EquipmentGatewayMapping>>('/api/v1/equipment/gateway-mappings', payload)
    return data.data
  },

  async updateEquipmentGatewayMapping(id: string, payload: { equipmentId: string, machineName: string, username?: string }) {
    const { data } = await http.put<ApiResponse<EquipmentGatewayMapping>>(`/api/v1/equipment/gateway-mappings/${id}`, payload)
    return data.data
  },

  async deleteEquipmentGatewayMapping(id: string) {
    await http.delete(`/api/v1/equipment/gateway-mappings/${id}`)
  },

  async listEquipmentGatewayUnmatchedMessages() {
    const { data } = await http.get<ApiResponse<EquipmentGatewayUnmatchedMessage[]>>('/api/v1/equipment/gateway-unmatched')
    return data.data
  },

  async listEquipmentRuntimeStatuses() {
    const { data } = await http.get<ApiResponse<EquipmentRuntimeStatus[]>>('/api/v1/equipment/runtime-statuses')
    return data.data
  },

  async listEquipmentTelemetryRecords(limit = 100) {
    const { data } = await http.get<ApiResponse<EquipmentTelemetryRecord[]>>('/api/v1/equipment/telemetry', { params: { limit } })
    return data.data
  },

  async listEquipmentTelemetryHistory(query: EquipmentTelemetryHistoryQuery = {}) {
    const { data } = await http.get<ApiResponse<EquipmentTelemetryHistoryPage>>('/api/v1/equipment/telemetry/history', { params: query })
    return data.data
  },

  async listEquipmentMaintenanceTemplateItems(equipmentType?: string, maintenanceType?: string) {
    const { data } = await http.get<ApiResponse<EquipmentMaintenanceTemplateItem[]>>('/api/v1/equipment/templates', { params: { equipmentType, maintenanceType } })
    return data.data
  },

  async saveEquipmentMaintenanceTemplateItem(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<EquipmentMaintenanceTemplateItem>>('/api/v1/equipment/templates', payload)
    return data.data
  },

  async deleteEquipmentMaintenanceTemplateItem(id: string) {
    await http.delete(`/api/v1/equipment/templates/${id}`)
  },

  async listEquipmentMaintenance(status?: string) {
    const { data } = await http.get<ApiResponse<EquipmentMaintenance[]>>('/api/v1/equipment/maintenance', { params: { status } })
    return data.data
  },

  async createEquipmentMaintenance(payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<EquipmentMaintenance>>('/api/v1/equipment/maintenance', payload)
    return data.data
  },

  async completeEquipmentMaintenance(id: string, payload: Record<string, unknown>) {
    const { data } = await http.post<ApiResponse<EquipmentMaintenance>>(`/api/v1/equipment/maintenance/${id}/complete`, payload)
    return data.data
  },

  async listEquipmentMaintenanceItems(id: string) {
    const { data } = await http.get<ApiResponse<EquipmentMaintenanceItem[]>>(`/api/v1/equipment/maintenance/${id}/items`)
    return data.data
  },

  async listEquipmentAlerts(status?: string) {
    const { data } = await http.get<ApiResponse<EquipmentAlert[]>>('/api/v1/equipment/alerts', { params: { status } })
    return data.data
  },

  async getTenantParameter(key: string) {
    const { data } = await http.get<ApiResponse<{ key: string; value: string }>>('/api/v1/settings/parameters', { params: { key } })
    return data.data
  },

  async updateTenantParameter(key: string, value: string) {
    const { data } = await http.put<ApiResponse<{ key: string; value: string }>>('/api/v1/settings/parameters', { value }, { params: { key } })
    return data.data
  },

  async getQualitySummary(from?: string, to?: string, processCode?: string) {
    const { data } = await http.get<ApiResponse<QualitySummary>>('/api/v1/reports/quality-summary', { params: { from, to, processCode } })
    return data.data
  },

  async downloadQualitySummaryExcel(from?: string, to?: string) {
    const { data } = await http.get('/api/v1/reports/quality-summary.xlsx', { params: { from, to }, responseType: 'blob' })
    return data as Blob
  },

  async getBatchTraceability(batchNo: string) {
    const { data } = await http.get<ApiResponse<Traceability>>(`/api/v1/reports/traceability/batches/${encodeURIComponent(batchNo)}`)
    return data.data
  },

}
