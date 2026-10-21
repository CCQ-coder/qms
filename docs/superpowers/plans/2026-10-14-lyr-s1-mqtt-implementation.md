# LYR-S1 MQTT 直连 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LYR 森泉监听软件改造成可按设备映射把处理后的森泉上报可靠发布到 S1 MQTT 的桌面程序。

**Architecture:** LYR 在成功登录时发放唯一 token，并由内存会话把后续 token 上报解析回来源 `device_code`。它在 HTTP 应答前将已映射的属性/事件写入本地 outbox，由独立 QoS 1 MQTT 发布器异步发送；S1 只需配置“LYR森泉设备”产品和逐设备凭据，不新增接口或修改源码。

**Tech Stack:** C# / .NET Framework 4.x / WinForms / `JavaScriptSerializer` / Windows DPAPI / M2Mqtt MQTT 3.1.1 客户端。

**Spec:** `docs/superpowers/specs/2026-10-14-lyr-s1-mqtt-design.md`

## Global Constraints

- 继续使用 `gateway/hoson-mes-mock/build.cmd` 的 .NET Framework 4.x `csc.exe` 编译链。
- 不修改 `D:\project\S1` 的源码、数据库结构或 HTTP 接口。
- 标题必须为“LYR森泉监听软件”，`ClientSize` 必须为 `1400 × 850`。
- MQTT 属性 Topic 为 `/{productId}/{serialNumber}/property/post`，事件 Topic 为 `/{productId}/{serialNumber}/event/post`，发布 QoS 为 1。
- 每一条映射使用独立 MQTT 凭据；密码只用 `ProtectedData`/`CurrentUser` DPAPI 落盘，永不显示在日志、错误或界面文本中。
- 属性历史值不超过 64 字符，事件备注不超过 200 字符；完整原始 JSON 仅写现有 LYR JSONL 日志。
- MQTT 实现提供至少一次投递；PUBACK 前不得删除 outbox 记录。
- 登录成功必须将 `response.data.token` 替换为不可预测的设备专属 token；token 到 `device_code` 的内存会话采用 24 小时空闲过期，并在重启后等待设备重新登录。
- 保留当前工作区的既有未提交改动；每次提交只能包含本任务文件。

## Review Focus

- 未知或过期 token、以及未配置 `device_code` 的请求必须保持原 MES 应答，且绝不误发到任一 S1 Topic；由 Task 4 的 HTTP 集成测试覆盖。
- 磁盘无法写入 outbox 时，已映射上报必须得到 HTTP 500，而不是成功模板；由 Task 4 的失败路径测试覆盖。
- `alarm_message`、扫码内容和摘要超过 S1 上限时必须确定性截断；由 Task 2 的映射测试覆盖。
- Broker 凭据错误或离线时，所有待发记录必须保留并退避重试；由 Task 3 的假 MQTT 会话测试覆盖。
- QoS 1 的 PUBACK 丢失会导致重复发送，属性应保持幂等、事件采用至少一次语义；由 Task 3 的未确认/重启恢复测试覆盖。

---

## File Structure

| 路径 | 责任 |
| --- | --- |
| `gateway/hoson-mes-mock/S1MqttConfiguration.cs` | 设置与逐设备映射、DPAPI 凭据保护、JSON 配置读写。 |
| `gateway/hoson-mes-mock/HosonDeviceSessionStore.cs` | 设备专属登录 token 的生成、解析和 24 小时空闲过期。 |
| `gateway/hoson-mes-mock/S1MqttPipeline.cs` | 森泉报文映射、消息序列化、持久化 outbox 和本地去重。 |
| `gateway/hoson-mes-mock/S1MqttPublisher.cs` | MQTT 会话抽象、M2Mqtt 适配器、QoS 1 PUBACK 处理和重试调度。 |
| `gateway/hoson-mes-mock/S1DeviceMappingDialog.cs` | S1 全局配置与逐设备映射的 WinForms 编辑对话框。 |
| `gateway/hoson-mes-mock/HosonMesServer.cs` | 在 HTTP 成功响应之前提供受控的 outbox 入队钩子。 |
| `gateway/hoson-mes-mock/HosonMesMock.cs` | 连接配置、pipeline、publisher、窗口布局与生命周期。 |
| `gateway/hoson-mes-mock/s1_mqtt_config.json` | 不含真实凭据的默认 S1 配置文件。 |
| `gateway/hoson-mes-mock/S1MqttTests.cs` | 配置、映射、outbox 和 publisher 的无 Broker 单元测试。 |
| `gateway/hoson-mes-mock/S1_SETUP.md` | S1 产品物模型、创建设备和凭据录入操作说明。 |
| `gateway/hoson-mes-mock/build.cmd`、`test-layout.cmd`、`README.md` | 依赖引用、测试编译、发布复制与使用说明。 |

