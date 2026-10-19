# QMS 遥测网关认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让飞针机采集器用已有 `X-Gateway-Token` 持续向 QMS 上报遥测数据，无需会过期的 Bearer Token。

**Architecture:** 生产安全链仅为遥测 POST 放行到专用网关过滤器；过滤器校验共享令牌与服务端指定租户，建立并清理租户上下文。携带 Bearer 的请求继续走 JWT 租户路径，控制器保留现有网关令牌复核。采集器协议与队列不变。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring Security、JUnit 5、Mockito、Maven；现有 .NET Framework 飞针机采集器。

**Spec:** `docs/superpowers/specs/2026-10-19-telemetry-gateway-auth-design.md`

## Global Constraints

- 只对 `POST /api/v1/equipment/telemetry` 增加无 Bearer 的网关模式；其他 QMS 接口、遥测 GET 和已有 Bearer 路径维持现有认证。
- 网关令牌读取 `QMS_GATEWAY_SHARED_TOKEN`，租户只读取服务端的 `QMS_GATEWAY_TELEMETRY_TENANT_CODE`；不信任客户端租户头或事件字段。
- 网关令牌缺失/错误为 HTTP 401；服务端租户配置缺失为 503；租户不存在/停用为 403。任何失败均不得写库。
- 租户上下文必须在请求结束或抛异常后清除；令牌不进入日志、错误正文、历史或版本库。
- 现场仅有 HTTP；部署文档要求 8080 只对受控网络来源开放，并安排 HTTPS。当前 QMS 工作目录的未提交修改必须保留。

## Review Focus

- 无令牌或错误令牌：过滤器返回 401，链不继续，租户上下文为空；Task 1 测试。
- 服务端共享令牌或租户配置缺失、租户停用：分别返回 503/503/403，链不继续；Task 1 测试。
- 有效网关令牌配无效或格式错误的 Authorization：不能回退到网关模式；Task 2 测试。
- 遥测 GET 与其他 QMS POST 只有网关令牌：仍需 JWT；Task 2 测试。
- 请求处理抛异常时：`TenantContext` 仍清除，下一次请求不会继承前一租户；Task 1 测试。

---

### Task 1: 网关遥测租户过滤器

**Files:**
- Create: `backend/src/main/java/com/pcbqms/common/tenancy/GatewayTelemetryTenantContextFilter.java`
- Modify: `backend/src/main/java/com/pcbqms/integration/application/GatewayIntegrationAuthentication.java`
- Test: `backend/src/test/java/com/pcbqms/common/tenancy/GatewayTelemetryTenantContextFilterTest.java`
- Test: `backend/src/test/java/com/pcbqms/integration/application/GatewayIntegrationAuthenticationTest.java`

**Interfaces:**
- Consumes: `GatewayIntegrationAuthentication.verify(String)`、`TenantContext.set/clear`、`JdbcTemplate.queryForObject`。
- Produces: `GatewayTelemetryTenantContextFilter(GatewayIntegrationAuthentication authentication, JdbcTemplate jdbcTemplate, String tenantCode)`；`GatewayIntegrationAuthentication.isConfigured(): boolean`。过滤器只匹配无 Authorization 的遥测 POST；令牌正确后查询 ACTIVE 租户，设置 actor 为 `gateway.telemetry`，在 `finally` 清理。

- [ ] **Step 1: 写失败测试**：`validGatewayRequestUsesConfiguredTenantAndClearsIt`、`missingOrWrongTokenReturns401`、`missingSharedTokenReturns503`、`missingTenantConfigReturns503`、`inactiveTenantReturns403`、`exceptionStillClearsTenant`；补 `isConfiguredReportsBlankToken`。断言 filter chain 是否调用、租户值和响应状态，不打印令牌。
- [ ] **Step 2: 运行 RED**：`mvn -f backend/pom.xml -Dtest=GatewayTelemetryTenantContextFilterTest,GatewayIntegrationAuthenticationTest test`；预期因新类/方法不存在而失败。
- [ ] **Step 3: 实现最小代码**：令牌校验复用 `verify` 的恒定时间比较；配置空值先返回 503，错误令牌返回 401，非 ACTIVE 租户返回 403。`shouldNotFilter` 对其他路径或存在任何 Authorization 头返回 true。
- [ ] **Step 4: 运行 GREEN**：同一 Maven 命令，预期全部通过。
- [ ] **Step 5: 在隔离分支只提交本任务文件**：`git add` 列出的四个文件并提交 `feat: authenticate telemetry gateway tenant`。

### Task 2: 只为遥测 POST 接入网关模式

**Files:**
- Modify: `backend/src/main/java/com/pcbqms/config/ProductionSecurityConfiguration.java`
- Modify: `backend/src/main/java/com/pcbqms/common/tenancy/JwtTenantContextFilter.java`
- Test: `backend/src/test/java/com/pcbqms/common/tenancy/JwtTenantContextFilterTest.java`
- Create test: `backend/src/test/java/com/pcbqms/config/ProductionSecurityConfigurationTest.java`

