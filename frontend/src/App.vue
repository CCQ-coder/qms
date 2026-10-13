<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppShell from '@/layout/AppShell.vue'

const route = useRoute()
const isPublicRoute = computed(() => Boolean(route.meta.public))
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <router-view v-if="isPublicRoute" />
    <AppShell v-else />
  </ElConfigProvider>
</template>