### Task 1: 安全的 S1 配置、设备映射与登录会话

**Files:**
- Create: `gateway/hoson-mes-mock/S1MqttConfiguration.cs`
- Create: `gateway/hoson-mes-mock/HosonDeviceSessionStore.cs`
- Create: `gateway/hoson-mes-mock/s1_mqtt_config.json`
- Modify: `gateway/hoson-mes-mock/build.cmd`
- Modify: `gateway/hoson-mes-mock/test-layout.cmd`
- Test: `gateway/hoson-mes-mock/S1MqttTests.cs`

**Interfaces:**
- Produces `S1MqttSettings` (`Enabled`, `Host`, `Port`, `UseTls`, `List<S1DeviceMapping> Devices`).
- Produces `S1DeviceMapping` (`SourceDeviceCode`, `ProductId`, `SerialNumber`, `ClientId`, `UserName`, `ProtectedPassword`).
- Produces `S1MqttSettingsStore.Load(string path)`, `Save(string path, S1MqttSettings settings)`, `ProtectPassword(string clearText)` and `UnprotectPassword(string protectedValue)`.
- Produces `HosonDeviceSessionStore.Register(string deviceCode, DateTime utcNow) -> string`, `TryResolve(string token, DateTime utcNow, out string deviceCode) -> bool`, and `Clear()`.
- Task 2 consumes `S1DeviceMapping`; Task 3 consumes `S1MqttSettings` and decrypted credentials.

- [ ] **Step 1: Write failing configuration tests in `S1MqttTests.cs`**

Test an empty/default configuration, a valid two-device round trip, duplicate `SourceDeviceCode` rejection, invalid port rejection, and a DPAPI round trip whose saved JSON does not contain the clear-text password. Test that two registrations yield distinct non-empty tokens, resolution returns the registering `deviceCode`, resolution refreshes a 24-hour idle lifetime, expiry fails, and `Clear()` simulates restart loss.

- [ ] **Step 2: Compile and run the configuration test before implementation**

Run: extend `test-layout.cmd` to compile `S1MqttTests.exe` with only the test and configuration source files, then run it.

Expected: compilation fails because `S1MqttSettingsStore` and `HosonDeviceSessionStore` do not exist.

- [ ] **Step 3: Implement `S1MqttConfiguration.cs` and `HosonDeviceSessionStore.cs`**

Use `JavaScriptSerializer` for a versioned JSON object. Validate non-empty host, port `1..65535`, unique case-insensitive source codes, positive product IDs, and required serial/client/user values. Use `ProtectedData.Protect` and `DataProtectionScope.CurrentUser`; keep only Base64 ciphertext in `ProtectedPassword`. Save atomically through `<path>.tmp` and `File.Replace`/`File.Move`. Implement the session store with cryptographically random token bytes, a `LYR-` prefix, case-sensitive lookup, and a 24-hour sliding UTC expiry; do not persist tokens.

- [ ] **Step 4: Add the shipped default `s1_mqtt_config.json` and update compilation/copy rules**

Ship forwarding disabled, port `1883`, TLS disabled, and no mappings. Add `System.Security.dll` to compilation and copy the default only if the output directory has no user-edited configuration.

- [ ] **Step 5: Run the configuration test**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: configuration test passes alongside existing parser, runtime, and layout checks.

- [ ] **Step 6: Commit Task 1**

```powershell
git add gateway/hoson-mes-mock/S1MqttConfiguration.cs gateway/hoson-mes-mock/HosonDeviceSessionStore.cs gateway/hoson-mes-mock/s1_mqtt_config.json gateway/hoson-mes-mock/S1MqttTests.cs gateway/hoson-mes-mock/build.cmd gateway/hoson-mes-mock/test-layout.cmd
git commit -m "feat: add protected S1 settings and device sessions"
```

### Task 2: 森泉到 S1 的映射与持久化 Outbox

**Files:**
- Create: `gateway/hoson-mes-mock/S1MqttPipeline.cs`
- Modify: `gateway/hoson-mes-mock/S1MqttTests.cs`

