<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { clampPage, paginateRows } from '@/utils/pagination'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ data: unknown[]; hasMore?: boolean; loadError?: string }>()
const emit = defineEmits<{ 'load-more': [] }>()
const currentPage = ref(1)
const pageSize = ref(10)
const total = computed(() => props.data.length)
const paginationTotal = computed(() => Math.max(total.value, 1))
const rows = computed(() => paginateRows(props.data, currentPage.value, pageSize.value))
const pageStart = computed(() => total.value ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))

watch(total, (count, previous) => {
  currentPage.value = count < previous ? 1 : clampPage(currentPage.value, paginationTotal.value, pageSize.value)
})
watch(paginationTotal, (count) => {
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
      <span v-if="rows.length">显示第 {{ pageStart }}–{{ pageEnd }} 条，{{ hasMore ? '已加载' : '共' }} {{ total }} 条</span>
      <span v-else>{{ loadError || (hasMore ? '当前页暂无记录，可继续翻页' : '暂无记录') }}，已加载 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="paginationTotal"
        :pager-count="11"
        background
        layout="sizes, prev, pager, next, jumper"
        @size-change="changePageSize"
      />
      <el-button v-if="hasMore && loadError" link type="primary" @click="emit('load-more')">重试读取</el-button>
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
