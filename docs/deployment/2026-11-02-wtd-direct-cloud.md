# 旺通达飞针机直传线上 QMS 部署记录

## 线上现状

- 服务器：`106.227.69.144`，QMS Docker 容器名 `qms`，程序 `/data/qms/qms-api.jar`，配置 `/data/qms/application.yml`。持久上传目录是系统盘 `/var/lib/qms/uploads`，通过 `/data/1panel/runtime/java/qms/docker-compose.yml` 挂载为容器内 `/app/uploads`；重建容器后已验证健康检查 200 和目录可写。
- 公网 5173 通过 [qms-5173-stream.conf](qms-5173-stream.conf) 区分 HTTP 和 TLS。原 HTTP 页面由 [qms-http.conf](qms-http.conf) 在本机 5174 提供；完整 QMS 页面、API 和设备 HTTPS 由 [qms-device-https-internal.conf](qms-device-https-internal.conf) 在本机 5175 提供。代理使用 PROXY 协议恢复客户端 IP。
- 5173 的设备 TLS 配置为 [qms-device-https-internal.conf](qms-device-https-internal.conf)，线上文件位于 `/data/1panel/www/conf.d/qms-device-https-internal.conf`。独立 CA 签发的服务器证书位于宿主机 `/data/1panel/apps/openresty/openresty/conf/ssl/qms-device/server-new.crt`，容器内路径为 `/usr/local/openresty/nginx/conf/ssl/qms-device/server-new.crt`。服务器私钥原件位于 `/root/qms-device-tls/server-new.key`（权限 600），容器内受限副本位于 `/etc/qms-device/server-new.key`；容器重建后需从原件重新 `docker cp` 并恢复受限权限。
- CA 公用证书位于服务器 `/root/qms-device-tls/ca.crt`，随 XP 包以 `qms-upload-ca.crt` 分发，设备以它验证服务器证书。CA 私钥仅保留在本地 Mac，绝不提交仓库或上传服务器；服务器只持有自己的私钥及公用证书。
- 443 的独立设备入口 [qms-flying-probe-https.conf](qms-flying-probe-https.conf) 仍可供本机诊断；公网 443 未转发，不用于设备配置。原 443 默认拒绝配置备份在 `/data/1panel/apps/openresty/openresty/conf/default/00.default.conf.bak-wtd-20261102`。
- 从公网验证 `http://106.227.69.144:5173/` 返回 200、`https://106.227.69.144:5173/api/v1/flying-probe/device-files` 在无令牌时返回 401 且证书校验通过、明文 HTTP 上传返回 426，原登录接口返回 200，客户端 IP 记录正常。HTTP 配置备份在 `/data/1panel/www/conf.d/qms.conf.bak-before-mux-20261102`。
- 上线前程序、配置和 MySQL `qms` 库的备份位于 `/root/qms-backups/wtd-direct-cloud-20261102/`，目录权限 700、文件权限 600；数据库转储已通过 `gzip -t`。`/data` 是不支持 Unix 权限位的 `fuseblk` 挂载，不存放证书私钥或数据库备份。
- 原 QMS Compose 文件也备份在上述目录。1Panel 如果重新生成 Compose 或 OpenResty 容器，需要重新核对 `/var/lib/qms/uploads` 挂载和 `/etc/qms-device/server-new.key` 容器副本，防止新上传数据丢失或 HTTPS 启动失败。
- QMS 新 JAR 已部署，健康检查和页面返回 200，Flyway `V45` 成功；无令牌 HTTPS 设备请求与现有无令牌网关请求均返回 401。尚未签发任何设备上传令牌或切换 XP 设备。
- 当前线上 `qms` 库有 1 个租户、0 条设备台账和 0 条网关映射。设备台账将由用户手动新增；每台设备还需创建 `machineName=device_id` 的网关映射并签发上传令牌，否则上传无法关联设备。

## 上线顺序

1. 在实际 XP 设备确认 `https://106.227.69.144:5173` 用包内 CA 公用证书 `qms-upload-ca.crt` 验证成功。服务器叶证书到期日为 2028-11-01，应提前由同一 CA 续签并部署；CA 未变时无需修改设备信任文件。CA 更换或到期前须先向设备分发新的 CA 公用证书。设备入口始终使用公网 5173，不能配置为未转发的 443。
2. 构建并测试 QMS 后端，替换线上 jar 后重启 `qms`，核对 Flyway `V45`、健康检查、原有页面和 API。设备接口只接收 `/api/v1/flying-probe/device-files` 与 `/api/v1/flying-probe/device-host-status`，最大原文件 8 MiB。
3. 按下方“管理员配置设备”步骤建立台账和映射，并在 HTTPS 管理页面签发该设备上传令牌。不要把令牌放入命令行、日志或共享文件。
4. 在 Windows 32 位构建机使用 `ini_share_sync/build_exe_xp32.ps1` 生成无窗口 XP 包，构建或下载产物后，将独立 ZIP 和 XP 计划任务命令交付到用户本地，由用户安装到 XP 设备；不向 SCBGX 复制安装包或更改其文件。包内不得含真实配置、令牌或同步状态。
5. 先选一台设备，备份原安装目录和 UNC 配置，核对 `device_id` 与 QMS 映射一致，然后配置 `server_url=https://106.227.69.144:5173`、专用令牌及 `ca_cert=qms-upload-ca.crt`。保留原 `sync_state.json`；新上报只写 `cloud_sync_state.json`。
6. 在 XP 实机验证 TLS、文件任务（5 分钟）、状态任务（1 分钟）均无窗口运行；核对线上原始 INI、设备遥测、FQC 和最新电脑状态。重复提交同一文件不应产生第二条记录，旧状态晚到不应覆盖新状态。
7. 首台通过后逐台切换；全部确认后停用 SCBGX 对应文件采集/状态转发及本地飞针投影任务，线上保持单一投影调度器。

