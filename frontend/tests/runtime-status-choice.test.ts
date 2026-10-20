import assert from 'node:assert/strict'
import test from 'node:test'

import { statusText } from '../src/utils/display-text.ts'
import { chooseRuntimeLabel } from '../src/utils/runtime-status-choice.ts'

test('flying probe status uses the effective presence result over old telemetry', () => {
  for (const [status, legacy] of [
    ['RUNNING', '离线'],
    ['ONLINE', '加工中'],
    ['OFFLINE', '加工中'],
    ['UNKNOWN', '加工中']
  ]) {
    const label = statusText(status)
    assert.equal(chooseRuntimeLabel('FLYING_PROBE', label, legacy), label)
  }
})

test('other equipment still prefers the legacy telemetry state', () => {
  assert.equal(chooseRuntimeLabel('TELEMETRY', '离线', '加工中'), '加工中')
  assert.equal(chooseRuntimeLabel(undefined, '离线', undefined), '离线')
})

test('presence statuses have distinct Chinese labels', () => {
  assert.deepEqual(
    ['RUNNING', 'ONLINE', 'OFFLINE', 'UNKNOWN'].map(statusText),
    ['运行中', '在线', '离线', '未确认']
  )
})