**Interfaces:**
- Consumes `MesExchange` and `S1DeviceMapping` from Task 1.
- Produces `S1PublishMessage` (`Id`, `SourceDeviceCode`, `Topic`, `Payload`, `Attempts`, `NextAttemptUtc`).
- Produces `S1EventMapper.Map(MesExchange exchange, S1DeviceMapping mapping) -> List<S1PublishMessage>`.
- Produces `S1Outbox.TryEnqueue(IEnumerable<S1PublishMessage> messages, out string error)`, `GetDue(DateTime utcNow)`, `Acknowledge(string id)`, and `Reschedule(string id, DateTime nextAttemptUtc)`.
- Task 3 consumes `S1PublishMessage` and `S1Outbox`; Task 4 uses `S1EventMapper` and `S1Outbox` in the pre-response hook.

- [ ] **Step 1: Write failing mapper and outbox tests in `S1MqttTests.cs`**

Add one test per supported method: `login`, `device_status`, `feed_material`, `scan_result`, `return_labels_result`, `carft_param`/`craft_param`, and `version_info`. Assert the exact Topic, identifiers, mapped `run_status` values (`1`, `2`, `3`), event identifier, 64-character value limit, and 200-character remark limit. Add tests for atomic outbox reload, retry state, acknowledgement removal, and duplicate inbound `device_code + method + id` suppression.

- [ ] **Step 2: Run the mapper/outbox test before implementation**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: compilation fails because `S1EventMapper` and `S1Outbox` do not exist.

- [ ] **Step 3: Implement `S1EventMapper.Map` and value serialization**

Deserialize `MesExchange.RequestBody`, read `data`, and produce one property message plus one event message where the protocol supplies stateful data. Use the source request `id` when present; otherwise use a SHA-256 of normalized device code, method, request body, and received millisecond to form message IDs. Use the exact property/event identifiers in the approved spec and serialize S1 `ThingsModelSimpleItem` arrays with `id`, `value`, `ts`, and `remark`.

- [ ] **Step 4: Implement durable `S1Outbox`**

Persist `data\\s1-pending-events.json` atomically under a single lock. Preserve unpublished messages across process restart, record attempts and next retry instant, and retain a bounded completed-ID ledger sufficient to suppress stable device retries for 24 hours. Do not delete a message before Task 3 confirms its PUBACK.

- [ ] **Step 5: Run the mapper/outbox test**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: all method mapping, truncation, outbox restart, acknowledgement, and duplicate tests pass.

- [ ] **Step 6: Commit Task 2**

```powershell
git add gateway/hoson-mes-mock/S1MqttPipeline.cs gateway/hoson-mes-mock/S1MqttTests.cs gateway/hoson-mes-mock/test-layout.cmd
git commit -m "feat: map Hoson reports into S1 outbox"
```

### Task 3: QoS 1 MQTT 发布与重试

**Files:**
- Create: `gateway/hoson-mes-mock/S1MqttPublisher.cs`
- Create: `gateway/hoson-mes-mock/lib/M2Mqtt.dll`
- Modify: `gateway/hoson-mes-mock/S1MqttTests.cs`
- Modify: `gateway/hoson-mes-mock/build.cmd`
- Modify: `gateway/hoson-mes-mock/test-layout.cmd`

**Interfaces:**
- Consumes `S1MqttSettings`, `S1DeviceMapping`, `S1PublishMessage`, and `S1Outbox`.
- Produces `IS1MqttSession` with `Connect(S1MqttSettings, S1DeviceMapping, string password)`, `Publish(string topic, byte[] payload) -> ushort`, `Disconnect()`, and `Published`/`ConnectionClosed` events.
- Produces `S1MqttPublisher.Start()`, `Stop(TimeSpan timeout)`, `Wake()`, and status properties `ConnectedMappings`, `TotalMappings`, `PendingCount`, `LastSuccessUtc`, `LastError`.
- Task 4 consumes `S1MqttPublisher` and its status only; it must not call M2Mqtt directly.

- [ ] **Step 1: Write failing publisher tests with a fake `IS1MqttSession`**

Verify that the publisher connects using the mapping’s own credentials, publishes QoS 1 only to the stored Topic, retains a record until the fake emits successful PUBACK, reschedules a failed PUBACK after 1 second, caps exponential retry at 60 seconds, and restores an unacknowledged record after reloading the outbox.

- [ ] **Step 2: Run the publisher test before implementation**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: compilation fails because `IS1MqttSession` and `S1MqttPublisher` do not exist.

- [ ] **Step 3: Vendor the .NET Framework-compatible M2Mqtt DLL and implement the adapter**

