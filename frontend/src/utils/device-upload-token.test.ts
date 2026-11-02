import assert from 'node:assert/strict'
import test from 'node:test'
import { requireHttpsDeviceToken } from './device-upload-token.ts'

test('upload credentials are never sent from an HTTP admin page', () => {
  assert.throws(() => requireHttpsDeviceToken('http://106.227.69.144:5173/equipment/gateway-mappings'), /HTTPS/)
})

test('HTTPS admin still refuses an explicitly insecure API base URL', () => {
  assert.throws(() => requireHttpsDeviceToken('https://106.227.69.144:5173/', 'http://106.227.69.144:8080'), /HTTPS/)
})

test('relative API requests keep the HTTPS admin origin', () => {
  assert.doesNotThrow(() => requireHttpsDeviceToken('https://106.227.69.144:5173/'))
  assert.doesNotThrow(() => requireHttpsDeviceToken('https://106.227.69.144:5173/', '/'))
})
