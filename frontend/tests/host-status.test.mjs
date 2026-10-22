import test from 'node:test'
import assert from 'node:assert/strict'
import { formatHostCapacity, formatHostUptime, isHostOnline } from '../src/utils/host-status.ts'

test('host status display keeps units and freshness clear', () => {
  assert.equal(formatHostCapacity(8 * 1024 ** 3, 16 * 1024 ** 3), '8.0 / 16.0 GiB')
  assert.equal(formatHostUptime(90061), '1 天 1 小时')
  assert.equal(formatHostUptime(3599), '59 分钟')
  assert.equal(isHostOnline('2026-10-22T05:00:00Z', Date.parse('2026-10-22T05:05:00Z'), 300), true)
  assert.equal(isHostOnline('2026-10-22T05:00:00Z', Date.parse('2026-10-22T05:05:01Z'), 300), false)
})
