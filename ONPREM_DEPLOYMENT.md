# PCB QMS 客户本地部署指南

此部署包只服务**一家**客户。客户 A 与客户 B 必须使用不同的服务器目录、数据库卷、登录密钥和网关密钥；不要将多租户数据库直接复制为双方共用的本地实例。

## 部署前条件

- Linux 服务器已安装 Docker Engine 与 Docker Compose v2。
- 推荐基线：8 核 CPU、32 GB 内存、1 TB SSD；OCR 图片量较大时增加 CPU 或单独部署 OCR。
- 已准备客户内部 DNS、HTTPS 反向代理和每日数据库备份位置。
- 已确定首位 QMS 管理员用户名、至少 10 位的初始密码，以及本客户独立的租户编码和 Base64 编码的 JWT 密钥（解码后至少 32 字节）。
- 客户 B 另有内网同步网关；QMS 服务器不连接客户 ERP 数据库。

## 首次部署

```bash
cp .env.onprem.example .env.onprem
# 编辑 .env.onprem，替换所有密码、JWT 密钥、网关密钥和首管理员信息
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d --build
docker compose --env-file .env.onprem -f docker-compose.onprem.yml ps
```

如果收到的是 `qms-onprem-*.zip`，先解压并进入 `qms-onprem` 目录再执行以上命令。部署包包含源码和构建文件；目标服务器首次构建需要访问 Maven、npm、PyPI 和 Docker 镜像仓库。部署包不包含数据库、附件或任何现场密钥。

系统会在 API 启动时执行 Flyway 数据库迁移。首次初始化 MySQL 卷时，容器会创建 `QMS_DB_USERNAME` 应用账号并仅授予 `MYSQL_DATABASE` 的访问权限；不要将 `root` 密码交给 API。浏览器入口默认为 `http://服务器地址:8080`；生产环境应由客户的反向代理提供 HTTPS，并将外部访问限制为该入口。MySQL、OCR 和 API 不对外发布端口。

> 不要把 `MYSQL_ROOT_PASSWORD` 对应的 `root` 账号用于 `QMS_DB_USERNAME`。QMS 依赖服务层的 `tenant_code` 隔离，因此必须限制应用账号只访问 QMS 数据库。

## 身份与权限开通

1. 此部署文件默认启用 QMS 本地账号密码登录，由 API 签发带 `tenant_code` 的 JWT。首次启动会创建租户和管理员；已有管理员密码不会被初始化参数覆盖。使用首位管理员登录后，在“用户与工序权限”中创建其他用户并分配角色与工序。
2. 客户 B 服务账号的令牌必须具有本客户的 `tenant_code`，并在网关上配置相同的 `QMS_GATEWAY_SHARED_TOKEN`。
3. 生产模式下不会使用 `X-Tenant-Code` 或 `X-QMS-Actor` 请求头模拟身份。

首次初始化成功后，可从部署编排中移除 `QMS_BOOTSTRAP_*` 环境变量，避免重启时再次设置账号角色和状态。默认登录令牌有效期为 480 分钟，由 `QMS_LOCAL_LOGIN_TOKEN_TTL_MINUTES` 配置。除飞针机遥测采集 POST 外，服务网关仍须提供有效的本租户 Bearer Token 和匹配的 `X-Gateway-Token`。

## 飞针机遥测网关认证

在 QMS 服务器的私有 `.env.onprem` 中设置已有的 `QMS_GATEWAY_SHARED_TOKEN`，并将 `QMS_GATEWAY_TELEMETRY_TENANT_CODE` 设为飞针机设备所属的、状态为 ACTIVE 的租户编码。两者缺一时，**无 Bearer** 的 `POST /api/v1/equipment/telemetry` 不会写库。飞针机采集器使用相同的网关令牌，Bearer Token 留空；无需更换采集器程序或清空本地待发送队列。不要在工单、日志或版本库中贴出真实令牌。

