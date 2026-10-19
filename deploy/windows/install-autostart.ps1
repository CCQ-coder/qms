param([switch]$Uninstall)

$ErrorActionPreference = 'Stop'
$taskName = 'QMS-Windows'
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

if ($Uninstall) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
    Write-Host "已移除开机启动任务 $taskName。"
    exit 0
}

$startScript = Join-Path $PSScriptRoot 'start-qms-windows.ps1'
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env.windows') -PathType Leaf)) {
    throw '请先创建 .env.windows，再安装开机启动任务。'
}
$arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $startScript + '" -Service'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Seconds 0) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "已安装开机启动任务 $taskName。"
