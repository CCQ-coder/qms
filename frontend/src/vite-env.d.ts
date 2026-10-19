/// <reference types="vite/client" />

interface Window {
  __QMS_RUNTIME_CONFIG__?: {
    oidcIssuer?: string
    oidcClientId?: string
    oidcScope?: string
  }
}
