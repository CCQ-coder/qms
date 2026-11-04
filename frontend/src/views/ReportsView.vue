<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { qmsApi } from '@/api/qms'
import FinishedTestAnalysis from '@/components/FinishedTestAnalysis.vue'
import { formatDateTime, statusText, taskTypeText } from '@/utils/display-text'
import type { Traceability } from '@/types/qms'

const route = useRoute()
const isAnalyticsPage = computed(() => route.meta.reportSection !== 'traceability')
const batchNo = ref('')
const traceLoading = ref(false)
const traceability = ref<Traceability>()

async function searchTrace() {
  if (!batchNo.value.trim()) return
  traceLoading.value = true
  traceability.value = undefined
  try {
    traceability.value = await qmsApi.getBatchTraceability(batchNo.value.trim())
  } catch {
    ElMessage.error('未查询到该生产批次')
  } finally {
    traceLoading.value = false
  }
}
</script>

<template>
  <section class="page-stack">
    <FinishedTestAnalysis v-if="isAnalyticsPage" initial-dimension="workOrder" show-trend />
    <section v-else>
      <el-card shadow="never" class="content-card">
        <template #header><div class="card-header"><span>生产批次质量追溯</span><div class="trace-search"><el-input v-model="batchNo" placeholder="输入生产批次" @keyup.enter="searchTrace" /><el-button type="primary" :loading="traceLoading" @click="searchTrace">追溯查询</el-button></div></div></template>
        <template v-if="traceability">
          <div class="trace-overview"><span>工单：<strong>{{ traceability.workOrder.workOrderNo }}</strong></span><span>批次：<strong>{{ traceability.batchNo }}</strong></span><span>产品：<strong>{{ traceability.workOrder.productCode }} {{ traceability.workOrder.productName }}</strong></span></div>
          <paged-table :key="traceability.batchNo" :data="traceability.inspectionRecords" empty-text="该批次暂无检验记录">
            <el-table-column prop="taskNo" label="任务号" min-width="150" />
            <el-table-column prop="processName" label="工序" min-width="140" />
            <el-table-column label="检验类型" min-width="110"><template #default="scope">{{ taskTypeText(scope.row.taskType) }}</template></el-table-column>
            <el-table-column label="结果" min-width="100"><template #default="scope"><el-tag :type="scope.row.verdict === 'PASS' ? 'success' : 'danger'">{{ statusText(scope.row.verdict) }}</el-tag></template></el-table-column>
            <el-table-column prop="defectiveQuantity" label="不良数量" min-width="100" />
            <el-table-column prop="defectSummary" label="缺陷说明" min-width="210" show-overflow-tooltip />
            <el-table-column prop="defectLocation" label="缺陷位置" min-width="180" show-overflow-tooltip />
            <el-table-column prop="inspectedBy" label="检验人" min-width="110" />
            <el-table-column label="检验时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.inspectedAt) }}</template></el-table-column>
          </paged-table>
          <div v-if="traceability.nonconformities.length" class="trace-ncr"><strong>关联不良品处置：</strong><el-tag v-for="ncr in traceability.nonconformities" :key="ncr.ncrNo" type="warning">{{ ncr.ncrNo }} · {{ statusText(ncr.status) }}</el-tag></div>
        </template>
        <el-empty v-else description="请输入批次号" />
      </el-card>
    </section>
  </section>
</template>
