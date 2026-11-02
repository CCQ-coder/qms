# 旺通达飞针机直传线上 QMS 部署记录

## 线上现状

- 服务器：`106.227.69.144`，QMS Docker 容器名 `qms`，程序 `/data/qms/qms-api.jar`，配置 `/data/qms/application.yml`，持久上传目录 `/data/qms/uploads`（容器内 `/app/uploads`）。
- OpenResty 的 HTTP 5173 入口从 `/data/1panel/www/conf.d/qms.conf` 反向代理 `/api/` 到本机 8080。
- 设备 HTTPS 入口配置为 [qms-flying-probe-https.conf](qms-flying-probe-https.conf)；线上文件位于 `/data/1panel/www/conf.d/qms-flying-probe-https.conf`。私钥只在服务器 `/data/1panel/apps/openresty/openresty/conf/ssl/qms-device/server.key`，公用证书随 XP 包分发。
- 原 443 默认拒绝配置备份在 `/data/1panel/apps/openresty/openresty/conf/default/00.default.conf.bak-wtd-20261102`。HTTPS 本机 TLS 1.2、证书校验及 QMS 代理已通过；公网 443 尚需转发到服务器内网 `192.168.0.3:443`，再从设备侧验证。
- HTTP 5173 已阻止两个设备上传路径（返回 426），配置备份在 `/data/1panel/www/conf.d/qms.conf.bak-wtd-20261102`，当前配置样本为 [qms-http.conf](qms-http.conf)。原有 QMS 页面仍返回 200。
- 上线前程序、配置和 MySQL `qms` 库的备份位于 `/data/qms/backups/wtd-direct-cloud-20261102/`；数据库转储已通过 `gzip -t`。
- QMS 新 JAR 已部署，健康检查和页面返回 200，Flyway `V45` 成功；无令牌 HTTPS 设备请求与现有无令牌网关请求均返回 401。尚未签发任何设备上传令牌或切换 XP 设备。

## 上线顺序

1. 确认公网 TCP 443 能连通且用 XP 包内 `qms-server.crt` 验证成功；证书到期日为 2028-11-01，需在到期前更新设备信任证书。
2. 构建并测试 QMS 后端，替换线上 jar 后重启 `qms`，核对 Flyway `V45`、健康检查、原有页面和 API。设备接口只接收 `/api/v1/flying-probe/device-files` 与 `/api/v1/flying-probe/device-host-status`，最大原文件 8 MiB。
3. 由 QMS 租户管理员对每台设备已有的 `machineName` 映射调用 `POST /api/v1/equipment/gateway-mappings/{mappingId}/upload-token`；只显示一次的 `uploadToken` 写入该设备 `sync_config.json`。重新签发会立即使旧令牌失效；撤销用同一路径的 `DELETE`。不要把令牌放入命令行、日志或共享文件。
4. 在 Windows 32 位构建机使用 `ini_share_sync/build_exe_xp32.ps1` 生成无窗口 XP 包，复制到 SCBGX 的 `E:\软件\飞针机-设备端` 供现场更新；包内不得含真实配置、令牌或同步状态。
5. 先选一台设备，备份原安装目录和 UNC 配置，核对 `device_id` 与 QMS 映射一致，然后配置 `server_url=https://106.227.69.144`、专用令牌及 `ca_cert=qms-server.crt`。保留原 `sync_state.json`；新上报只写 `cloud_sync_state.json`。
6. 在 XP 实机验证 TLS、文件任务（5 分钟）、状态任务（1 分钟）均无窗口运行；核对线上原始 INI、设备遥测、FQC 和最新电脑状态。重复提交同一文件不应产生第二条记录，旧状态晚到不应覆盖新状态。
7. 首台通过后逐台切换；全部确认后停用 SCBGX 对应文件采集/状态转发及本地飞针投影任务，线上保持单一投影调度器。

## 回滚

设备端恢复备份的 UNC 配置及旧 EXE、计划任务；恢复 SCBGX 采集/转发任务。保留源 INI、共享 TXT、`sync_state.json`、`cloud_sync_state.json` 和已上传的原始文件。线上程序可用备份 jar 恢复；Flyway `V45` 仅新增字段并提高状态时间精度，旧程序可暂时带着该迁移运行。数据库备份仅供故障恢复，恢复整库会丢失备份后业务数据，不能作为常规回滚步骤。

本次 SSH 私钥曾通过聊天传输；部署完成后应从服务器 `authorized_keys` 撤销对应公钥并更换密钥。
