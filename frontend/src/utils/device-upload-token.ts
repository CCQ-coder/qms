export function requireHttpsDeviceToken(pageUrl: string, apiBaseUrl = '') {
  if (new URL(pageUrl).protocol !== 'https:' || new URL(apiBaseUrl, pageUrl).protocol !== 'https:') {
    throw new Error('设备上传令牌只能通过 HTTPS 签发或撤销，请使用 HTTPS 管理页面')
  }
}
