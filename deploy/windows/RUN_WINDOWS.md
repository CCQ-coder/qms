# QMS Windows 快速运行说明

本包自带 Java、后端和前端，无需 Docker。业务数据库使用云端 MySQL 8。以下命令均在 **PowerShell** 中执行；所有文件路径指向解压后包含 `start-qms-windows.ps1` 的 `qms-windows` 目录。

## 已经配置过：停止后重新启动

直接双击安装目录里的 **`一键启动QMS.cmd`**。它不调用 PowerShell 脚本，因此可用于禁止执行未签名 `.ps1` 的电脑。运行期间请保持弹出的窗口打开；如果启动失败，窗口会保留错误信息供查看。在允许执行 PowerShell 脚本的电脑上，也可以进入原来的安装目录，运行下面的命令：

```powershell
Set-Location -LiteralPath 'D:\QMS\qms-windows'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-qms-windows.ps1
```

把示例路径换成实际解压位置。如果当前 PowerShell 已在 `qms-windows` 目录，只需运行第二行。**不要再次执行 `Copy-Item .env.windows.example .env.windows`**；`.env.windows` 中保存了已经配置好的数据库密码和登录密钥。双击启动和命令启动都会读取该文件。双击启动要求配置项按示例写成 `KEY=value`，不要在等号两侧加空格，也不要用引号包住值。

如果已经安装 `QMS-Windows` 开机任务，用下面的命令启动，不要同时手动启动另一份：

```powershell
Start-ScheduledTask -TaskName QMS-Windows
```

如果旧版双击启动时提示 `start-qms-windows.ps1` **未签名**，将新版压缩包里的 `一键启动QMS.cmd` 覆盖到原安装目录的同名文件，然后重新双击。保留原目录的 `.env.windows` 和 `data\uploads`；无需修改电脑的 PowerShell 执行策略。如果仍看到同一条 `.ps1` 签名错误，检查是否双击了旧目录里的启动文件。

## 第一次安装到新目录

1. 解压 ZIP，进入包含 `QMS.exe` 和 `start-qms-windows.ps1` 的 `qms-windows` 目录。
2. **仅当 `.env.windows` 不存在时**，从示例生成一次配置文件：

   ```powershell
   if (-not (Test-Path -LiteralPath .\.env.windows)) {
       Copy-Item -LiteralPath .\.env.windows.example -Destination .\.env.windows
   }
   notepad .\.env.windows
   ```

3. 在记事本中填写 `QMS_DB_PASSWORD` 和 `QMS_LOCAL_LOGIN_JWT_SECRET`，并核对 `QMS_DB_URL`、`QMS_DB_USERNAME`。示例中的 `REPLACE_WITH_...` 不能直接使用。生成 32 字节 Base64 登录密钥的命令：

   ```powershell
   $b=New-Object byte[] 32; $r=[Security.Cryptography.RandomNumberGenerator]::Create(); $r.GetBytes($b); $r.Dispose(); [Convert]::ToBase64String($b)
   ```

   整行执行，把结果填在 `QMS_LOCAL_LOGIN_JWT_SECRET=` 后；以后升级沿用这个密钥。
4. 保存配置后，双击 **`一键启动QMS.cmd`**；也可以运行：

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-qms-windows.ps1
   ```

启动后打开 [本机登录页](http://127.0.0.1:8080/#/login)，选择 `customer-b`，使用账号 `admin`、初始密码 `Admin@2026`。示例配置会在首次启动时为现有 `customer-b` 初始化该账号；**已有账号的密码不会因启动而重置**。首次成功登录后，修改初始密码，并从真实的 `.env.windows` 中删除三行 `QMS_BOOTSTRAP_*` 配置；此后重启不会再执行账号初始化。当前已配置的安装目录无需重复初始化。

## 换用新版压缩包

先停止旧版 QMS（手动启动的窗口或 `Stop-ScheduledTask -TaskName QMS-Windows`），将新版 ZIP 解压到**新目录**。从旧目录复制真实的 `.env.windows` 和 `data\uploads` 到新目录的对应位置；不要用新版示例文件覆盖旧配置。日志 `logs` 可另行保留用于排查。若使用开机任务，按详细部署文档在新目录重新安装任务，使其指向新路径。确认旧进程已停止后，在新目录双击 `一键启动QMS.cmd`。不要同时运行两份占用 8080 端口的 QMS。

## 查看状态和日志

打开 `http://127.0.0.1:8080/actuator/health` 检查服务。日志位于解压目录的 `logs\qms-api.log`。查看最近 200 行：

```powershell
Get-Content .\logs\qms-api.log -Tail 200
```

更完整的网络、开机启动和故障排查说明见 [WINDOWS_DEPLOYMENT.md](WINDOWS_DEPLOYMENT.md)。
