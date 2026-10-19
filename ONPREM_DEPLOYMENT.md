# PCB QMS 客户本地部署指南

系统使用 QMS 自管账号密码登录，由后端签发 JWT。每家客户使用独立的部署目录、数据库卷、附件卷、租户编码和密钥。

## 部署前条件

- 准备 Docker Engine、Docker Compose、客户域名和 HTTPS 反向代理。
- 为数据库和附件准备每日备份位置，并安排恢复演练。
- 确定首个租户编码、组织名称、管理员用户名及初始密码。
- 生成 Base64 编码的随机 JWT 密钥，解码后至少 32 字节；可用 `openssl rand -base64 32`。不同客户使用不同密钥。
- ERP 或设备网关按对应文档独立部署，配置本客户的身份和网关共享密钥。

## 首次部署

```bash
cp .env.onprem.example .env.onprem
# 填写数据库、JWT、网关密钥，以及首租户和管理员参数
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d --build
docker compose --env-file .env.onprem -f docker-compose.onprem.yml ps
```

`docker-compose.onprem.yml` 已启用账号密码登录和 JWT 验证。后端启动时执行 Flyway 迁移；数据库应用账号只用于 QMS schema，不使用 root。

浏览器入口默认 `http://服务器地址:8080`，正式使用应由反向代理提供 HTTPS。配置后端允许的页面来源 `QMS_WEB_CORS_ALLOWEDORIGINS`，或正确传递同源请求的主机信息；来源需包含实际协议、主机和端口。MySQL、OCR 和 API 不直接发布到外部。

## 账号和权限

1. 初始化参数为 `QMS_BOOTSTRAP_TENANT_CODE`、`QMS_BOOTSTRAP_TENANT_NAME`、`QMS_BOOTSTRAP_ADMIN_USERNAME` 和 `QMS_BOOTSTRAP_ADMIN_PASSWORD`。密码至少 10 位，建议使用独立随机值。
2. 初始化器会创建租户和管理员，并将指定账号设为启用的平台管理员；仅在该账号没有密码哈希时写入初始密码。已有账号的密码不会被此参数重置。
3. 首次初始化成功后，可从部署编排中移除四项初始化环境变量，避免后续启动再次设置该账号角色和状态。
4. 登录后在用户管理中配置账号、角色和工序；不同客户使用不同 tenant code。
5. 默认登录令牌有效期为 480 分钟，由 `QMS_LOCAL_LOGIN_TOKEN_TTL_MINUTES` 配置；过期后重新登录。
6. JWT 认证模式下，`X-Tenant-Code`、`X-QMS-Actor` 不用于模拟登录身份。除遥测采集 POST 外，服务网关仍须提供有效的本租户 Bearer Token 和匹配的 `X-Gateway-Token`。

## 飞针机遥测网关认证

在 QMS 服务器的私有 `.env.onprem` 中设置已有的 `QMS_GATEWAY_SHARED_TOKEN`，并将 `QMS_GATEWAY_TELEMETRY_TENANT_CODE` 设为飞针机设备所属的、状态为 ACTIVE 的租户编码。两者缺一时，**无 Bearer** 的 `POST /api/v1/equipment/telemetry` 不会写库。飞针机采集器使用相同的网关令牌，Bearer Token 留空；无需更换采集器程序或清空本地待发送队列。不要在工单、日志或版本库中贴出真实令牌。

网关模式只适用于该 POST；遥测查询和其他 QMS 接口仍需原有身份认证。网关请求的租户由服务端配置决定，请求头及事件正文中的租户值不会改变它。由于当前现场链路使用 HTTP，须把 8080 的入站来源限制为采集电脑和受控内网，并安排 HTTPS。Docker 部署应在反向代理或防火墙上实施来源限制。

升级时先备份数据库、部署目录和附件，保留真实 `.env.onprem`，在其中**新增**租户配置，再更新 QMS 后端并重启。不要用示例文件覆盖现有配置。先检查 `/actuator/health`，再观察采集器一条已有待发送记录：HTTP 返回 2xx、同步记录显示成功、待发送数下降，并在指定租户的 QMS 遥测记录中核对 `recordNo`。若失败，保留采集器队列，恢复旧 QMS 程序与配置。既有 `recordNo` 用于重复上报去重。

## 上线核验

- 原账号密码登录、错误密码、退出和过期后重新登录。
- 两个租户及不同角色之间的工单、附件、检验和报表权限隔离。
- 带真实页面 Origin 的登录和 API 请求；跨域客户端还要验证 OPTIONS 预检。
- 质量业务流程、离线补传、OCR 人工确认、现场网关及报表导出，按功能进度表逐项验收。
- MySQL 与附件卷都进行备份恢复演练；SQL 备份不含附件文件。

## 日常运维

```bash
docker compose --env-file .env.onprem -f docker-compose.onprem.yml logs -f api
docker compose --env-file .env.onprem -f docker-compose.onprem.yml up -d --build
```

升级前备份数据库和附件，在维护窗口执行。当前 Mac 开发环境使用 `compose.local.yaml` / `dev.sh`，端口为 5174、8082、3307；不要与本文件的独立客户部署命令混用。
