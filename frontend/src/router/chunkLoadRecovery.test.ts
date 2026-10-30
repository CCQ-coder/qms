import assert from 'node:assert/strict'
import test from 'node:test'
import {
  clearChunkLoadRecovery,
  isChunkLoadError,
  recoverFromChunkLoadError
} from './chunkLoadRecovery.ts'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string) {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string) {
    this.values.set(key, value)
  }

  removeItem(key: string) {
    this.values.delete(key)
  }
}

test('recognizes stale dynamic import and chunk load failures', () => {
  assert.equal(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: /assets/View-old.js')), true)
  assert.equal(isChunkLoadError(new Error('Loading chunk 42 failed.')), true)
  assert.equal(isChunkLoadError(new Error('Importing a module script failed.')), true)
  assert.equal(isChunkLoadError(new Error('Request failed with status code 500')), false)
})

test('reloads only once for the same failed route in one session', () => {
  const storage = new MemoryStorage()
  let reloads = 0
  const options = {
    routeKey: '/inspection',
    storage,
    reload: () => { reloads += 1 }
  }

  assert.equal(recoverFromChunkLoadError(new Error('Failed to fetch dynamically imported module: /assets/Inspection-old.js'), options), true)
  assert.equal(reloads, 1)
  assert.equal(recoverFromChunkLoadError(new Error('Failed to fetch dynamically imported module: /assets/Inspection-old.js'), options), false)
  assert.equal(reloads, 1)
})

test('successful navigation clears the guard so a later deployment can recover', () => {
  const storage = new MemoryStorage()
  let reloads = 0
  const options = {
    routeKey: '/inspection',
    storage,
    reload: () => { reloads += 1 }
  }
  const error = new Error('Failed to fetch dynamically imported module: /assets/Inspection-old.js')

  recoverFromChunkLoadError(error, options)
  clearChunkLoadRecovery('/inspection', storage)
  assert.equal(recoverFromChunkLoadError(error, options), true)
  assert.equal(reloads, 2)
})

test('does not reload for unrelated application errors', () => {
  const storage = new MemoryStorage()
  let reloads = 0

  assert.equal(recoverFromChunkLoadError(new Error('HTTP 401'), {
    routeKey: '/inspection',
    storage,
    reload: () => { reloads += 1 }
  }), false)
  assert.equal(reloads, 0)
})
