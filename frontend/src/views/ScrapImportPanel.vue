<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { isMenuVisible, loadMenuAccess, menuAccess } from '@/navigation/menu'
import type { ScrapImportJob } from '@/types/qms'

const emit = defineEmits<{ imported: [] }>()
const canImport = computed(() => Boolean(menuAccess.value) && isMenuVisible('SCRAP_IMPORT'))
const visible = ref(false)
const uploading = ref(false)
const selectedFile = ref<File>()
const jobs = ref<ScrapImportJob[]>([])
const knownStatuses = new Map<string, string>()
let timer: number | undefined

async function loadJobs() {
  if (!canImport.value) return
  try {
    const result = await qmsApi.listScrapImports()
    for (const job of result) {
      if (['PENDING', 'PROCESSING'].includes(knownStatuses.get(job.id) ?? '') && job.status === 'COMPLETED') emit('imported')
      knownStatuses.set(job.id, job.status)
    }
    jobs.value = result
  } catch { ElMessage.error('导入任务读取失败') }
}

async function upload() {
  if (!selectedFile.value || uploading.value) return
  uploading.value = true
  try {
    const job = await qmsApi.uploadScrapImport(selectedFile.value)
    ElMessage.success(job.status === 'COMPLETED' ? '该文件已导入，无需重复上传' : '文件已提交，后台正在导入')
    selectedFile.value = undefined
    await loadJobs()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报废明细上传失败')
  } finally { uploading.value = false }
}

async function downloadTemplate() {
  try {
    const blob = await qmsApi.downloadScrapImportTemplate()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '报废明细导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch { ElMessage.error('模板下载失败') }
}

async function open() {
  visible.value = true
  await loadJobs()
}

onMounted(async () => {
  await loadMenuAccess()
  timer = window.setInterval(() => {
    if (visible.value && jobs.value.some((job) => ['PENDING', 'PROCESSING'].includes(job.status))) void loadJobs()
  }, 3000)
})
onBeforeUnmount(() => { if (timer != null) window.clearInterval(timer) })
</script>

<template>
  <el-button v-if="canImport" type="primary" @click="open">批量导入报废明细</el-button>
  <el-dialog v-model="visible" title="批量导入报废明细" width="min(900px, 94vw)">
    <p class="helper-text">按十列模板填写。上传后后台逐行导入；未找到唯一合适生产指令的明细可在报废明细表手工关联。</p>
    <div class="task-queue-actions">
      <el-button @click="downloadTemplate">下载空白模板</el-button>
      <input type="file" accept=".xlsx" aria-label="选择报废明细 Excel" @change="selectedFile = ($event.target as HTMLInputElement).files?.[0]" />
      <el-button type="primary" :disabled="!selectedFile" :loading="uploading" @click="upload">开始导入</el-button>
    </div>
    <el-table :data="jobs" empty-text="暂无导入任务" border style="margin-top: 16px" max-height="320">
      <el-table-column prop="filename" label="文件" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ { PENDING: '待处理', PROCESSING: '导入中', COMPLETED: '已完成', FAILED: '失败' }[row.status as ScrapImportJob['status']] }}</template></el-table-column>
      <el-table-column label="进度" width="110"><template #default="{ row }">{{ row.processed }}/{{ row.total || '—' }}</template></el-table-column>
      <el-table-column prop="imported" label="已导入" width="80" />
      <el-table-column prop="unmatched" label="待关联" width="80" />
      <el-table-column prop="invalid" label="失败行" width="80" />
      <el-table-column label="错误" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.errors.join('；') || '—' }}</template></el-table-column>
    </el-table>
  </el-dialog>
</template>
