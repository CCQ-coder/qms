<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <section class="placeholder-page">
    <el-empty :description="String(route.meta.description ?? '功能开发中')">
      <template #image><div class="placeholder-mark">Q</div></template>
      <el-button type="primary" @click="$router.push('/dashboard')">返回质量总览</el-button>
    </el-empty>
  </section>
</template>

