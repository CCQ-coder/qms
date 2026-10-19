# QMS Windows 部署（云端 MySQL 8，无 Docker）

本包包含 QMS 后端、前端网页和独立 Java 运行时，通过 `QMS.exe` 启动。目标电脑不需要安装 Docker、Node.js、Maven 或 Java；本机已有的 SQL Server 不参与 QMS 数据存储。QMS 的业务数据继续存放在现有云端 MySQL 8。

先看 [RUN_WINDOWS.md](RUN_WINDOWS.md)：它分别列出首次安装、日常重启和升级命令。

## 迁移前准备

1. 备份云端 QMS MySQL 数据库，并确认其版本、连接地址、端口、数据库名和 QMS 应用账号。应用启动时默认执行 Flyway 数据库迁移；先备份并检查旧、新版本是否兼容。不要让旧、新实例长期同时运行定时任务。
2. 放通目标 Windows 电脑到云端 MySQL 的 TCP 连接；云服务器防火墙、安全组和 MySQL 用户授权需要允许这台电脑的出口 IP。用 `Test-NetConnection <数据库域名> -Port 3306` 检查网络，实际端口以配置为准。
3. 如果旧服务使用本地附件目录，另行复制旧目录里的文件到本包的 `data\uploads`，同时保留原有相对路径。数据库在云端，不代表附件也在云端。
4. 确认目标电脑是 64 位 Windows，且安装目录具有足够空间。压缩包应解压到固定目录，例如 `D:\QMS\qms-windows`；不要在完成开机启动设置后再移动目录。

## 配置与首次启动

1. **仅首次安装且 `.env.windows` 不存在时**，将 `.env.windows.example` 复制为 `.env.windows`。填写 `QMS_DB_URL`、`QMS_DB_USERNAME`、`QMS_DB_PASSWORD` 和 `QMS_LOCAL_LOGIN_JWT_SECRET`。已配置或升级时保留现有 `.env.windows`，不要用示例覆盖。示例中的占位符不能直接用于启动。PowerShell 可用以下命令生成 JWT 密钥：

   ```powershell
   $b=New-Object byte[] 32; $r=[Security.Cryptography.RandomNumberGenerator]::Create(); $r.GetBytes($b); $r.Dispose(); [Convert]::ToBase64String($b)
   ```

   请整行复制执行，把输出填在 `.env.windows` 的 `QMS_LOCAL_LOGIN_JWT_SECRET=` 后面。保留此密钥，后续升级继续使用。配置文件中的密码和密钥会保存在目标电脑上，请限制读取权限，不要放进共享目录或再次压缩上传。

2. JDBC 示例使用已验证可连接的 `sslMode=REQUIRED`：连接加密，但不校验服务器证书。若改用 `VERIFY_IDENTITY`，需将云数据库 CA 配置到 Java 信任库，并使用与证书匹配的域名。MySQL 账号仅授权 QMS 数据库所需权限，不要用 root 账号。
3. 示例默认在现有 `customer-b` 组织初始化本地账号 `admin`，初始密码为 `Admin@2026`。如果该账号已有密码，启动初始化不会覆盖密码；但只要保留 `QMS_BOOTSTRAP_*`，每次启动仍会将该账号设为启用的 `PLATFORM_ADMIN`。首次登录成功后，从 `.env.windows` 删除这三项，再重新启动。这个固定初始密码适合迁移测试，正式使用前应更换。
4. 首次安装时，在解压目录的 PowerShell 中运行：

   ```powershell
   if (-not (Test-Path -LiteralPath .\.env.windows)) {
       Copy-Item -LiteralPath .\.env.windows.example -Destination .\.env.windows
   }
   notepad .\.env.windows
   ```

   保存配置后双击 `一键启动QMS.cmd`。它直接读取 `.env.windows` 并运行 `QMS.exe`，无需执行未签名的 PowerShell 脚本。配置项请按示例写成 `KEY=value`，等号两侧不加空格，值不加引号。在允许执行 PowerShell 脚本的电脑上，也可运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-qms-windows.ps1`。以后的日常重启只需双击该文件，不要重新复制示例配置。启动时会创建 `logs\qms-api.log` 和 `data\uploads`。启动成功后，在本机打开 `http://127.0.0.1:8080/#/login`，在局域网其他电脑打开 `http://<服务器 IP>:8080/#/login`。如果修改了 `SERVER_PORT`，地址也应使用对应端口。通过 `/actuator/health` 检查应用状态，然后用现有账号登录。

5. 如需局域网访问，在管理员 PowerShell 中按实际端口建立 Windows 防火墙入站规则。若跨不可信网络使用，请在前面配置 HTTPS 反向代理并限制访问来源，不要直接公开 HTTP 端口。

## 开机启动

首次手动启动确认成功后，用**管理员 PowerShell** 在解压目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install-autostart.ps1
Start-ScheduledTask -TaskName QMS-Windows
Get-ScheduledTaskInfo -TaskName QMS-Windows
```

任务在开机时以 Windows `SYSTEM` 账号运行，数据库密码不写入任务参数。进程异常退出后脚本每 30 秒重启一次。停止或移除：

```powershell
Stop-ScheduledTask -TaskName QMS-Windows
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install-autostart.ps1 -Uninstall
```

手动启动的程序停止后，可双击解压目录里的 `一键启动QMS.cmd`，也可在该目录重新运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-qms-windows.ps1
```

如果已安装并使用 `QMS-Windows` 开机任务，则用 `Start-ScheduledTask -TaskName QMS-Windows` 启动任务，不要同时手动运行另一份。启动脚本每次都会读取当前 `.env.windows`；删除 `QMS_BOOTSTRAP_*` 后，后续启动不会再次执行账号初始化。

升级时先停止任务和旧进程，备份 `.env.windows`、`data\uploads` 和数据库，然后解压新版到固定目录，放回配置与附件，再启动。不要用新压缩包直接覆盖仍在运行的 Java 文件。

## 日志与排查

- 应用日志在 `logs\qms-api.log`，按 20 MB 滚动、保留 14 天、总量上限 1 GB；可在 `.env.windows` 调整 `QMS_LOG_*`。
- 每个 HTTP 请求会记录方法、路径、状态码、耗时，并在响应头返回 `X-Request-ID`。日志行包含相同的 `rid`，便于用一次请求定位错误；查询参数、请求体和授权头不会写入请求日志。
- 查看最近日志：`Get-Content .\logs\qms-api.log -Tail 200`。实时跟踪：`Get-Content .\logs\qms-api.log -Tail 50 -Wait`。按请求 ID 查找：`Select-String -Path .\logs\qms-api*.log -Pattern '<请求 ID>'`。
- 无法启动时先直接运行 `start-qms-windows.ps1` 查看屏幕错误，再检查 `logs\qms-api.log`。云数据库连接故障可检查 DNS、出站端口、云端 IP 白名单、MySQL 用户授权和 TLS 证书；不要在工单中粘贴 `.env.windows` 密码或完整连接串。

## 打包与校验（开发电脑）

在 Windows 开发电脑执行 `python scripts\package_windows.py`。构建需要 Node.js/npm、Maven 和 JDK 21 的 `jpackage`；输出为 `outputs\qms-windows-*.zip`。压缩包根目录内的 `MANIFEST.sha256.json` 列出每个文件的 SHA-256。实际 `.env.windows`、日志和附件不会进入压缩包。
