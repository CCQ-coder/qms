const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const { runInNewContext } = require('node:vm')
const test = require('node:test')

const source = readFileSync(join(__dirname, '../public/sw.js'), 'utf8')

function worker(networkResponse, cachedResponse) {
  const handlers = new Map()
  const entries = new Map()
  const assetUrl = 'https://qms.example/assets/View-123.js'
  if (cachedResponse) entries.set(assetUrl, cachedResponse)
  let fetches = 0
  const cache = {
    match: async (request) => entries.get(request.url),
    put: async (request, response) => { entries.set(request.url, response) }
  }
  runInNewContext(source, {
    self: {
      location: { origin: 'https://qms.example' },
      addEventListener: (name, handler) => handlers.set(name, handler)
    },
    caches: { open: async () => cache, match: cache.match },
    fetch: async () => { fetches++; return networkResponse },
    URL,
    Response
  })
  return {
    assetUrl,
    get fetches() { return fetches },
    entries,
    async request(url) {
      let result
      handlers.get('fetch')({ request: new Request(url), respondWith: (promise) => { result = promise } })
      return result
    }
  }
}

test('cached versioned assets open without waiting for the network', async () => {
  const sw = worker(
    new Response('new script', { headers: { 'content-type': 'application/javascript' } }),
    new Response('cached script', { headers: { 'content-type': 'application/javascript' } })
  )

  assert.equal(await (await sw.request(sw.assetUrl)).text(), 'cached script')
  assert.equal(sw.fetches, 0)
})

test('an HTML fallback is never cached as a JavaScript asset', async () => {
  const sw = worker(new Response('<html>fallback</html>', { headers: { 'content-type': 'text/html' } }))

  await sw.request(sw.assetUrl)
  assert.equal(sw.entries.has(sw.assetUrl), false)
})
