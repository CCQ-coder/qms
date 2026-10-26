<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { qmsApi } from '../api/qms'
import { formatDate, formatDateTime } from '../utils/display-text'
import type { WorkOrder, WorkOrderQuery } from '../types/qms'

const loading = ref(false)
const keyword = ref('')
const batchNo = ref('')
const customerCode = ref('')
const currentProcess = ref('')
const warehouseStatus = ref('')
const priority = ref('')
const inputDateRange = ref<string[]>([])
const deliveryDateRange = ref<string[]>([])
const rows = ref<WorkOrder[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const detailVisible = ref(false)
const selected = ref<WorkOrder | null>(null)
const filtersCollapsed = ref(false)

const pageStart = computed(() => total.value ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))
const latestSyncTime = computed(() => rows.value[0]?.updatedAt ? formatDateTime(rows.value[0].updatedAt) : '暂无数据')
const showBatchColumn = computed(() => rows.value.some(row => row.batches.some(batch => !!batch.batchNo?.trim())))

function value(value: string | number | undefined | null) {
  return value === undefined || value === null || value === '' ? '—' : value
}

function batchText(row: WorkOrder) {
  return row.batches.length ? row.batches.map((batch) => batch.batchNo).join('、') : '未分批'
}

function workOrderStatusText(value?: string) {
  return ({ ACTIVE: '进行中', SUSPENDED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消', INACTIVE: '已停用' } as Record<string, string>)[value ?? ''] ?? value ?? '—'
}

function processLabel(row: WorkOrder) {
  return row.sourceSystem === 'ERP_EXCEL' ? '表面处理' : '当前工序'
}

function qualityStatusText(value?: string) {
  return ({ UNASSESSED: '待质量评估', RELEASED: '已放行', HOLD: '质量冻结', REJECTED: '不放行' } as Record<string, string>)[value ?? ''] ?? value ?? '—'
}

function warehouseStatusText(value?: string) {
  return ({ PENDING: '待入库', RECEIVED: '已入库', CLOSED: '已结批' } as Record<string, string>)[value ?? ''] ?? value ?? '—'
}

function priorityText(value?: string) {
  return ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' } as Record<string, string>)[value ?? ''] ?? value ?? '—'
}

function workOrderTagType(value?: string) {
  return ({ ACTIVE: 'primary', SUSPENDED: 'warning', COMPLETED: 'success', CANCELLED: 'info' } as Record<string, 'primary' | 'warning' | 'success' | 'info'>)[value ?? ''] ?? 'info'
}

function qualityTagType(value?: string) {
  return ({ UNASSESSED: 'warning', RELEASED: 'success', HOLD: 'danger', REJECTED: 'danger' } as Record<string, 'warning' | 'success' | 'danger'>)[value ?? ''] ?? 'warning'
}

function warehouseTagType(value?: string) {
  return ({ PENDING: 'warning', RECEIVED: 'success', CLOSED: 'info' } as Record<string, 'warning' | 'success' | 'info'>)[value ?? ''] ?? 'info'
}

function buildQuery(page = currentPage.value): WorkOrderQuery {
  return {
    keyword: keyword.value.trim() || undefined,
    batchNo: batchNo.value.trim() || undefined,
    customerCode: customerCode.value.trim() || undefined,
    currentProcess: currentProcess.value.trim() || undefined,
    warehouseStatus: warehouseStatus.value || undefined,
    priority: priority.value || undefined,
    inputDateFrom: inputDateRange.value[0],
    inputDateTo: inputDateRange.value[1],
    requiredDeliveryDateFrom: deliveryDateRange.value[0],
    requiredDeliveryDateTo: deliveryDateRange.value[1],
    page,
    pageSize: pageSize.value
  }
}

async function load(page = 1) {
  loading.value = true
  try {
    const result = await qmsApi.listWorkOrders(buildQuery(page))
    rows.value = result.records
    total.value = result.total
    currentPage.value = result.page
    pageSize.value = result.pageSize
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  batchNo.value = ''
  customerCode.value = ''
  currentProcess.value = ''
  warehouseStatus.value = ''
  priority.value = ''
  inputDateRange.value = []
  deliveryDateRange.value = []
  void load(1)
}

function openDetail(row: WorkOrder) {
  selected.value = row
  detailVisible.value = true
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  void load(1)
}

onMounted(() => { void load() })
</script>

<template>
  <section class="work-order-page">
    <el-card class="content-card work-order-filter-card" shadow="never">
      <div class="work-order-filter-heading">
        <div>
          <h2>生产指令查询</h2>
          <p>生产指令由 ERP 同步、QMS 补充质量状态；查询结果仅供查看和追溯，不支持在此页修改来源数据。</p>
        </div>
        <div class="work-order-filter-heading-actions">
          <span class="work-order-sync"><i></i>本页最新同步 {{ latestSyncTime }}</span>
          <el-button class="work-order-filter-toggle" text type="primary" :aria-expanded="!filtersCollapsed" aria-controls="work-order-filters" @click="filtersCollapsed = !filtersCollapsed">
            <el-icon><component :is="filtersCollapsed ? ArrowDown : ArrowUp" /></el-icon>{{ filtersCollapsed ? '展开筛选' : '收起筛选' }}
          </el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="!filtersCollapsed" id="work-order-filters" class="work-order-filters">
          <label class="work-order-filter-field work-order-keyword-field"><span>快速查询</span><el-input v-model="keyword" clearable placeholder="指令号、厂内编号、客户料号、订单号" @keyup.enter="load(1)" /></label>
          <label class="work-order-filter-field"><span>生产批次</span><el-input v-model="batchNo" clearable placeholder="输入批次号" @keyup.enter="load(1)" /></label>
          <label class="work-order-filter-field"><span>客户</span><el-input v-model="customerCode" clearable placeholder="输入客户" @keyup.enter="load(1)" /></label>
          <label class="work-order-filter-field"><span>工序信息</span><el-input v-model="currentProcess" clearable placeholder="工序或表面处理名称" @keyup.enter="load(1)" /></label>
          <label class="work-order-filter-field"><span>入库状态</span><el-select v-model="warehouseStatus" placeholder="全部" clearable><el-option label="待入库" value="PENDING" /><el-option label="已入库" value="RECEIVED" /><el-option label="已结批" value="CLOSED" /></el-select></label>
          <label class="work-order-filter-field"><span>优先级</span><el-select v-model="priority" placeholder="全部" clearable><el-option label="低" value="LOW" /><el-option label="普通" value="NORMAL" /><el-option label="高" value="HIGH" /><el-option label="紧急" value="URGENT" /></el-select></label>
          <label class="work-order-filter-field work-order-date-field"><span>输入日期</span><el-date-picker v-model="inputDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" /></label>
          <label class="work-order-filter-field work-order-date-field"><span>要求交期</span><el-date-picker v-model="deliveryDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" /></label>
          <div class="work-order-filter-actions"><el-button @click="resetFilters">重置</el-button><el-button type="primary" :loading="loading" @click="load(1)">查询</el-button></div>
        </div>
      </el-collapse-transition>
    </el-card>

    <el-card class="content-card work-order-list-card" shadow="never">
      <div class="work-order-list-heading">
        <div><h2>生产指令列表</h2><p>默认显示现场排产、投料面积和入库信息；板材、尺寸与工艺明细请点击生产指令号查看。</p></div>
      </div>

      <div class="work-order-table-wrap work-order-summary-table-wrap" :class="{ 'no-batch-column': !showBatchColumn }">
        <el-table v-loading="loading" class="work-order-data-table" :data="rows" stripe>
          <el-table-column label="生产指令号" min-width="150"><template #default="{ row }"><el-button class="work-order-detail-link" link type="primary" @click="openDetail(row)">{{ row.workOrderNo }}</el-button></template></el-table-column>
          <el-table-column label="输入日期" min-width="145"><template #default="{ row }">{{ formatDate(row.inputDate) }}</template></el-table-column>
          <el-table-column v-if="showBatchColumn" label="生产批次" min-width="145" show-overflow-tooltip><template #default="{ row }">{{ batchText(row) }}</template></el-table-column>
          <el-table-column label="厂内编号 / 客户料号" min-width="180"><template #default="{ row }"><div>{{ value(row.internalProductNo ?? row.productCode) }}</div><small>{{ value(row.customerPartNo) }}</small></template></el-table-column>
          <el-table-column label="客户" min-width="100"><template #default="{ row }">{{ value(row.customerCode) }}</template></el-table-column>
          <el-table-column label="工艺" min-width="135"><template #default="{ row }"><div>{{ value(row.currentProcessName) }}</div><small v-if="row.sourceSystem === 'ERP_EXCEL'">表面处理</small></template></el-table-column>
          <el-table-column label="订单 / 投料 / 入库" min-width="160"><template #default="{ row }">{{ value(row.customerOrderQuantity) }} / {{ value(row.plannedQuantity) }} / {{ value(row.cumulativeWarehouseQuantity) }}</template></el-table-column>
          <el-table-column label="投料面积" min-width="145"><template #default="{ row }"><div>{{ value(row.materialArea) }} m²</div><small>预投率 {{ value(row.estimatedYieldRate) }}% · 计划领料 {{ value(row.plannedMaterialIssueQuantity) }}</small></template></el-table-column>
          <el-table-column label="总 / A / B PNL" min-width="130"><template #default="{ row }"><div>{{ value(row.totalPanelQuantity) }} / {{ value(row.aBoardPanelQuantity) }} / {{ value(row.bBoardPanelQuantity) }}</div><small>{{ value(row.panelizationNo) }}</small></template></el-table-column>
          <el-table-column label="工单状态" min-width="105"><template #default="{ row }"><el-tag :type="workOrderTagType(row.status)" effect="light">{{ workOrderStatusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="入库状态" min-width="105"><template #default="{ row }"><el-tag :type="warehouseTagType(row.warehouseStatus)" effect="plain">{{ warehouseStatusText(row.warehouseStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="优先级" min-width="80"><template #default="{ row }">{{ priorityText(row.priority) }}</template></el-table-column>
          <el-table-column label="投料日期" min-width="110"><template #default="{ row }">{{ formatDate(row.materialInputDate ?? row.releasedAt) }}</template></el-table-column>
        </el-table>
      </div>

      <div class="work-order-pagination">
        <span>显示第 {{ pageStart }}–{{ pageEnd }} 条，共 {{ total }} 条</span>
        <el-pagination :current-page="currentPage" :page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" background layout="sizes, prev, pager, next, jumper" @current-change="load" @size-change="handlePageSizeChange" />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" class="work-order-detail-dialog" :title="selected ? `生产指令详情 · ${selected.workOrderNo}` : '生产指令详情'" width="min(1080px, 94vw)" top="6vh" destroy-on-close>
      <template #header>
        <div v-if="selected" class="work-order-detail-header">
          <div>
            <p>生产指令详情</p>
            <h2>{{ selected.workOrderNo }}</h2>
            <span>生产批次：{{ batchText(selected) }}</span>
          </div>
          <div class="work-order-detail-statuses">
            <el-tag :type="workOrderTagType(selected.status)" effect="light">{{ workOrderStatusText(selected.status) }}</el-tag>
            <el-tag :type="qualityTagType(selected.qualityStatus)" effect="light">{{ qualityStatusText(selected.qualityStatus) }}</el-tag>
            <el-tag :type="warehouseTagType(selected.warehouseStatus)" effect="plain">{{ warehouseStatusText(selected.warehouseStatus) }}</el-tag>
          </div>
        </div>
      </template>

      <div v-if="selected" class="work-order-detail-content">
        <div class="work-order-detail-metrics">
          <div><span>投料数量</span><strong>{{ value(selected.materialInputQuantity ?? selected.plannedQuantity) }}</strong></div>
          <div><span>完工数量</span><strong>{{ value(selected.completedQuantity) }}</strong></div>
          <div><span>累计入库数</span><strong>{{ value(selected.cumulativeWarehouseQuantity) }}</strong></div>
          <div><span>{{ processLabel(selected) }}</span><strong>{{ value(selected.currentProcessName) }}</strong></div>
          <div><span>优先级</span><strong>{{ priorityText(selected.priority) }}</strong></div>
        </div>

        <el-tabs class="work-order-detail-tabs">
          <el-tab-pane label="生产状态">
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>生产执行</h3><span>生产进度、质量与入库状态</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item :label="processLabel(selected)">{{ value(selected.currentProcessName) }}（{{ value(selected.currentProcessCode) }}）</el-descriptions-item>
                <el-descriptions-item label="工单状态"><el-tag :type="workOrderTagType(selected.status)" effect="light">{{ workOrderStatusText(selected.status) }}</el-tag></el-descriptions-item>
                <el-descriptions-item label="质量状态"><el-tag :type="qualityTagType(selected.qualityStatus)" effect="light">{{ qualityStatusText(selected.qualityStatus) }}</el-tag></el-descriptions-item>
                <el-descriptions-item label="优先级">{{ priorityText(selected.priority) }}</el-descriptions-item>
                <el-descriptions-item label="计划投产数">{{ value(selected.plannedQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="投料数量">{{ value(selected.materialInputQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="完工数量">{{ value(selected.completedQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="累计入库数">{{ value(selected.cumulativeWarehouseQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="累计报废数">{{ value(selected.cumulativeScrapQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="累计不良数">{{ value(selected.cumulativeDefectQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="入库状态"><el-tag :type="warehouseTagType(selected.warehouseStatus)" effect="plain">{{ warehouseStatusText(selected.warehouseStatus) }}</el-tag></el-descriptions-item>
                <el-descriptions-item label="未入库数量">{{ value(selected.unreceivedQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="最后入库日期">{{ formatDate(selected.lastWarehouseDate) }}</el-descriptions-item>
                <el-descriptions-item label="生产通知单号">{{ value(selected.productionNoticeNo) }}</el-descriptions-item>
                <el-descriptions-item label="生产通知单数量">{{ value(selected.productionNoticeQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="生产通知单订单数量">{{ value(selected.productionNoticeOrderQuantity) }}</el-descriptions-item>
              </el-descriptions>
            </section>
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>计划节点</h3><span>用于现场排产与交付跟踪</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="投料日期">{{ formatDate(selected.materialInputDate ?? selected.releasedAt) }}</el-descriptions-item>
                <el-descriptions-item label="输入/下达日期">{{ formatDate(selected.inputDate ?? selected.releasedAt) }}</el-descriptions-item>
                <el-descriptions-item label="要求交期">{{ formatDate(selected.requiredDeliveryDate) }}</el-descriptions-item>
                <el-descriptions-item label="订单编号">{{ value(selected.customerOrderNo) }}</el-descriptions-item>
                <el-descriptions-item label="订单数量">{{ value(selected.customerOrderQuantity) }}</el-descriptions-item>
              </el-descriptions>
            </section>
          </el-tab-pane>
          <el-tab-pane label="产品与工艺">
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>产品信息</h3><span>产品标识与客户要求</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="产品编码">{{ value(selected.productCode) }}</el-descriptions-item><el-descriptions-item label="厂内编号">{{ value(selected.internalProductNo) }}</el-descriptions-item><el-descriptions-item label="客户料号">{{ value(selected.customerPartNo) }}</el-descriptions-item>
                <el-descriptions-item label="产品名称">{{ value(selected.productName) }}</el-descriptions-item><el-descriptions-item label="产品规格" :span="2">{{ value(selected.specification) }}</el-descriptions-item>
                <el-descriptions-item label="客户">{{ value(selected.customerCode) }}</el-descriptions-item><el-descriptions-item label="板厚">{{ value(selected.boardThickness) }}</el-descriptions-item><el-descriptions-item label="层数">{{ value(selected.layerCount) }}</el-descriptions-item>
              </el-descriptions>
            </section>
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>工艺要求</h3><span>版本、材质及成型信息</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="工艺">{{ value(selected.processTechnology) }}</el-descriptions-item><el-descriptions-item label="工艺路线版本">{{ value(selected.processRouteVersion) }}</el-descriptions-item><el-descriptions-item label="MI 版本">{{ value(selected.miVersion) }}</el-descriptions-item>
                <el-descriptions-item label="材质">{{ value(selected.material) }}</el-descriptions-item><el-descriptions-item label="铜箔">{{ value(selected.copperThickness) }}</el-descriptions-item><el-descriptions-item label="成型方式">{{ value(selected.formingMethod) }}</el-descriptions-item>
              </el-descriptions>
            </section>
          </el-tab-pane>
          <el-tab-pane label="板材与尺寸">
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>板材与单元尺寸</h3><span>板材规格、PCS 及 A/B 板尺寸</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="板材编号">{{ value(selected.materialNo) }}</el-descriptions-item><el-descriptions-item label="板材名称">{{ value(selected.materialName) }}</el-descriptions-item><el-descriptions-item label="板材规格">{{ value(selected.materialSpecification) }}</el-descriptions-item>
                <el-descriptions-item label="标准用量">{{ value(selected.standardMaterialConsumption) }}</el-descriptions-item><el-descriptions-item label="PCS 长">{{ value(selected.pcsLength) }}</el-descriptions-item><el-descriptions-item label="PCS 宽">{{ value(selected.pcsWidth) }}</el-descriptions-item>
                <el-descriptions-item label="单 PCS 面积">{{ value(selected.singlePcsArea) }}</el-descriptions-item><el-descriptions-item label="PNL 长">{{ value(selected.pnlLength) }}</el-descriptions-item><el-descriptions-item label="PNL 宽">{{ value(selected.pnlWidth) }}</el-descriptions-item>
                <el-descriptions-item label="A 板尺寸">{{ value(selected.aBoardLength) }} × {{ value(selected.aBoardWidth) }}</el-descriptions-item><el-descriptions-item label="B 板尺寸">{{ value(selected.bBoardLength) }} × {{ value(selected.bBoardWidth) }}</el-descriptions-item>
              </el-descriptions>
            </section>
            <section class="work-order-detail-section">
              <div class="work-order-detail-section-heading"><h3>投料与拼版</h3><span>计划投料、拼板及面积数据</span></div>
              <el-descriptions :column="3" border>
                <el-descriptions-item label="总投料面积">{{ value(selected.materialArea) }} m²</el-descriptions-item><el-descriptions-item label="预投率">{{ value(selected.estimatedYieldRate) }}%</el-descriptions-item><el-descriptions-item label="计划领料">{{ value(selected.plannedMaterialIssueQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="总拼 PNL 数">{{ value(selected.totalPanelQuantity) }}</el-descriptions-item><el-descriptions-item label="A 板总 PNL">{{ value(selected.aBoardPanelQuantity) }}</el-descriptions-item><el-descriptions-item label="B 板总 PNL">{{ value(selected.bBoardPanelQuantity) }}</el-descriptions-item>
                <el-descriptions-item label="合拼编号" :span="3">{{ value(selected.panelizationNo) }}</el-descriptions-item>
                <el-descriptions-item label="生产通知单面积">{{ value(selected.productionNoticeArea) }} m²</el-descriptions-item><el-descriptions-item label="客户订单面积" :span="2">{{ value(selected.customerOrderArea) }} m²</el-descriptions-item>
              </el-descriptions>
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </section>
</template>

<style>
.work-order-filters { grid-template-columns: repeat(4, minmax(170px, 1fr)); }
.work-order-keyword-field { grid-column: span 2; }
.work-order-filter-actions { justify-content: flex-end; }
.work-order-filter-heading-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 14px; }
.work-order-filter-toggle { margin: 0; }
.work-order-filter-toggle .el-icon { margin-right: 4px; }
.work-order-summary-table-wrap .el-table { min-width: 1370px; }
.work-order-summary-table-wrap.no-batch-column .el-table { min-width: 1225px; }
.work-order-data-table small { color: #667085; font-size: 11px; }
.work-order-detail-link { font-weight: 650; }
.work-order-detail-dialog .el-dialog__header { margin-right: 0; padding: 20px 28px 16px; border-bottom: 1px solid #e5edf7; }
.work-order-detail-dialog .el-dialog__headerbtn { top: 20px; right: 22px; }
.work-order-detail-dialog .el-dialog__body { padding: 18px 28px 26px; max-height: calc(88vh - 100px); overflow-y: auto; }
.work-order-detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding-right: 42px; }
.work-order-detail-header p { margin: 0 0 4px; color: #6b7b93; font-size: 13px; line-height: 1.4; }
.work-order-detail-header h2 { margin: 0; color: #172b4d; font-size: 22px; line-height: 1.35; }
.work-order-detail-header span { display: inline-block; margin-top: 6px; color: #52657e; font-size: 13px; }
.work-order-detail-statuses { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; padding-top: 5px; }
.work-order-detail-statuses .el-tag { height: 27px; padding: 0 10px; }
.work-order-detail-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); overflow: hidden; margin-bottom: 18px; border: 1px solid #dbe8f5; border-radius: 10px; background: #f8fbff; }
.work-order-detail-metrics > div { min-width: 0; padding: 13px 16px; border-right: 1px solid #dbe8f5; }
.work-order-detail-metrics > div:last-child { border-right: 0; }
.work-order-detail-metrics span { display: block; overflow: hidden; color: #718198; font-size: 12px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.work-order-detail-metrics strong { display: block; overflow: hidden; margin-top: 5px; color: #173c68; font-size: 18px; font-weight: 650; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.work-order-detail-tabs > .el-tabs__header { margin: 0 0 18px; }
.work-order-detail-tabs .el-tabs__item { height: 42px; padding: 0 22px; font-size: 15px; font-weight: 600; }
.work-order-detail-tabs .el-descriptions { margin: 0; }
.work-order-detail-tabs .el-descriptions__label { color: #475467; font-weight: 600; white-space: nowrap; }
.work-order-detail-tabs .el-descriptions__content { color: #172b4d; word-break: break-word; }
.work-order-detail-section + .work-order-detail-section { margin-top: 20px; }
.work-order-detail-section-heading { display: flex; align-items: baseline; gap: 10px; margin: 0 0 10px; }
.work-order-detail-section-heading h3 { margin: 0; color: #243b5a; font-size: 15px; }
.work-order-detail-section-heading span { color: #8090a5; font-size: 12px; }

@media (max-width: 850px) {
  .work-order-detail-dialog .el-dialog__header { padding: 18px 20px 14px; }
  .work-order-detail-dialog .el-dialog__body { padding: 16px 20px 22px; }
  .work-order-detail-header { flex-direction: column; gap: 10px; }
  .work-order-detail-statuses { justify-content: flex-start; padding-top: 0; }
  .work-order-detail-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .work-order-detail-metrics > div:nth-child(2n) { border-right: 0; }
  .work-order-detail-metrics > div:nth-child(-n+3) { border-bottom: 1px solid #dbe8f5; }
  .work-order-filter-heading-actions { align-self: flex-start; }
}

@media (max-width: 1100px) {
  .work-order-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .work-order-keyword-field { grid-column: span 2; }
}

@media (max-width: 760px) {
  .work-order-filters { grid-template-columns: 1fr; }
  .work-order-keyword-field { grid-column: auto; }
  .work-order-filter-actions { justify-content: flex-start; }
  .work-order-filter-heading-actions { flex-wrap: wrap; gap: 8px 14px; }
}
</style>