## 管理员配置设备

1. 从已提交的 XP 客户端仓库或本地可信安装包取得 CA 公用证书 `qms-upload-ca.crt`，导入管理电脑的受信任根证书存储，再打开 `https://106.227.69.144:5173`。不要从明文 HTTP QMS 页面下载信任证书，也不要跳过浏览器证书警告。HTTP 与 HTTPS 的登录存储按来源隔离，切换到 HTTPS 后需要重新登录；使用租户管理员或平台主管理员账号。
2. 在“设备管理 → 设备台账”新增现场设备。需要出现在成品测试监控卡片上的设备，类型选“成品测试设备”。
3. 在“设备管理 → 设备网关映射”新增映射，选择刚建的台账设备，来源名称填写设备配置中完全对应的 `device_id`（即 `machineName=device_id`）。
4. 在该映射行点击“签发 / 轮换令牌”，确认后复制仅显示一次的令牌，写入该设备 `sync_config.json` 的 `upload_token`。同时设置 `device_id`、`server_url=https://106.227.69.144:5173` 和 `ca_cert=qms-upload-ca.crt`。关闭令牌对话框后无法再次查看明文；HTTP 页面会禁用签发与撤销按钮并给出 HTTPS 链接。
5. 需要停用上传时，在映射行点击“撤销令牌”。轮换令牌、修改映射目标设备或来源名称都会使旧令牌失效，须将新令牌更新到设备配置。对应 API 为 `POST` / `DELETE /api/v1/equipment/gateway-mappings/{mappingId}/upload-token`，仅管理员可调用。
6. 设备成功上报后，在“设备管理 → 设备数据接入”的“采集电脑状态”表查看心跳和资源指标；该页默认每 5 秒读取。成品测试监控卡片每 30 秒读取电脑状态，显示最后心跳、CPU、内存、系统盘和开机时长，按映射后的设备 ID 关联。存在电脑心跳时以它判断连接状态；尚无心跳的旧设备沿用遥测运行状态。空闲且 INI 未变化时，持续心跳仍会保持在线。

前端 API 默认使用相对地址，HTTPS 页面上的登录和令牌请求也保持 HTTPS。不要为管理员构建设置明文 HTTP 的 `VITE_API_BASE_URL`；令牌 API 调用会在发送前拒绝明文页面或明文 API 地址，HTTP 代理也会对令牌路径返回 426。

首次云端运行上传扫描范围内的全部历史 INI，旧 UNC 的成功记录不会阻止首次补传；后续依靠独立的 `cloud_sync_state.json` 跳过未变化文件。首次切换应预留补传时间、上行带宽和 `/var/lib/qms/uploads` 存储空间，并在补传完成后核对数量与重试结果。

## 证书与入口核对

在服务器上核对证书链和有效期；CA 私钥不参与这些检查：

```sh
openssl verify -CAfile /root/qms-device-tls/ca.crt /data/1panel/apps/openresty/openresty/conf/ssl/qms-device/server-new.crt
openssl x509 -in /root/qms-device-tls/ca.crt -noout -dates
openssl x509 -in /data/1panel/apps/openresty/openresty/conf/ssl/qms-device/server-new.crt -noout -dates -ext subjectAltName
```

服务器证书的 SAN 必须包含 `IP Address:106.227.69.144`。从具有包内 CA 文件的公网机器检查两个协议共用 5173；以下请求依次应返回 200、401、426：

```sh
curl -sS -o /dev/null -w '%{http_code}\n' http://106.227.69.144:5173/
curl --cacert qms-upload-ca.crt -sS -o /dev/null -w '%{http_code}\n' -X POST https://106.227.69.144:5173/api/v1/flying-probe/device-files
curl -sS -o /dev/null -w '%{http_code}\n' -X POST http://106.227.69.144:5173/api/v1/flying-probe/device-files
```

5173 的 stream 配置必须保留 `ssl_preread on` 和 `proxy_protocol on`，5174/5175 两个本机监听也必须保留 `proxy_protocol`，使旧 HTTP 页面和设备 HTTPS 同时可用。独立 443 配置只用于本机诊断，不需要新增公网 443 转发。

## 回滚

设备端恢复备份的 UNC 配置及旧 EXE、计划任务；恢复 SCBGX 采集/转发任务。保留源 INI、共享 TXT、`sync_state.json`、`cloud_sync_state.json` 和已上传的原始文件。线上程序可用备份 jar 恢复；Flyway `V45` 仅新增字段并提高状态时间精度，旧程序可暂时带着该迁移运行。数据库备份仅供故障恢复，恢复整库会丢失备份后业务数据，不能作为常规回滚步骤。

5173 代理回滚：移除 `/data/1panel/www/stream.d/qms-5173-stream.conf` 和 `/data/1panel/www/conf.d/qms-device-https-internal.conf`，用 `qms.conf.bak-before-mux-20261102` 恢复 `/data/1panel/www/conf.d/qms.conf`，执行 `docker exec 1Panel-openresty-dAWa nginx -t` 和 `nginx -s reload`，核对原 HTTP 页面返回 200。

本次 SSH 私钥曾通过聊天传输；部署完成后应从服务器 `authorized_keys` 撤销对应公钥并更换密钥。
