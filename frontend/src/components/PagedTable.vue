<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { clampPage, paginateRows } from '@/utils/pagination'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ data: unknown[]; total?: number; page?: number; pageSize?: number }>()
const emit = defineEmits<{ 'page-change': [page: number, pageSize: number] }>()
const currentPage = ref(1)
const pageSize = ref(10)
const serverPaged = computed(() => props.total !== undefined)
const selectedPage = computed(() => serverPaged.value ? (props.page ?? 1) : currentPage.value)
const selectedSize = computed(() => serverPaged.value ? (props.pageSize ?? 10) : pageSize.value)
const total = computed(() => props.total ?? props.data.length)
const paginationTotal = computed(() => Math.max(total.value, 1))
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / selectedSize.value)))
const rows = computed(() => serverPaged.value ? props.data : paginateRows(props.data, currentPage.value, pageSize.value))
const pageStart = computed(() => total.value ? (selectedPage.value - 1) * selectedSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(pageStart.value + rows.value.length - 1, total.value))

watch(total, (count, previous) => {
  currentPage.value = count < previous ? 1 : clampPage(currentPage.value, paginationTotal.value, pageSize.value)
})
watch(paginationTotal, (count) => {
  currentPage.value = clampPage(currentPage.value, count, pageSize.value)
})

function changePageSize(size: number) {
  if (serverPaged.value) {
    emit('page-change', 1, size)
    return
  }
  pageSize.value = size
  currentPage.value = 1
}

function changePage(page: number) {
  if (serverPaged.value) emit('page-change', page, selectedSize.value)
  else currentPage.value = page
}
</script>

<template>
  <div class="paged-table">
    <el-table v-bind="$attrs" :data="rows"><slot /></el-table>
    <div class="paged-table__footer">
      <span v-if="rows.length && serverPaged">第 {{ selectedPage }}/{{ pageCount }} 页，本页显示 {{ rows.length }} 条，共 {{ total }} 条</span>
      <span v-else-if="rows.length">显示第 {{ pageStart }}–{{ pageEnd }} 条，共 {{ total }} 条</span>
      <span v-else>暂无记录，共 {{ total }} 条</span>
      <el-pagination
        :current-page="selectedPage"
        :page-size="selectedSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="paginationTotal"
        :pager-count="11"
        background
        layout="sizes, prev, pager, next, jumper"
        @current-change="changePage"
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
