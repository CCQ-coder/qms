param(
  [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSCommandPath
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'
$backendHealthUrl = 'http://127.0.0.1:8080/actuator/health'
$frontendUrl = 'http://127.0.0.1:5173/'
$localConfigPath = Join-Path $projectRoot '.env.secure.local'

if (Test-Path -LiteralPath $localConfigPath) {
  foreach ($line in Get-Content -LiteralPath $localConfigPath) {
    $trimmed = $line.Trim()
    if (!$trimmed -or $trimmed.StartsWith('#')) {
      continue
    }

    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) {
      throw "Invalid configuration line in $localConfigPath"
    }

    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1)
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
      throw "Invalid configuration name in $localConfigPath"
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }
}

$requiredEnvironmentVariables = @(
  'QMS_LOCAL_LOGIN_JWT_SECRET',
  'QMS_BOOTSTRAP_TENANT_CODE',
  'QMS_BOOTSTRAP_TENANT_NAME',
  'QMS_BOOTSTRAP_ADMIN_USERNAME',
  'QMS_BOOTSTRAP_ADMIN_PASSWORD'
)

foreach ($variableName in $requiredEnvironmentVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variableName))) {
    throw "Set $variableName before starting the secure QMS environment."
  }
}

$env:QMS_SECURITY_DEVELOPMENT_MODE = 'false'
$env:QMS_LOCAL_PASSWORD_LOGIN_ENABLED = 'true'
$env:QMS_WEB_CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173,http://192.168.1.71:5173,http://tauri.localhost'
Remove-Item Env:QMS_OIDC_ISSUER_URI -ErrorAction SilentlyContinue

function Test-HttpAvailable([string]$url) {
  try {
    return (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 $url).StatusCode -ge 200
  } catch {
    return $false
  }
}

function Wait-ForHttp([string]$url, [string]$name) {
  for ($attempt = 1; $attempt -le 30; $attempt++) {
    if (Test-HttpAvailable $url) {
      Write-Host "$name is ready: $url" -ForegroundColor Green
      return
    }
    Start-Sleep -Seconds 1
  }
  throw "$name timed out. Check the matching run-qms-*.log file."
}

if (Test-HttpAvailable $backendHealthUrl) {
  throw "A backend is already listening at $backendHealthUrl. Stop it before starting the secure environment."
} else {
  Start-Process -FilePath 'D:\maven\bin\mvn.cmd' `
    -ArgumentList @('-Dmaven.repo.local=D:/project/qms/.m2/repository', 'spring-boot:run') `
    -WorkingDirectory $backendDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $backendDirectory 'run-qms-backend.out.log') `
    -RedirectStandardError (Join-Path $backendDirectory 'run-qms-backend.err.log')
  Write-Host 'Starting backend...'
}

if (Test-HttpAvailable $frontendUrl) {
  throw "A frontend is already listening at $frontendUrl. Stop it before starting the secure environment."
} else {
  Start-Process -FilePath 'npm.cmd' `
    -ArgumentList @('run', 'dev', '--', '--host', '0.0.0.0') `
    -WorkingDirectory $frontendDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $frontendDirectory 'run-qms-frontend.out.log') `
    -RedirectStandardError (Join-Path $frontendDirectory 'run-qms-frontend.err.log')
  Write-Host 'Starting frontend...'
}

Wait-ForHttp $backendHealthUrl 'Backend'
Wait-ForHttp $frontendUrl 'Frontend'

if ($OpenBrowser) {
  Start-Process $frontendUrl
}
