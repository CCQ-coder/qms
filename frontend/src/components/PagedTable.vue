<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { clampPage, paginateRows } from '@/utils/pagination'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ data: unknown[] }>()
const currentPage = ref(1)
const pageSize = ref(10)
const total = computed(() => props.data.length)
const rows = computed(() => paginateRows(props.data, currentPage.value, pageSize.value))
const pageStart = computed(() => total.value ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))

watch(total, (count) => {
  currentPage.value = clampPage(currentPage.value, count, pageSize.value)
})

function changePageSize(size: number) {
  pageSize.value = size
  currentPage.value = 1
}
</script>

<template>
  <div class="paged-table">
    <el-table v-bind="$attrs" :data="rows"><slot /></el-table>
    <div class="paged-table__footer">
      <span>显示第 {{ pageStart }}–{{ pageEnd }} 条，共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        background
        layout="sizes, prev, pager, next, jumper"
        @size-change="changePageSize"
      />
    </div>
  </div>
</template>

<style scoped>
.paged-table { min-width: 0; }
.paged-table__footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 2px 0; color: #667085; font-size: 12px; }
.paged-table__footer > span { white-space: nowrap; }
.paged-table__footer :deep(.el-pagination) { justify-content: flex-end; }
@media (max-width: 760px) {
  .paged-table__footer :deep(.el-pagination) { justify-content: flex-start; max-width: 100%; overflow-x: auto; }
}
</style>
