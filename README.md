# PCB QMS

两家独立 PCB 客户的多租户质量管理系统。当前工程从工单/批次、扫码巡检、成品检验和不良处置主链路开始实现。

## 工程结构

```text
backend/       Java 21 + Spring Boot API
frontend/      Vue 3 + TypeScript + PWA
gateway/       客户 B 内网只读数据库同步网关
ocr-service/   客户 A ERP 工单截图 OCR 服务
docker-compose.yml
docker-compose.onprem.yml
QMS_V1_需求与技术架构方案.md
```

## 当前已实现的基础能力

- 多租户上下文：本地开发通过 `X-Tenant-Code` 请求头模拟，默认租户为 `demo-pcb`；生产前端使用 OIDC 授权码 + PKCE 登录并发送 Bearer Token。MyBatis 执行前会强制要求租户上下文，服务层的所有业务查询按 `tenant_code` 隔离数据。
- 工序权限：租户管理员和主管可查看全部检验任务；检验员仅能读取、扫码查看和提交被授权工序的数据。生产环境中未配置权限的 OIDC 用户会被拒绝。
- 用户权限维护：租户管理员可在“检验标准配置 → 用户与工序权限”中新增、停用和编辑用户角色与检验工序范围；产品、工序、检验模板、自动派单规则及租户质量参数也仅允许租户管理员修改。
- 工单与生产批次：主管/管理员可创建和维护；检验员只会查询到有授权检验任务的工单，扫码批次时只返回该批次、该工序的待检任务。客户 B 网关经专用受信接口同步，不依赖人工用户权限。
- 基础资料：每个客户独立维护产品、工序及启停状态；工单与 OCR 确认可直接选择并回填这些资料。
- 巡检任务：手动创建、待办查询、提交合格/不合格记录。
- 检验模板与规则：客户管理员可维护模板、检验项、录入类型、必填、上下限、单位和下拉选项；按产品/工序绑定派单规则。系统定时或人工按规则生成巡检、成检任务，并避免重复生成未完成任务。
- 动态现场表单：任务关联模板时，现场检验页自动加载相应检验项；填写结果、缺陷数量/说明/位置与最多 5 张检验照片随检验记录保存。
- 检验控制范围：模板中的必填项、数值上下限、下拉选项及是/否值会在服务端强制校验；数值超限时，现场页面自动切换为不合格，接口直连或离线补传也不能以合格结论绕过。
- 离线现场检验：断网提交时，表单与照片会按当前客户/用户暂存到浏览器 IndexedDB；网络恢复后自动补传。后端以客户端请求标识保证补传幂等，不会重复生成检验记录。
- 附件权限：检验照片访问会回溯到关联检验任务并校验工序权限；未关联照片仅上传者可查看，ERP 工单截图仅主管或租户管理员可查看。
- 不良品处置：通过租户参数 `quality.auto-create-ncr` 控制不合格记录是否自动创建不良单，默认关闭；关闭时，检验员提交不合格记录后仍可立即人工发起不良品处置单。
- 8D / CAPA：检测人记录团队、问题、遏制、根因、纠正与预防措施；主管验证并关闭。
- 设备：台账、点检/保养/维修/校准记录创建与完成；校准/检定 30 天内到期及维护计划逾期状态实时显示，并按工序限制检验员可见与可操作的设备。
- 客户 A：现场检测人员可上传、查看并确认本人 ERP 工单截图（仅限其授权工序）；主管/管理员可处理全部截图。来源文件保存和人工确认导入均已实现，OCR 服务接入点已预留。
- OCR 服务：独立 FastAPI + PaddleOCR 容器，识别结果自动预填但必须人工确认。
- 客户 B：独立网关使用只读 SQL 增量读取并主动推送云端 QMS，详见 `gateway/README.md`。
- CNC/锣机：现场网关负责 TCP 6100 和厂商私有协议；QMS 只接收标准化 JSON、保存状态/记录并生成报警、停机、加工完成等待处理业务事件。QMS 不监听设备 TCP 端口。
- MySQL 8 数据库迁移与演示数据（含 FQC 与阻焊示例模板）。
- 演示身份：`demo.admin`（管理员）、`demo.supervisor`（主管）、`demo.inspector`（仅 FQC 检验员）。
- 报表导出：质量汇总支持按检验日期筛选、质量趋势图及原生 Excel `.xlsx` 下载（含工序汇总、Top 10 缺陷排行和质量趋势），也保留 CSV 导出；汇总、导出和批次追溯均遵从当前用户的工序数据权限。
- 自动化验证：后端 `mvn verify` 包含 Excel 导出和租户基础资料创建的单元测试。
- Vue 工作台：质量概览、工单列表、扫码检验、检验标准配置、不良/CAPA、设备维护、质量汇总与生产批次追溯；业务页面按路由懒加载且仅注册实际使用的 UI 组件，减少现场终端的首次下载量。

