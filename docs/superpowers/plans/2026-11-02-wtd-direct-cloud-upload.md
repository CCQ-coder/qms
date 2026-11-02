# 旺通达飞针机直传线上 QMS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** XP 飞针机把原始 INI 和电脑状态直传线上 QMS，由线上服务保存、解析和处理，不再依赖 SCBGX 中转。

**Architecture:** 保留现有设备端扫描和计划任务，新增 HTTPS 传输与按目标区分的同步状态。QMS 用设备专用令牌鉴权、保存原始文件、生成与旧 C# 采集器一致的遥测，并复用现有入库和 FQC 投影。

**Tech Stack:** Python 3.4/stdlib、PyInstaller 3.4、Java 21/Spring Boot、MySQL/Flyway、OpenResty HTTPS。

**Spec:** `docs/superpowers/specs/2026-11-02-wtd-direct-cloud-upload-design.md`

## Global Constraints

- 不向 XP 分发 `X-Gateway-Token`；每台设备使用独立随机上传令牌。
- 旧 UNC 模式、无窗口 EXE、5 分钟文件任务和 1 分钟状态任务继续可用。
- 云端使用独立 `cloud_sync_state.json`；不改旧 `sync_state.json` 结构，并备份原 UNC 配置。
- 仅服务端确认原始文件保存及遥测接收后，客户端才记录成功。
- 保留旧复制算法得到的 Windows FILETIME 和相对路径，以匹配旧 `FPT-` 记录编号。
- 线上启用 HTTPS 和持久上传卷，并在切换后只保留线上投影调度器。

## Review Focus

- 旧 `sync_state.json` 已记成功的文件：新 HTTPS 目标必须首次补传，此后跳过未变化文件。
- 请求成功但回包丢失：客户端重试不得生成第二条遥测或第二个 FQC 批次。
- 文件在读取中变化、Base64/哈希错误或超大小：服务端不得确认成功，客户端下次重试。
- 中文设备号与任务名、夏令时/时区、FILETIME 舍入：C# 与 Java 的事件字段和 recordNo 必须逐项相等。
- 令牌被撤销、跨租户或伪造 deviceId、旧状态晚到：不得写入他机数据或覆盖新样本。

---

### Task 1: QMS 设备令牌与身份

**Files:**
- Create: `backend/src/main/resources/db/migration/V45__add_flying_probe_upload_token.sql`
- Modify: `backend/src/main/java/com/pcbqms/equipment/domain/EquipmentGatewayMapping.java`
- Modify: `backend/src/main/java/com/pcbqms/equipment/application/EquipmentGatewayMappingApplicationService.java`
- Modify: `backend/src/main/java/com/pcbqms/equipment/api/EquipmentGatewayMappingController.java`
- Modify: `backend/src/main/java/com/pcbqms/config/ProductionSecurityConfiguration.java`
- Modify: `backend/src/main/java/com/pcbqms/common/tenancy/JwtTenantContextFilter.java`
- Create: `backend/src/main/java/com/pcbqms/common/tenancy/FlyingProbeDeviceTenantContextFilter.java`
- Test: `backend/src/test/java/com/pcbqms/common/tenancy/FlyingProbeDeviceTenantContextFilterTest.java`
- Test: existing equipment mapping controller/application tests.

**Interfaces:** POST `.../gateway-mappings/{id}/upload-token` rotates and returns a 32-byte Base64URL token once; DELETE on the same path revokes it. Two device POST paths accept `X-Device-Token`; filter hashes it, finds one mapping and stores the bound mapping for the controller.

- [x] Write tests for issuance, rotation, revocation, missing/wrong token, cross-tenant mapping and device-id mismatch; run them to see failure.
- [x] Add nullable unique SHA-256 hash column, secured issuance endpoints and tenant filter; include dev security configuration so device paths never bypass token checks.
- [x] Run the focused tests and `git diff --check`; commit this QMS task.

