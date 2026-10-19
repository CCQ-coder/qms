type RuntimeOidcConfiguration = {
  issuer: string
  clientId: string
  scope: string
}

type OidcMetadata = {
  authorization_endpoint: string
  token_endpoint: string
  end_session_endpoint?: string
}

type TokenSet = {
  accessToken: string
  idToken?: string
  expiresAt: number
}

const TOKEN_STORAGE_KEY = 'qms.oidc.token-set'
const STATE_STORAGE_KEY = 'qms.oidc.state'
const VERIFIER_STORAGE_KEY = 'qms.oidc.verifier'
let metadataPromise: Promise<OidcMetadata> | undefined

function configuration(): RuntimeOidcConfiguration {
  const configured = window.__QMS_RUNTIME_CONFIG__
  return {
    issuer: configured?.oidcIssuer?.trim().replace(/\/$/, '') ?? '',
    clientId: configured?.oidcClientId?.trim() ?? '',
    scope: configured?.oidcScope?.trim() || 'openid profile'
  }
}

function callbackUrl(): string {
  return `${window.location.origin}${window.location.pathname}`
}

function encodeBase64Url(bytes: Uint8Array): string {
  let value = ''
  bytes.forEach((byte) => { value += String.fromCharCode(byte) })
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function randomBase64Url(): string {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  return encodeBase64Url(bytes)
}

async function codeChallenge(verifier: string): Promise<string> {
  return encodeBase64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))
}

async function metadata(): Promise<OidcMetadata> {
  const { issuer } = configuration()
  if (!issuer) throw new Error('尚未配置 OIDC issuer')
  metadataPromise ??= fetch(`${issuer}/.well-known/openid-configuration`, { cache: 'no-store' })
    .then(async (response) => {
      if (!response.ok) throw new Error(`无法读取 OIDC 配置（HTTP ${response.status}）`)
      const document = await response.json() as Partial<OidcMetadata>
      if (!document.authorization_endpoint || !document.token_endpoint) {
        throw new Error('OIDC 配置缺少授权端点或令牌端点')
      }
      return document as OidcMetadata
    })
  return metadataPromise
}

function readTokenSet(): TokenSet | undefined {
  const raw = sessionStorage.getItem(TOKEN_STORAGE_KEY)
  if (!raw) return undefined
  try {
    const tokenSet = JSON.parse(raw) as TokenSet
    if (!tokenSet.accessToken || tokenSet.expiresAt <= Date.now()) {
      sessionStorage.removeItem(TOKEN_STORAGE_KEY)
      return undefined
    }
    return tokenSet
  } catch {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
    return undefined
  }
}

function jwtSubject(token: string | undefined): string | undefined {
  if (!token) return undefined
  const payload = token.split('.')[1]
  if (!payload) return undefined
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const decoded = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))) as { sub?: unknown }
    return typeof decoded.sub === 'string' && decoded.sub.trim() ? decoded.sub : undefined
  } catch {
    return undefined
  }
}

export function isOidcEnabled(): boolean {
  const { issuer, clientId } = configuration()
  return Boolean(issuer && clientId)
}

export function accessToken(): string | undefined {
  return readTokenSet()?.accessToken
}

export function authenticatedSubject(): string | undefined {
  const tokenSet = readTokenSet()
  return jwtSubject(tokenSet?.idToken) ?? jwtSubject(tokenSet?.accessToken)
}

export async function startLogin(): Promise<void> {
  const { clientId, scope } = configuration()
  if (!isOidcEnabled()) throw new Error('尚未配置 OIDC 登录参数')

  const state = randomBase64Url()
  const verifier = randomBase64Url()
  sessionStorage.setItem(STATE_STORAGE_KEY, state)
  sessionStorage.setItem(VERIFIER_STORAGE_KEY, verifier)

  const parameters = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: callbackUrl(),
    scope,
    state,
    code_challenge: await codeChallenge(verifier),
    code_challenge_method: 'S256'
  })
  window.location.assign(`${(await metadata()).authorization_endpoint}?${parameters}`)
}

export async function finishLoginCallback(): Promise<void> {
  if (!isOidcEnabled()) return
  const parameters = new URLSearchParams(window.location.search)
  const providerError = parameters.get('error')
  if (providerError) {
    throw new Error(parameters.get('error_description') || `OIDC 登录失败：${providerError}`)
  }

  const code = parameters.get('code')
  if (!code) return
  const state = parameters.get('state')
  const expectedState = sessionStorage.getItem(STATE_STORAGE_KEY)
  const verifier = sessionStorage.getItem(VERIFIER_STORAGE_KEY)
  sessionStorage.removeItem(STATE_STORAGE_KEY)
  sessionStorage.removeItem(VERIFIER_STORAGE_KEY)
  if (!state || !expectedState || state !== expectedState || !verifier) {
    throw new Error('OIDC 登录回调校验失败，请重新登录')
  }

  const { clientId } = configuration()
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: clientId,
    code,
    redirect_uri: callbackUrl(),
    code_verifier: verifier
  })
  const response = await fetch((await metadata()).token_endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  })
  if (!response.ok) throw new Error(`OIDC 令牌兑换失败（HTTP ${response.status}）`)
  const result = await response.json() as { access_token?: unknown, id_token?: unknown, expires_in?: unknown }
  if (typeof result.access_token !== 'string' || !result.access_token) throw new Error('OIDC 未返回 access_token')
  const expiresIn = typeof result.expires_in === 'number' && result.expires_in > 0 ? result.expires_in : 300
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify({
    accessToken: result.access_token,
    idToken: typeof result.id_token === 'string' ? result.id_token : undefined,
    expiresAt: Date.now() + expiresIn * 1000
  } satisfies TokenSet))
  window.history.replaceState({}, document.title, `${window.location.pathname}${window.location.hash}`)
}

export async function logout(): Promise<void> {
  const tokenSet = readTokenSet()
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  sessionStorage.removeItem(STATE_STORAGE_KEY)
  sessionStorage.removeItem(VERIFIER_STORAGE_KEY)
  if (!isOidcEnabled()) return

  const endSessionEndpoint = (await metadata()).end_session_endpoint
  if (!endSessionEndpoint) {
    window.location.assign(callbackUrl())
    return
  }
  const { clientId } = configuration()
  const parameters = new URLSearchParams({
    client_id: clientId,
    post_logout_redirect_uri: callbackUrl()
  })
  if (tokenSet?.idToken) parameters.set('id_token_hint', tokenSet.idToken)
  window.location.assign(`${endSessionEndpoint}?${parameters}`)
}