**Interfaces:**
- Consumes: Task 1 的 `GatewayTelemetryTenantContextFilter` 构造器；现有 `GatewayIntegrationAuthentication`、`JdbcTemplate` 和 JWT 过滤器。
- Produces: 安全链对遥测 POST 使用 `permitAll` 以进入网关过滤器；网关过滤器在 JWT 租户过滤器之后运行。JWT 租户过滤器仅在无 Authorization 的遥测 POST 跳过；携带 Authorization 的请求保留 JWT 路径。

- [ ] **Step 1: 写失败测试**：`JwtTenantContextFilterTest` 增加 `skipsOnlyTelemetryPostWithoutAuthorization`；`ProductionSecurityConfigurationTest` 增加 `gatewayOnlyTelemetryPostReachesGatewayFilter`、`invalidAuthorizationNeverFallsBackToGateway`、`gatewayHeaderDoesNotOpenGetOrOtherPost`。用 Spring Security MockMvc 小型测试上下文验证路由，不启动真实数据库。
- [ ] **Step 2: 运行 RED**：`mvn -f backend/pom.xml -Dtest=JwtTenantContextFilterTest,ProductionSecurityConfigurationTest test`；预期新路由/过滤器接线断言失败。
- [ ] **Step 3: 实现最小接线**：将 `productionSecurityFilterChain` 参数扩展为 `HttpSecurity`、`JwtTenantContextFilter`、`GatewayIntegrationAuthentication`、`JdbcTemplate` 和 `@Value("${qms.integration.gateway.telemetry-tenant-code:}") String telemetryTenantCode`；只对目标 POST 增加 `permitAll`，以 `new GatewayTelemetryTenantContextFilter(...)` 接入安全链，不把它注册为全局 servlet filter。保留现有前端资源和登录接口允许规则。缺失/错误 Authorization 仍由既有 JWT 流程拒绝。
- [ ] **Step 4: 运行 GREEN 与回归**：运行上述定向测试及 `mvn -f backend/pom.xml test`，预期测试均通过。
- [ ] **Step 5: 在隔离分支只提交本任务文件**：提交 `feat: allow scoped telemetry gateway requests`。

### Task 3: 部署配置、发布包和现场验证

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `docker-compose.onprem.yml`
- Modify: `.env.onprem.example`
- Modify: `deploy/windows/.env.windows.example`
- Modify: `deploy/windows/WINDOWS_DEPLOYMENT.md`
- Modify: `deploy/windows/RUN_WINDOWS.md`
- Test: `backend/src/test/java/com/pcbqms/config/ProductionSecurityConfigurationTest.java`

**Interfaces:**
- Consumes: Task 2 的 `qms.integration.gateway.telemetry-tenant-code` 配置项。
- Produces: `QMS_GATEWAY_TELEMETRY_TENANT_CODE` 在 Windows 和 Docker on-prem 部署时可设置；默认空值使网关模式失败关闭，不改变 JWT 路径。

- [ ] **Step 1: 写配置失败测试**：在 Task 2 的安全配置测试中加入属性缺失返回 503、显式租户值传给过滤器的断言；RED 运行该测试。
- [ ] **Step 2: 增加配置与文档**：示例仅写占位或注释，不写现场令牌；说明同时配置共享令牌和租户、保留原 `.env.windows`、限制 HTTP 来源、发布后的验证与回退。
- [ ] **Step 3: 验证**：`mvn -f backend/pom.xml test`、`mvn -f backend/pom.xml -DskipTests package`、`git diff --check`；预期零失败。运行飞针机采集器现有无 Bearer 发送测试，确认请求头和本地重试行为未变。
- [ ] **Step 4: 在隔离分支只提交本任务文件**：包含上述测试文件，提交 `docs: configure telemetry gateway tenant`。
- [ ] **Step 5: 部署前核对**：比较待发布后端与 `192.168.1.98` 正在运行的版本，确认不会回退现场其他功能；保留真实 `.env.windows`、日志、附件和飞针机队列。若没有服务器部署访问权限，交付构建包和明确部署步骤，并如实记录现场未更新。
- [ ] **Step 6: 现场验证**：在 QMS 服务器核对现有 `QMS_GATEWAY_SHARED_TOKEN` 与采集器一致，新增 `QMS_GATEWAY_TELEMETRY_TENANT_CODE`，更新并重启服务；先检查健康接口，再观察采集器第一条待发记录返回 2xx、QMS 正确租户有记录、待发量下降。若失败，保留队列与旧包并回退 QMS 服务。

## 执行前隔离与交付检查

- 使用 `superpowers:using-git-worktrees` 从 QMS 当前提交创建 `codex/telemetry-gateway-auth` 工作区。QMS 主工作目录已有大量未提交修改，包括安全配置文件；不要重置、移动、暂存或提交这些用户修改。实施前阅读相关文件的当前差异，确保分支保留现有对前端资源的放行规则。
- 最终仅交付本方案的提交、测试输出与发布包。由于现场 QMS 在另一台电脑，只有核对部署版本并确认具备实际访问方式后才能声称同步已经恢复。
