<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { formatDate, formatDateTime, statusText } from '@/utils/display-text'
import type { Nonconformity } from '@/types/qms'

const loading = ref(false)
const nonconformities = ref<Nonconformity[]>([])
const rows = computed(() => nonconformities.value.flatMap((ncr) =>
  ncr.details.map((detail) => ({ ncr, detail }))))

function dispositionText(method?: string) {
  return ({ SCRAP: '报废', REWORK: '返工', CONCESSION: '让步接收' } as Record<string, string>)[method ?? ''] ?? method ?? '—'
}

async function load() {
  loading.value = true
  try {
    nonconformities.value = await qmsApi.listNonconformities()
  } catch {
    ElMessage.error('不良品明细读取失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <el-card shadow="never" class="content-card">
    <template #header><div class="card-header"><span>不良品明细表</span><el-button link type="primary" @click="load">刷新</el-button></div></template>
    <el-table v-loading="loading" :data="rows" empty-text="暂无不良品明细" border>
      <el-table-column label="单据编号" min-width="180" fixed="left"><template #default="{ row }">{{ row.ncr.ncrNo }}</template></el-table-column>
      <el-table-column label="检测日期" min-width="120"><template #default="{ row }">{{ formatDate(row.ncr.detectionDate) }}</template></el-table-column>
      <el-table-column label="班次" min-width="90"><template #default="{ row }">{{ row.ncr.shiftName || '—' }}</template></el-table-column>
      <el-table-column label="检验工序" min-width="130"><template #default="{ row }">{{ row.ncr.processName || '—' }}</template></el-table-column>
      <el-table-column label="责任部门" min-width="130"><template #default="{ row }">{{ row.ncr.responsibleDepartment || '—' }}</template></el-table-column>
      <el-table-column label="处置方式" min-width="105"><template #default="{ row }">{{ dispositionText(row.ncr.dispositionMethod) }}</template></el-table-column>
      <el-table-column label="行号" min-width="70"><template #default="{ row }">{{ row.detail.lineNo }}</template></el-table-column>
      <el-table-column label="生产指令" min-width="150"><template #default="{ row }">{{ row.detail.workOrderNo }}</template></el-table-column>
      <el-table-column label="厂内编号" min-width="135"><template #default="{ row }">{{ row.detail.internalProductNo || '—' }}</template></el-table-column>
      <el-table-column label="单位" min-width="75"><template #default="{ row }">{{ row.detail.unit }}</template></el-table-column>
      <el-table-column label="单 PCS 面积" min-width="125" align="right"><template #default="{ row }">{{ row.detail.singlePcsArea ?? '—' }}</template></el-table-column>
      <el-table-column label="报废数量" min-width="105" align="right"><template #default="{ row }">{{ row.detail.scrapQuantity }}</template></el-table-column>
      <el-table-column label="报废总面积" min-width="125" align="right"><template #default="{ row }">{{ row.detail.scrapArea ?? '—' }}</template></el-table-column>
      <el-table-column label="报废原因" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.detail.scrapReason || '—' }}</template></el-table-column>
      <el-table-column label="明细备注" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.detail.remark || '—' }}</template></el-table-column>
      <el-table-column label="单据备注" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.ncr.remark || '—' }}</template></el-table-column>
      <el-table-column label="创建来源" min-width="130"><template #default="{ row }">{{ row.ncr.sourceType === 'DEVICE' ? '系统自动生成' : (row.ncr.initiatedBy || '—') }}</template></el-table-column>
      <el-table-column label="创建日期" min-width="170"><template #default="{ row }">{{ formatDateTime(row.ncr.createdAt) }}</template></el-table-column>
      <el-table-column label="状态" min-width="110"><template #default="{ row }">{{ statusText(row.ncr.status) }}</template></el-table-column>
      <el-table-column label="审核人" min-width="110"><template #default="{ row }">{{ row.ncr.reviewedBy || '—' }}</template></el-table-column>
      <el-table-column label="审核时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.ncr.reviewedAt) }}</template></el-table-column>
    </el-table>
  </el-card>
</template>
