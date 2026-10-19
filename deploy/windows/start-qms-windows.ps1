param([switch]$Service)

$ErrorActionPreference = 'Stop'
$packageRoot = $PSScriptRoot
$configPath = Join-Path $packageRoot '.env.windows'
$launcherPath = Join-Path $packageRoot 'QMS.exe'
$jarPath = Join-Path $packageRoot 'app\qms-api.jar'

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "缺少配置文件 $configPath；先复制 .env.windows.example 并填写云端 MySQL 连接信息。"
}
if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf) -or -not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw '安装包不完整：缺少 QMS.exe 或 qms-api.jar。'
}

$lineNumber = 0
foreach ($line in [System.IO.File]::ReadAllLines($configPath)) {
    $lineNumber++
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
    if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        throw ".env.windows 第 $lineNumber 行格式错误；请使用 KEY=value。"
    }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[-1] -eq '"') -or
        ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($key, $value, 'Process')
}

foreach ($key in @('QMS_DB_URL', 'QMS_DB_USERNAME', 'QMS_DB_PASSWORD', 'QMS_LOCAL_LOGIN_JWT_SECRET')) {
    $value = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains('REPLACE_WITH') -or
        $value.Contains('CLOUD_MYSQL_HOST') -or $value -eq 'QMS_DB_USER') {
        throw "请在 .env.windows 中填写 $key。"
    }
}
try {
    if ([Convert]::FromBase64String($env:QMS_LOCAL_LOGIN_JWT_SECRET).Length -lt 32) {
        throw 'too short'
    }
} catch {
    throw 'QMS_LOCAL_LOGIN_JWT_SECRET 必须是至少 32 字节的 Base64 密钥。'
}

$env:QMS_SERVE_FRONTEND = 'true'
$env:QMS_SECURITY_DEVELOPMENT_MODE = 'false'
$env:QMS_LOCAL_PASSWORD_LOGIN_ENABLED = 'true'
$env:QMS_OCR_ENABLED = 'false'
$env:LOGGING_FILE_NAME = Join-Path $packageRoot 'logs\qms-api.log'
$env:QMS_ATTACHMENT_LOCAL_ROOT = Join-Path $packageRoot 'data\uploads'
New-Item -ItemType Directory -Path (Join-Path $packageRoot 'logs'), $env:QMS_ATTACHMENT_LOCAL_ROOT -Force | Out-Null

Push-Location $packageRoot
try {
    do {
        & $launcherPath
        $exitCode = $LASTEXITCODE
        if ($Service) {
            Write-Warning "QMS 进程已退出（代码 $exitCode）；30 秒后重启。请查看 logs\qms-api.log。"
            Start-Sleep -Seconds 30
        }
    } while ($Service)
    exit $exitCode
} finally {
    Pop-Location
}
