# 标特福旧版监控软件 Hessian 设备数据接口设计

## 目的与范围

让现有标特福 `WorkingMonitor.exe` 不改动程序即可向 QMS 上报设备监控快照。QMS 新增一个仅兼容旧协议的 Hessian 服务端端点；接收到的快照转换为现有的设备遥测记录和设备实时状态。

本设计不替换旧监控软件，不修改锣机 TCP 协议，也不启用已有 `BtfGateway.exe` 的采集路径。它们可以在不同现场按需使用，但同一台锣机不能同时由两种采集程序监听。

## 旧客户端契约

旧客户端通过 HTTP 调用 Hessian 服务：

| 项目 | 固定值 |
| --- | --- |
| URL | `/hessian/monitor` |
| Hessian 方法 | `Monitor` |
| 参数 | 一个字符串参数，内容为 UTF-8 JSON 数组 |
| 正常返回 | 任意非空 Hessian 字符串，例如 `OK` |

监控软件配置窗口应填写部署后的 QMS 主机 IP、QMS 服务端口、URL `/hessian/monitor`、Method `Monitor`，并按现场需要设置上报间隔。该软件不支持自定义认证头或令牌。

请求 JSON 的第一项是 `{\"type\":\"RealtimeInf\"}`。其余每一项是一台设备的监控快照，至少包含 `machinename`、`state` 和 `updateCurTime`。旧软件把数值也序列化为字符串。

示例：

```json
[
  {"type":"RealtimeInf"},
  {
    "id":"1",
    "machinename":"L001",
    "machinetype":"0",
    "spindlestatus":"1110",
    "state":"WORK",
    "updateCurTime":"2026-09-17 09:30:00",
    "runtime":"3600",
    "username":"操作员甲",
    "programname":"JOB-A",
    "finHole":"120",
    "routLen":"5000",
    "finper":"42.5",
    "screentext":""
  }
]
```

## 接入架构

在 QMS 后端新增隔离的标特福兼容模块：

1. 基于 Caucho Hessian I/O 编解码器实现一个 Jakarta Servlet HTTP 处理器，仅注册在 `/hessian/monitor`，只接受 `Monitor(String)`；不使用依赖旧 `javax.servlet` API 的 Hessian servlet 类。
2. 入口先用 TCP 对端地址执行白名单校验，再解析 Hessian 和 JSON。
3. 快照适配器将旧字段转换为现有 `EquipmentTelemetryEventRequest`。
4. 适配后的事件复用 `EquipmentTelemetryApplicationService`；该服务负责设备映射、历史留存、幂等、实时状态更新和报警业务事件。
5. 服务端以 Hessian `OK` 字符串确认成功；校验或处理失败时返回 Hessian fault，旧客户端会在下一个间隔重试。

来自反向代理的 `X-Forwarded-For` 不作为信任来源。部署在反向代理后时，必须保证后端实际看到的对端地址是已配置的受信任代理，或将入口限制在内网；本期默认按 servlet 连接的远程地址校验。

## 认证与配置

新增配置项：

```yaml
qms:
  integration:
    btf-hessian:
      allowed-ips: ${QMS_BTF_HESSIAN_ALLOWED_IPS:}
      tenant-code: ${QMS_BTF_HESSIAN_TENANT_CODE:${qms.tenancy.default-code}}
```

`QMS_BTF_HESSIAN_ALLOWED_IPS` 为逗号分隔的精确 IPv4 或 IPv6 地址，例如 `10.20.169.142,10.20.169.143`。空配置、空白条目、非法地址或非白名单来源均拒绝请求。应用启动时即校验配置中的地址格式，避免错误配置导致意外放行。

旧协议也没有租户字段，因此 `QMS_BTF_HESSIAN_TENANT_CODE` 将此入口固定绑定到一个客户租户；默认值是 `qms.tenancy.default-code`，生产部署必须显式设置为现场设备所属的租户编码。