Place the audited M2Mqtt DLL in `lib\\M2Mqtt.dll`. Implement an adapter behind `IS1MqttSession`; configure broker host/port/TLS from global settings, client ID/user/password from the selected mapping, `cleanSession=false`, and QoS `MqttMsgBase.QOS_LEVEL_AT_LEAST_ONCE`. Forward the library’s published callback to `Published`.

- [ ] **Step 4: Implement `S1MqttPublisher`**

Run one worker thread, keep a session per source device mapping, associate M2Mqtt message IDs with outbox IDs, acknowledge only on successful PUBACK, and schedule retries using `min(60 seconds, 2^(attempt-1) seconds)`. Catch authentication, network, TLS, and callback failures without losing outbox state; do not log credentials.

- [ ] **Step 5: Update build and test references, then run the publisher test**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: fake-session QoS, PUBACK, reconnect, retry, and restart tests pass; the released build copies `M2Mqtt.dll` next to `HosonMesMock.exe`.

- [ ] **Step 6: Commit Task 3**

```powershell
git add gateway/hoson-mes-mock/S1MqttPublisher.cs gateway/hoson-mes-mock/lib/M2Mqtt.dll gateway/hoson-mes-mock/S1MqttTests.cs gateway/hoson-mes-mock/build.cmd gateway/hoson-mes-mock/test-layout.cmd
git commit -m "feat: publish S1 telemetry over MQTT"
```

### Task 4: HTTP token 绑定、入队时机、窗口配置与运行生命周期

**Files:**
- Create: `gateway/hoson-mes-mock/S1DeviceMappingDialog.cs`
- Modify: `gateway/hoson-mes-mock/HosonMesServer.cs`
- Modify: `gateway/hoson-mes-mock/HosonMesMock.cs`
- Modify: `gateway/hoson-mes-mock/ServerTests.cs`
- Modify: `gateway/hoson-mes-mock/LayoutTests.cs`
- Modify: `gateway/hoson-mes-mock/S1MqttTests.cs`
- Modify: `gateway/hoson-mes-mock/test-layout.cmd`

**Interfaces:**
- Consumes the settings store, `HosonDeviceSessionStore`, mapper, outbox, and publisher from Tasks 1–3.
- Produces `HosonMesServer.BeforeResponse` as `Func<MesExchange, string>`; it may replace `MesExchange.ResponseBody`, `null` permits the response, and a non-empty message turns it into HTTP 500 JSON error.
- Produces `MesResponseBuilder.ReplaceLoginToken(string responseBody, string token) -> string`; it preserves the response’s existing ID, timestamp, method, success state, and all fields except `response.data.token`, and is used only for a successful `login` response.
- Produces a mapping dialog that returns validated `S1MqttSettings` only after `S1MqttSettingsStore.Save` succeeds.

- [ ] **Step 1: Write failing HTTP and layout tests**

In `ServerTests`, install a `BeforeResponse` handler and assert it runs before the client receives a successful template; assert a non-empty handler result becomes HTTP 500. Add two login requests with different `device_code` values and assert their response tokens differ; send a later `device_status` with one returned token and assert the received exchange resolves to the correct device code. Assert an unknown token retains its normal template and creates no outbox message. In `LayoutTests`, assert title `LYR森泉监听软件`, client size `1400 × 850`, the visible S1 configuration group, masked password editor, and publisher shutdown when the form closes.

- [ ] **Step 2: Run the affected tests before implementation**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: HTTP/layout compilation or assertions fail because the hook and S1 controls do not exist.

- [ ] **Step 3: Add the pre-response hook to `HosonMesServer`**

After a valid configured POST has generated `MesExchange.ResponseBody` and before `WriteResponse`, invoke `BeforeResponse`. On a successful `login`, the form handler registers `data.device_code` and replaces only `response.data.token`; on later messages it resolves the request token and sets `MesExchange.DeviceCode`. It queues only enabled, mapped reports. A queue persistence error returns HTTP 500 with the handler’s safe error text; unknown paths, invalid JSON, unsupported methods, disabled forwarding, missing mappings, and unknown/expired tokens preserve their existing behavior without MQTT publication.

- [ ] **Step 4: Integrate pipeline and publisher into `MockForm`**

Load `s1_mqtt_config.json` at startup, subscribe `BeforeResponse`, start the publisher only when forwarding is enabled, call `Wake()` after an enqueue, and stop the publisher before the form closes. Keep `ExchangeReceived` for UI/log display after the response. Surface only redacted publisher status text on the UI thread.