### Task 2: QMS 原始文件接收与兼容解析

**Files:**
- Create: `backend/src/main/java/com/pcbqms/integration/api/FlyingProbeDeviceUploadController.java`
- Create: `backend/src/main/java/com/pcbqms/integration/api/FlyingProbeDeviceFileRequest.java`
- Create: `backend/src/main/java/com/pcbqms/integration/application/FlyingProbeDeviceUploadService.java`
- Create: `backend/src/main/java/com/pcbqms/integration/application/FlyingProbeIniParser.java`
- Create: `backend/src/main/java/com/pcbqms/integration/infrastructure/storage/FlyingProbeRawStorage.java`
- Modify: `backend/src/main/java/com/pcbqms/integration/application/GatewayHostStatusApplicationService.java`
- Test: new upload parser/service/controller tests, existing telemetry and host-status tests.

**Interfaces:** POST `/api/v1/flying-probe/device-files` accepts JSON `schemaVersion, deviceId, jobName, createdFileTime, modifiedFileTime, sha256, contentBase64`; POST `/api/v1/flying-probe/device-host-status` accepts the current status JSON. Both require the bound device token. Parser produces `EquipmentTelemetryEventRequest` and calls `EquipmentTelemetryApplicationService.receive`.

- [x] Build golden fixtures from the old C# `ProbeRecords` and test identical `sourceFile`, times, counts, `recordNo` and duplicate behavior; first run must fail.
- [x] Implement request validation, bounded decoding, atomic content-hash storage and compatible parser; call telemetry service only after raw bytes are saved.
- [x] Map status `deviceId` to the bound `hostName`; update only when `sampledAt` is newer; test retry and out-of-order samples.
- [x] Run focused backend tests and `git diff --check`; commit this QMS task.

### Task 3: XP 设备端直传

**Files:**
- Modify: `hr_sync/ini_share_sync/src/ini_share_sync/config.py`, `main.py`, `transfer.py`, `host_status.py`, `state.py`
- Modify: `hr_sync/ini_share_sync/sync_config.example.json`, `README.md`, `更新说明.txt`
- Test: `hr_sync/ini_share_sync/tests/test_config_and_discovery.py`, `test_main.py`, `test_host_status.py`, `test_state_and_transfer.py` and focused new HTTPS transport tests.

**Interfaces:** New config `server_url`, `device_id`, `upload_token`, `ca_cert` selects HTTPS; existing `target_root` selects UNC. Client sends Task 2 JSON with copied-TXT FILETIME values using Python 3.4 standard library; successful 2xx is the only state checkpoint, written only to `cloud_sync_state.json`.

- [x] Write tests for new/legacy config, UTF-8 metadata, FILETIME, destination-specific state, 2xx/4xx/timeout retries and status independence; first run must fail.
- [x] Implement HTTPS transport and config branching without changing old UNC copy and silent task actions.
- [ ] Run Windows Python 3.4 tests, build `dist-xp32/IniShareSync` on the prepared Windows host, verify no black window and TLS handshake; commit this `hr_sync` task.

### Task 4: 线上部署和逐台切换

**Files:**
- Update: QMS deployment runbook and XP package update instructions with HTTPS, persistent volume, token provisioning, rollback and verification commands.

- [x] Verify SSH login to `106.227.69.144`, identify the actual QMS API container, OpenResty config and persistent upload volume; back up current config/binaries.
- [ ] Configure verified HTTPS and deploy QMS migration/API; check unauthenticated and wrong-token requests fail, a valid sample saves raw bytes and produces one telemetry/FQC result. (公网 5173 HTTPS、V45、无令牌及错误令牌 401 已验证；实际设备样本待验。)
- [ ] Issue one device token, update one XP device, verify file and status reception plus idempotent retry, then roll out remaining devices.
- [ ] Disable SCBGX matching collector/relay and local projection scheduler only after the online path is confirmed; verify no duplicate FQC and leave rollback files in place.
