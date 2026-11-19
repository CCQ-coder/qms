import assert from 'node:assert/strict'
import test from 'node:test'
import { flyingProbeDetectionTime } from './flyingProbeDetectionTime.ts'

test('uses the corrected time for a calibrated flying-probe record', () => {
  assert.deepEqual(flyingProbeDetectionTime({
    deviceTime: '2026-11-19T09:58:00',
    correctedDeviceTime: '2026-11-19T09:48:00'
  }), { text: '2026-11-19 09:48:00', note: '' })
})

test('identifies the raw device time when a record has no correction', () => {
  assert.deepEqual(flyingProbeDetectionTime({ deviceTime: '2026-11-19T09:58:00' }), {
    text: '2026-11-19 09:58:00', note: '未校时'
  })
  assert.deepEqual(flyingProbeDetectionTime({ deviceTime: '2026-11-19T09:58:00', correctedDeviceTime: '  ' }), {
    text: '2026-11-19 09:58:00', note: '未校时'
  })
})

test('shows no time or calibration warning when there is no flying-probe record', () => {
  assert.deepEqual(flyingProbeDetectionTime(undefined), { text: '—', note: '' })
})

test('rejects malformed and impossible corrected timestamps from legacy payloads', () => {
  for (const correctedDeviceTime of ['2026-02-30T09:48:00', '2026-11-19T09:48:00Z', '2026-11-19 09:48:00']) {
    assert.deepEqual(flyingProbeDetectionTime({ deviceTime: '2026-11-19T09:58:00', correctedDeviceTime }), {
      text: '2026-11-19 09:58:00', note: '未校时'
    })
  }
})

test('rejects a corrected time more than two hours from the source timestamp', () => {
  assert.deepEqual(flyingProbeDetectionTime({
    deviceTime: '2026-11-19T09:58:00', correctedDeviceTime: '2026-11-19T12:58:01'
  }), { text: '2026-11-19 09:58:00', note: '未校时' })
  assert.deepEqual(flyingProbeDetectionTime({
    deviceTime: '2026-11-19T09:58:00', correctedDeviceTime: '2026-11-19T07:58:00'
  }), { text: '2026-11-19 07:58:00', note: '' })
})
