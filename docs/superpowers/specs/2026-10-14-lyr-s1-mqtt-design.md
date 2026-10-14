# LYR 森泉监听软件接入 S1 MQTT 设计

**状态：** 已确认设计，待实施计划批准
**日期：** 2026-10-14

## 目标与范围

将 `gateway/hoson-mes-mock` 改造成“LYR森泉监听软件”。它继续作为森泉 MES HTTP 监听端，接收设备上报、返回原协议响应，并把处理后的数据可靠地发布到 S1 的原生 MQTT 数据入口。

每台现场设备在 S1 中都是“LYR森泉设备”产品下的独立设备。LYR 使用该设备自己的 MQTT 凭据，而不是新增 S1 REST 接口，也不改动 S1 的业务源码。

本次包含：

- 窗口标题改为“LYR森泉监听软件”，客户端尺寸调整为 `1400 × 850`。
- 增加 S1 MQTT 服务配置、设备映射维护、连接和待发状态展示。
- 对已支持的森泉上报方法做属性/事件映射，并通过 MQTT QoS 1 发布。
- 增加本地持久化待发队列、断线重连和补传。
- 提供 S1 产品、物模型和逐设备建档说明。

本次不包含：森泉向 MES 查询工单、标签和资料的五个下行方法；完整原始 JSON 写入 S1；修改 S1 数据库结构或新增 S1 接口。

## S1 预置

在 S1 后台创建产品 **“LYR森泉设备”**，并为每台现场设备分别创建设备。来源设备的 `data.device_code` 是默认映射键；如 S1 序列号不同，在 LYR 映射中显式填写。

### 属性模型

所有历史属性的值长度限制为 64 字符，避免超过 S1 当前 `iot_device_log.log_value` 的 `varchar(64)` 限制。

| 标识符 | 名称 | 类型和值 |
| --- | --- | --- |
| `run_status` | 运行状态 | 枚举：`0` 未知、`1` 运行、`2` 故障、`3` 待机 |
| `host_ip` | 设备 IP | 字符串，64 |
| `alarm_code` | 故障码 | 字符串，64 |
| `alarm_message` | 故障信息 | 字符串，64，超长时截断 |
| `work_order_no` | 批次/工单号 | 字符串，64 |
| `feed_status` | 上料状态 | 字符串，64 |
| `last_scan_result` | 最近扫码结果 | 字符串，64 |
| `last_scan_content` | 最近扫码内容 | 字符串，64，超长时截断 |
| `last_print_result` | 最近打印结果 | 枚举：`0` 失败、`1` 成功 |
| `software_version` | 软件版本 | 字符串，64 |

### 事件模型

创建 `device_login`、`status_changed`、`material_feed`、`scan_result`、`print_result`、`recipe_reported`、`version_reported` 七个事件。事件值是简短结果代码；备注为不超过 200 字符的摘要。完整森泉原始请求只保留在 LYR 本地 JSONL 日志中。

## 森泉到 S1 的映射

| 森泉方法 | S1 属性更新 | S1 事件 |
| --- | --- | --- |
| `login` | `host_ip` | `device_login` |
| `device_status` | `run_status`、`alarm_code`、`alarm_message` | `status_changed` |
| `feed_material` | `work_order_no`、`feed_status` | `material_feed` |
| `scan_result` | `last_scan_result`、`last_scan_content` | `scan_result` |
| `return_labels_result` | `work_order_no`、`last_print_result`、故障字段 | `print_result` |
| `carft_param` / `craft_param` | 无长期 JSON 属性 | `recipe_reported` |
| `version_info` | `software_version` | `version_reported` |

属性使用 `/{productId}/{serialNumber}/property/post` 发布；事件使用 `/{productId}/{serialNumber}/event/post` 发布。消息体均为 S1 的 `ThingsModelSimpleItem` 数组，单个项含 `id`、`value`、`ts` 和 `remark`。

## LYR 组件与配置

增加四个边界清晰的组件：

- `S1MqttSettingsStore`：读写 `s1_mqtt_config.json`。Broker 地址、端口和 TLS 是全局配置；每条设备映射带来源设备号、产品 ID、S1 序列号、client ID、用户名和受保护的密码。
- `S1EventMapper`：从已验证的森泉请求产生属性和事件消息，负责字段截断、状态转换和摘要。
- `S1Outbox`：将映射后的 MQTT 发布任务以原子方式写入 `data\\s1-pending-events.json`，维护重试时间和尝试次数。
- `S1MqttPublisher`：每个映射对应一个 MQTT 3.1.1 客户端会话，以 QoS 1 发布；仅在收到 PUBACK 后删除队列项。

为匹配现有 .NET Framework 4.x 的 `csc.exe` 构建方式，项目将随源码引入兼容 .NET Framework 的 M2Mqtt 客户端 DLL，并由 `build.cmd` 显式引用。密码将用当前 Windows 用户的 DPAPI 保护；日志、界面和异常信息不得输出密码或解密后的连接字符串。

## 数据流与故障语义

1. HTTP 服务校验森泉请求和方法，取得 `device_code`。
2. 对已配置映射的上报，在生成森泉成功响应前完成事件映射和待发队列落盘。
3. HTTP 服务按既有协议把响应返还设备；MQTT 发布在独立工作线程进行，不等待 S1。
4. 发布成功并收到 PUBACK 时删除队列项；网络异常、认证失败或 Broker 不可用时保留队列并递增退避重试，最大间隔为 60 秒。
5. 映射缺失时保留既有 MES 应答、在窗口和日志标记“未映射”，但不向错误设备发送数据。
6. 不能写入待发队列时返回 HTTP 500，不发送成功模板，以便设备按其重试策略再次上报；同时记录高优先级错误。

MQTT QoS 1 保证至少一次到达，不保证事件的跨进程“恰好一次”。属性上报天然幂等；设备重复上报或断线重传可能产生重复事件，LYR 使用来源请求 `id` 加方法和设备号在本地短期去重，缺少稳定请求 `id` 时保留至少一次语义。

## 界面与运维

主窗口顶部保留“服务配置”，新增“**S1 MQTT服务器配置**”：启用开关、Broker 地址、端口、TLS、设备映射按钮和连接状态。设备映射对话框维护每台设备的 S1 标识和 MQTT 凭据，密码始终以掩码显示。

状态栏展示已连接映射数、待发队列数、最近成功时间和最近错误。关闭程序时先停止 HTTP 接收，再让发布线程在有限时间内停止，未确认任务保持在待发文件中等待下次启动补传。

## 验收与测试

- 为七个森泉上报方法增加映射单元测试，校验 Topic、属性、事件、状态转换和字段截断。
- 为待发队列增加写入、重启恢复、PUBACK 删除、失败退避和重复请求去重测试。
- 使用可替换 MQTT 客户端接口进行单元测试；在联调环境使用一个 S1 测试产品和两台测试设备验证独立凭据、属性历史、事件日志和断网补传。
- 扩展布局测试，验证新标题、`1400 × 850` 客户端尺寸、S1 配置控件和密码掩码。
- 构建仍以 `gateway/hoson-mes-mock/build.cmd` 通过；不触碰工作区中既有的未提交改动。

## 依赖与部署前提

S1 的 MQTT Broker 必须可从 LYR 所在机器访问。每台设备必须在 S1 中创建并取得可用的 client ID、用户名、密码、产品 ID 和序列号。首次联调使用测试设备和测试 Broker；确认属性历史、事件日志和设备在线状态均符合预期后，再录入生产凭据。