## 本地启动

### 前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:5173`，Vite 会将 `/api` 代理到 `http://localhost:8080`。

### 后端

后端要求 Java 21。配置可从 `backend/src/main/resources/application.yml` 查看。

```powershell
cd backend
mvn spring-boot:run
```

本地需要可访问的 MySQL 8 实例。默认连接为
`jdbc:mysql://127.0.0.1:3306/qms?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`，账号/密码为 `qms` / `qms`。也可以直接使用下方 Docker 编排创建开发数据库。

### Docker 开发环境

```powershell
Copy-Item .env.example .env
# 编辑 .env，为数据库密码和网关密钥设置强随机值
docker compose up --build
```

访问：前端 `http://localhost:5173`，后端健康检查 `http://localhost:8080/actuator/health`。

开发环境中的 ERP 截图附件保存在 API 容器的 `uploads` 数据卷。正式部署时应将该本地适配器替换为客户自有的 S3/MinIO/NAS 存储适配器，并落实备份策略。

> `dev` 配置仅用于开发演示，会通过请求头模拟租户和用户。生产环境必须接入 JWT/OIDC、移除开发安全配置，并将数据库、对象存储和密钥改为受管服务。

生产模式已提供 JWT/OIDC Resource Server 骨架：访问令牌需要包含 `tenant_code` 与可选 `roles` 声明；QMS 仅在验签成功后才从令牌建立租户上下文，开发请求头不会生效。网关需使用客户 B 服务账号令牌，并且无论运行环境都必须配置相同的 `QMS_GATEWAY_SHARED_TOKEN`，否则同步入口会拒绝请求。启用生产模式时设置 `QMS_SECURITY_DEVELOPMENT_MODE=false` 与 `QMS_OIDC_ISSUER_URI`。MySQL 应用账号仅授予 QMS 数据库的必要权限，不应使用 `root` 账号；租户隔离由应用层的租户条件与 MyBatis 租户上下文守卫共同保障。

## CNC 网关接口

QMS 面向现场网关提供 HTTPS JSON 接口；不实现、不暴露也不需要了解设备原始 TCP 协议。网关必须提交 `X-Gateway-Token`。生产环境还必须携带现有的服务账号 Bearer Token，其中 `tenant_code` 必须是该客户的租户。

首次对接时，租户管理员先通过 `POST /api/v1/equipment/gateway-mappings` 将 QMS 设备台账映射到网关上报的 `username`（设备 `#Username`）和/或 `machineName`；标识在 QMS 中会规范化为大写，并且同一租户内不能映射到两台设备。随后在 `POST /api/v1/cnc/program-releases` 为“设备 + 条码”发布有效的程序文件、版本、SHA-256 和文件地址。

`POST /api/v1/equipment/telemetry` 接收实时或批量遥测：

```json
{
  "events": [
    {
      "username": "CNC-USER-01",
      "machineName": "CNC-01",
      "deviceTime": "2026-09-14T09:30:00",
      "eventType": "ALARM",
      "status": "ALARM",
      "recordNo": "evt-000042",
      "machiningCount": 7,
      "programNo": "P100",
      "programVersion": "v2",
      "materialNo": "MAT-001",
      "operatorName": "张三",
      "progressPercent": 42.5,
      "spindleStatus": "STOPPED",
      "alarmCode": "E-01",
      "alarmMessage": "主轴异常",
      "attributes": { "vendorExtension": "kept-for-traceability" }
    }
  ]
}
```

