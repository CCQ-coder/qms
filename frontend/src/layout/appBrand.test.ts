import assert from 'node:assert/strict'
import test from 'node:test'
import { appBrand, resetAppBrand, setBrowserTitle, setSidebarTitle } from './appBrand.ts'

test('configured titles update the tab and sidebar independently and reset between tenants', () => {
  const previousDocument = globalThis.document
  globalThis.document = { title: 'PCB QMS' } as Document
  try {
    setBrowserTitle(' 共赢质量系统 ')
    setSidebarTitle(' 共赢 QMS ')
    assert.equal(document.title, '共赢质量系统')
    assert.equal(appBrand.sidebarTitle, '共赢 QMS')
    setBrowserTitle('新浏览器标题')
    assert.equal(appBrand.sidebarTitle, '共赢 QMS')
    resetAppBrand()
    assert.equal(document.title, 'PCB QMS')
    assert.equal(appBrand.sidebarTitle, 'PCB QMS')
  } finally {
    globalThis.document = previousDocument
  }
})
