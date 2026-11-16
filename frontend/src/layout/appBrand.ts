import { reactive } from 'vue'

export const DEFAULT_APP_TITLE = 'PCB QMS'
export const BROWSER_TITLE_KEY = 'ui.browser-title'
export const SIDEBAR_TITLE_KEY = 'ui.sidebar-title'

export const appBrand = reactive({ browserTitle: DEFAULT_APP_TITLE, sidebarTitle: DEFAULT_APP_TITLE })

export function setBrowserTitle(value: string) {
  appBrand.browserTitle = value.trim() || DEFAULT_APP_TITLE
  document.title = appBrand.browserTitle
}

export function setSidebarTitle(value: string) {
  appBrand.sidebarTitle = value.trim() || DEFAULT_APP_TITLE
}

export function resetAppBrand() {
  setBrowserTitle(DEFAULT_APP_TITLE)
  setSidebarTitle(DEFAULT_APP_TITLE)
}