网关模式只适用于该 POST；遥测查询和其他 QMS 接口仍需原有身份认证。网关请求的租户由服务端配置决定，请求头及事件正文中的租户值不会改变它。由于当前现场链路使用 HTTP，须把 8080 的入站来源限制为采集电脑和受控内网，并安排 HTTPS。Docker 部署应在反向代理或防火墙上实施来源限制。

升级时先备份数据库、部署目录和附件，保留真实 `.env.onprem`，在其中**新增**租户配置，再更新 QMS 后端并重启。不要用示例文件覆盖现有配置。先检查 `/actuator/health`，再观察采集器一条已有待发送记录：HTTP 返回 2xx、同步记录显示成功、待发送数下降，并在指定租户的 QMS 遥测记录中核对 `recordNo`。若失败，保留采集器队列，恢复旧 QMS 程序与配置。既有 `recordNo` 用于重复上报去重。

## 上线核验

- 使用首位管理员登录，确认账号、角色与工序权限符合预期；不同客户使用独立部署和独立数据卷。
- 从现场手机扫码并离线补传一条检验记录；确认超限数值自动判为不合格。
- 客户 A 上传 ERP 截图并人工确认；客户 B 运行网关后确认工单同步及重叠窗口重试。
- 导出日期范围质量报表，核对工序、缺陷排行和趋势。
- 执行并演练一次 MySQL 卷与附件卷的备份恢复。

## 日常运维

```bash
docker compose --env-file .env.onprem -f docker-compose.onprem.yml logs -f api
docker compose --env-file .env.onprem -f docker-compose.onprem.yml logs --since 1h web ocr mysql
docker compose --env-file .env.onprem -f docker-compose.onprem.yml exec api sh -c 'grep "rid:请求编号" /app/logs/qms-api.log'
docker compose --env-file .env.onprem -f docker-compose.onprem.yml pull
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d --build
```

API 返回 `X-Request-ID`，浏览器请求和 Nginx 访问日志使用同一个编号；API 日志行中的 `rid:` 可用于串联异常堆栈与请求状态、耗时。请求日志只记录方法、路径、状态码和耗时，不记录查询参数、令牌和请求体。`/actuator/health` 不写逐请求日志。API 文件日志保存在 `api-logs` 卷，按 20 MB、14 天和 1 GB 总量轮转；`.env.onprem` 可调整 `QMS_LOG_*`，临时将 `QMS_LOG_LEVEL` 设为 `DEBUG` 后需重新创建 API 容器。MySQL、OCR、Web 和 API 的容器日志由 Docker `local` 驱动各保留至多 5 个 20 MB 文件，可用 `docker compose logs` 查看。

## 迁移现有数据

在旧服务器上停写后，分别备份数据库和附件；将备份文件与部署包安全传到新服务器。以下命令在解压后的 `qms-onprem` 目录执行，`backups` 仅作迁移中转，不在部署包内：

```bash
mkdir -p backups
docker compose --env-file .env.onprem -f docker-compose.onprem.yml exec -T mysql sh -c 'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --routines --triggers "$MYSQL_DATABASE"' > backups/qms.sql
docker compose --env-file .env.onprem -f docker-compose.onprem.yml run --rm --no-deps -v "$PWD/backups:/backup" --entrypoint sh api -c 'tar -C /app/uploads -czf /backup/attachments.tar.gz .'
```

在新服务器上填写与该客户一致的 `.env.onprem`，先启动 MySQL 并等待其健康检查通过，再恢复数据和附件：

```bash
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d mysql
docker compose --env-file .env.onprem -f docker-compose.onprem.yml exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < backups/qms.sql
docker compose --env-file .env.onprem -f docker-compose.onprem.yml run --rm --no-deps -v "$PWD/backups:/backup:ro" --entrypoint sh api -c 'tar -C /app/uploads -xzf /backup/attachments.tar.gz'
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d --build
```

恢复前应核对 `.env.onprem` 的租户编码、认证密钥、数据库名与原部署一致。升级前先备份数据库和附件，在维护窗口执行；应用数据库账号不得使用 MySQL `root`，且只应拥有 QMS schema 的权限。日志卷可单独迁移以保留旧服务器排障记录，但不影响业务数据恢复。
