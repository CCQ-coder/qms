const CACHE_NAME = 'pcb-qms-shell-v4'
const APP_SHELL = ['/', '/manifest.webmanifest']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)))
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(
    keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
  )))
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  const requestUrl = new URL(event.request.url)
  if (event.request.method !== 'GET' || requestUrl.origin !== self.location.origin
    || requestUrl.pathname.startsWith('/api/') || requestUrl.pathname === '/sw.js') return
  event.respondWith(fetch(event.request)
    .then(async (response) => {
      // Never save an HTML SPA fallback under a hashed asset URL.
      const assetHtmlFallback = requestUrl.pathname.startsWith('/assets/')
        && response.headers.get('content-type')?.includes('text/html')
      if (response.ok && !assetHtmlFallback) {
        const cache = await caches.open(CACHE_NAME)
        cache.put(event.request, response.clone())
      }
      return response
    })
    .catch(async () => {
      const cachedResponse = await caches.match(event.request)
      if (cachedResponse) return cachedResponse
      if (event.request.mode === 'navigate') return (await caches.match('/')) || Response.error()
      return Response.error()
    }))
})