- [ ] **Step 5: Build the S1 configuration and mapping UI**

Set title and client size to the required values. Add the “S1 MQTT服务器配置” group with forwarding switch, Broker host, port, TLS, device-mapping button, connection summary, pending count, last success, and last error. Implement the mapping dialog as an editable grid/form for source device code, product ID, serial number, client ID, username, and a password field that always renders as bullets.

- [ ] **Step 6: Run the complete local verification**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Run: `gateway\hoson-mes-mock\build.cmd`

Expected: parser, runtime, S1 configuration/pipeline/publisher, server and layout tests pass; build creates the executable, default configuration, README, and M2Mqtt DLL in `outputs\\hoson-mes-mock`.

- [ ] **Step 7: Commit Task 4**

```powershell
git add gateway/hoson-mes-mock/HosonMesServer.cs gateway/hoson-mes-mock/HosonMesMock.cs gateway/hoson-mes-mock/S1DeviceMappingDialog.cs gateway/hoson-mes-mock/ServerTests.cs gateway/hoson-mes-mock/LayoutTests.cs gateway/hoson-mes-mock/S1MqttTests.cs gateway/hoson-mes-mock/test-layout.cmd
git commit -m "feat: add LYR S1 MQTT configuration UI"
```

### Task 5: S1 建档说明与发布文档

**Files:**
- Create: `gateway/hoson-mes-mock/S1_SETUP.md`
- Modify: `gateway/hoson-mes-mock/README.md`
- Modify: `gateway/hoson-mes-mock/build.cmd`

**Interfaces:**
- Consumes the exact identifiers and configuration fields produced in Tasks 1–4.
- Produces an operator-ready S1 provisioning guide and a packaged `S1_SETUP.md` beside the executable.

- [ ] **Step 1: Write documentation acceptance assertions in `S1MqttTests.cs`**

Assert the shipped setup guide contains all ten property identifiers, seven event identifiers, both MQTT Topic patterns, the QoS 1 rule, the per-device credential requirement, the 24-hour token idle-expiry/re-login behavior, and the statement that raw JSON remains in LYR logs.

- [ ] **Step 2: Run the documentation test before implementation**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Expected: the documentation assertion fails because `S1_SETUP.md` does not exist.

- [ ] **Step 3: Write `S1_SETUP.md` and update `README.md`**

Document creation of the “LYR森泉设备” product, each approved property/event identifier and value range, per-device S1 setup, retrieval of MQTT credentials, LYR mapping input, firewall/TLS guidance, and the two-device test procedure. State that LYR issues a per-device login token, expires it after 24 hours of idleness, and requires a new device login after restart. Update the README title, UI description, S1 file locations, data-flow guarantee, and explicit at-least-once event behavior.

- [ ] **Step 4: Package the guide and run final tests**

Run: `gateway\hoson-mes-mock\test-layout.cmd`

Run: `gateway\hoson-mes-mock\build.cmd`

Expected: all tests pass and `outputs\\hoson-mes-mock\\S1_SETUP.md` exists with the executable and configuration.

- [ ] **Step 5: Commit Task 5**

```powershell
git add gateway/hoson-mes-mock/S1_SETUP.md gateway/hoson-mes-mock/README.md gateway/hoson-mes-mock/S1MqttTests.cs gateway/hoson-mes-mock/build.cmd
git commit -m "docs: add S1 MQTT provisioning guide"
```

## Plan Self-Review

- **Spec coverage:** Task 1 covers secure settings and the 24-hour in-memory token session; Tasks 2–4 cover the direct MQTT mapping, privacy, reliable transfer, HTTP token binding, existing HTTP behavior, and window change. Task 5 covers S1 product/physical-device provisioning and deployed operator documentation. No S1 source modification is planned.
- **Step scan:** Every task begins with a failing test and ends with a specific test command, expected outcome, and narrow commit.
- **Type consistency:** `S1MqttSettings`/`S1DeviceMapping` and `HosonDeviceSessionStore` originate in Task 1; Task 2 produces `S1PublishMessage`/`S1Outbox`; Task 3 owns all MQTT sessions; Task 4 consumes those public interfaces without broker-library coupling.
- **Review focus:** Unknown token/missing mapping and queue I/O failure are assigned to Task 4; truncation to Task 2; broker failure/PUBACK loss to Task 3.
- **Proportion:** The plan specifies interfaces, decisions and assertions without duplicating implementation bodies.
