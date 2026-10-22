import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElAside,
  ElButton,
  ElCard,
  ElCheckbox,
  ElContainer,
  ElDatePicker,
  ElDialog,
  ElDivider,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElProgress,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElUpload,
  vLoading
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import PagedTable from './components/PagedTable.vue'
import { finishLoginCallback } from './auth/oidc'
import { initializeDesktopConnection, isDesktopApplication } from './desktop/connection'
import router from './router'
import './styles.css'

async function bootstrap() {
  try {
    await initializeDesktopConnection()
  } catch (error) {
    console.error('桌面端服务地址初始化失败', error)
  }

  try {
    await finishLoginCallback()
  } catch (error) {
    console.error('OIDC 登录初始化失败', error)
  }

  const app = createApp(App)
  app.use(createPinia()).use(router)
  app.component('PagedTable', PagedTable)
  for (const component of [
    ElAlert, ElAside, ElButton, ElCard, ElCheckbox, ElContainer, ElDatePicker, ElDialog, ElDivider, ElEmpty,
    ElForm, ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber, ElMain, ElMenu, ElMenuItem, ElOption, ElProgress,
    ElPagination, ElRadioButton, ElRadioGroup, ElSelect, ElSwitch, ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag, ElUpload
  ]) {
    app.use(component)
  }
  app.directive('loading', vLoading)
  app.mount('#app')
}

void bootstrap()

if ('serviceWorker' in navigator) {
  if (isDesktopApplication()) {
    // Tauri uses a persistent WebView profile.  The browser-oriented offline
    // worker can retain stale asset references there and leave the desktop
    // shell blank after an upgrade, so it must never control this client.
    void navigator.serviceWorker.getRegistrations()
      .then((registrations) => Promise.all(registrations.map((registration) => registration.unregister())))
      .catch((error) => console.warn('清理桌面端离线缓存失败', error))
  } else {
    window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'))
  }
}