旧客户端没有令牌能力，因此不能复用 `X-Gateway-Token` 认证；该 Hessian 入口不接受令牌作为替代认证。

## 数据映射

设备通过旧字段 `machinename` 与 QMS 设备网关映射中的 `machineName` 匹配。每台现场设备必须先建立一条映射；不存在或匹配不唯一时，该请求失败且不写入任何设备数据。

| 旧快照字段 | QMS 遥测字段或属性 | 规则 |
| --- | --- | --- |
| `machinename` | `machineName` | 去首尾空格后匹配设备映射 |
| `updateCurTime` | `deviceTime` | 按 `yyyy-MM-dd HH:mm:ss` 的本地时间解析 |
| `state` | `status` | `WORK→PROCESSING`、`ALAM→ALARM`、`STOP→STOPPED`、`IDLE→IDLE` |
| `state=WAIT` | `status=IDLE`，属性 `legacyState=WAIT` | 旧协议没有可映射的等待状态，保留原值 |
| `state=OFFLINE` | `status=IDLE`，属性 `legacyState=OFFLINE` | 离线判定仍由 QMS 无数据超时机制负责 |
| `programname` | `programNo` | 原样保留 |
| `username` | `operatorName` | 原样保留 |
| `finHole` | `machiningCount` | 解析为非负整数 |
| `finper` | `progressPercent` | 解析为 0 至 100 的十进制数 |
| `spindlestatus` | `spindleStatus` | 原样保留 |
| `screentext` | `alarmMessage` | `ALAM` 状态下写入，空白不写入 |
| `runtime` | `attributes.runtimeSeconds` | 解析为非负整数或小数 |
| `routLen` | `attributes.routeLength` | 解析为数值；旧单位未明确，保留原始字段名避免误标单位 |
| `id`、`machinetype` 与原始 `state` | `attributes` | 完整保留用于审计与排障 |

每条快照的 `eventType` 固定为 `LEGACY_MONITOR_SNAPSHOT`。`recordNo` 使用设备名、设备时间、状态、加工计数和程序名的稳定组合；同一设备、同一快照重试不会重复写入。一个请求内包含多台设备时，全部校验通过后才写入；其中任一台数据非法、未映射或无法处理时，整批失败。

## 错误处理与可观测性

| 场景 | 处理 | 对旧客户端的结果 |
| --- | --- | --- |
| 非白名单 IP | 不解析业务数据，记录来源 IP 和拒绝原因 | Hessian fault |
| 非法 Hessian 或方法不是 `Monitor` | 拒绝并记录协议错误 | Hessian fault |
| JSON 格式错误、头记录缺失或字段格式错误 | 不写入数据，记录字段级原因 | Hessian fault |
| 设备未映射 | 不写入该批次，记录设备名 | Hessian fault |
| 重复快照 | 返回 `OK`，统计为重复 | Hessian `OK` |
| 成功处理 | 记录设备数、接收数、重复数和耗时 | Hessian `OK` |

日志不记录完整原始请求内容，以免泄露操作员信息；仅记录请求来源、设备名称、处理结果和可安全展示的错误摘要。

## 测试与验收

自动化测试应覆盖：

1. Hessian 1 与 Hessian 2 格式均可调用 `Monitor(String)` 并得到 `OK`。
2. 所有状态映射及 `WAIT`、`OFFLINE` 的原始状态保留。
3. 数字字段的正常、空白、越界和非法格式。
4. 设备映射成功、未映射、白名单命中、白名单拒绝及空白名单拒绝。
5. 相同快照重复提交只保留一条历史遥测记录。
6. 同批次包含一条非法或未映射设备时不产生部分写入。
7. Hessian fault 可被客户端识别，且不返回内部异常细节。

现场验收：在一台白名单内的监控电脑填写 QMS IP、端口、`/hessian/monitor`、`Monitor` 后，旧监控软件显示 ERP 已连接；QMS 中对应设备出现当前状态和历史快照。更换为非白名单电脑后，QMS 拒绝上报且不产生数据。