每条消息必须有 `recordNo` 或 `machiningCount`。QMS 以“已映射设备编码 + deviceTime + recordNo（或 machiningCount）”去重，并同时保存 `deviceTime` 与 QMS `receivedAt`。报警、状态变为停止、加工完成会留下 `PENDING` 业务事件；报警还会进入设备告警清单。`GET /api/v1/equipment/runtime-statuses` 按最后接收时间展示最新运行状态；超过 `QMS_EQUIPMENT_TELEMETRY_OFFLINE_AFTER_SECONDS`（默认 300 秒）会计算为 `OFFLINE`，不会覆写最后一条设备上报状态。

为适应多台设备每秒上报，秒级原始记录默认只保留 7 天；每次入库同时维护分钟、小时汇总。分钟汇总默认保留 365 天，小时汇总长期保留；告警、停机、完工等业务事件不受该清理影响。监控历史接口使用按设备与接收时间的游标分页，不会执行深度 `OFFSET` 查询。可用 `QMS_EQUIPMENT_TELEMETRY_RAW_RETENTION_DAYS`、`QMS_EQUIPMENT_TELEMETRY_MINUTE_RETENTION_DAYS` 与 `QMS_EQUIPMENT_TELEMETRY_RETENTION_ENABLED` 调整策略。

`POST /api/v1/cnc/file-requests` 是扫码下发的业务判定接口：

```json
{
  "username": "CNC-USER-01",
  "machineName": "CNC-01",
  "barcode": "WO-260914-001"
}
```

只有该映射设备上已发布且状态为 `ACTIVE` 的“设备 + 条码”程序会返回 `approved: true`，并给出 `fileUrl`、`programVersion`、`checksumSha256` 和文件大小。网关负责校验文件并把成功结果翻译为设备所需的 `#OpenFile:` 回复；QMS 不向设备建立 TCP 连接。

客户本地部署请使用 [ONPREM_DEPLOYMENT.md](ONPREM_DEPLOYMENT.md) 和 `docker-compose.onprem.yml`；该编排默认启用生产认证，不暴露数据库、OCR 或 API 端口。

## 标特福旧版监控软件接口

为兼容不能改造的 `WorkingMonitor.exe`，API 还提供 `POST /hessian/monitor`。这是一个 Hessian 1/2 兼容入口，只接受 `Monitor(String)`；字符串为旧软件发送的设备快照 JSON。软件端填写 QMS 主机 IP、服务端口、URL `/hessian/monitor`、Method `Monitor` 即可。

部署前必须设置 `QMS_BTF_HESSIAN_ALLOWED_IPS` 为监控电脑的精确 IPv4/IPv6 地址（逗号分隔）；空值会拒绝所有请求。旧协议没有租户和令牌字段，因此还必须把 `QMS_BTF_HESSIAN_TENANT_CODE` 设为现场所属租户。每台锣机需先通过设备网关映射登记，其 `machineName` 与旧软件的 `machinename` 一致。该接口不信任 `X-Forwarded-For`，也不接受 `X-Gateway-Token` 代替白名单。

旧状态的转换规则为：`WORK` 到 `PROCESSING`、`ALAM` 到 `ALARM`、`STOP` 到 `STOPPED`、`IDLE` 到 `IDLE`；`WAIT` 和 `OFFLINE` 作为 `IDLE` 写入并保留原始状态。现场监控软件、QMS 与数据库之间必须使用同一时区；接口收到的是 `yyyy-MM-dd HH:mm:ss` 的本地时间。

已添加一键启动脚本：[start-qms.ps1](D:\project\qms\start-qms.ps1)

在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-qms.ps1
```

启动后自动检查前后端；已运行的服务会跳过。需自动打开浏览器：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-qms.ps1 -OpenBrowser
```
