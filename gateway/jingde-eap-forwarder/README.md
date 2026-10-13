# LYR 设备数据监听

`JingdeEapForwarder.exe` 是部署在设备网段中间电脑上的独立 Windows 程序。界面名称为“LYR 设备数据监听”，可同时接收精德、森泉与科视的 HTTP POST 数据；每一路先写入本地持久化队列，再异步转发至 QMS 统一遥测接口。

默认启动的是“LYR 设备数据监听”窗口：可实时查看设备原始 JSON、转换后的 QMS 事件、入队状态、转发成功或待重试原因。点击“服务配置”可分别勾选是否启用精德、森泉、科视端口，并设置对应监听地址、设备 IP 白名单、QMS 地址/令牌、重试和显示策略；保存后程序会自动重新启动监听服务。

设备收到成功响应的条件是网关已将数据写入本地队列，而不是 QMS 已立即处理。因此 QMS 暂时不可用、重启或网络中断时不会丢失设备上报；QMS 恢复后程序会自动补传。

## 支持的设备接口

| 协议 / 默认监听端口 | 设备请求 | 归一化到 QMS 的事件 | 关键处理 |
| --- | --- | --- | --- |
| 精德 EAP / `8002` | `POST /RestAPI/ZJDataCollectionReport` | `PRODUCTION_PARAMETERS` | 保存图号、生产起止时间、参数清单、断刀清单原文 |
| 精德 EAP / `8002` | `POST /RestAPI/EQPAlarmReport` | `ALARM_RAISED` / `ALARM_CLEARED` | Set 映射为 QMS `ALARM`；Clear 保留为事件，不擅自覆盖随后设备状态 |
| 精德 EAP / `8002` | `POST /RestAPI/EQPStatusChangeReport` | `STATUS_CHANGED` | `1=IDLE`、`2=PROCESSING`、`3=ALARM`、`4=STOPPED` |
| 森泉 MES / `6680` | JSON `method=login`、`device_status`、`feed_material`、`scan_result`、`return_labels_result`、`carft_param`、`version_info` | 登录、状态、上料、扫码、打印结果、配方与版本事件 | `device_code` 是设备映射键；`1=运行`、`2=故障`、`3=待机`。扫码响应需在配置中明确 `is_ng`，默认拒绝。 |
| 科视 LDI / `9000` | `/Status`、`/OEE`、`/Alarm`、`/ExposureResult`、`/HeartBeat`、`/Mode` | 状态、OEE、报警、曝光结果、心跳、模式事件 | `MachineNO`（曝光结果用 `CMACHINE_CODE`）是设备映射键；`WORK/WAIT/STOP/ALARM` 映射 QMS 状态。 |

完整的精德原始报文会随事件存入 QMS 的 `attributes.rawEapEnvelope`，便于追溯和后续补充字段映射。

## 首次部署

1. 在 Windows 电脑上运行 `build.cmd`，将生成的 `outputs\jingde-eap-forwarder` 文件夹复制到中间电脑，例如 `C:\QmsGateway\Jingde`。
2. 在 QMS 对应租户中创建一个专供监听程序使用的低权限账号并设置密码；当前可选“检验员”角色并按 QMS 的账号规则指定工序。双击 `start-gateway.cmd` 打开监控窗口，点击“服务配置”：填写 QMS 地址、网关令牌、租户编码、服务用户名和密码，并确认精德、森泉、科视的监听地址分别与设备端配置一致。密码由当前 Windows 账号加密保存在 `JingdeEapForwarder.ini`；后台以同一 Windows 账号运行。无界面部署也可设置 `QMS_SERVICE_PASSWORD` 环境变量，并在 INI 中填写租户编码、用户名及网关令牌。
3. 在 QMS 的“设备网关映射”中，将精德 `EquipmentID`、森泉 `device_code`、科视 `MachineNO`（曝光结果为 `CMACHINE_CODE`）分别配置为来源用户名和设备名称。网关使用该值关联 QMS 设备。
4. 以管理员身份在中间电脑放行 HTTP URL 和防火墙。若三路均监听所有网卡，可执行：

   ```bat
   netsh http add urlacl url=http://+:8002/ user=Everyone
   netsh advfirewall firewall add rule name="QMS Jingde EAP HTTP 8002" dir=in action=allow protocol=TCP localport=8002
   netsh http add urlacl url=http://+:6680/ user=Everyone
   netsh advfirewall firewall add rule name="QMS Hoson MES HTTP 6680" dir=in action=allow protocol=TCP localport=6680
   netsh http add urlacl url=http://+:9000/ user=Everyone
   netsh advfirewall firewall add rule name="QMS KST LDI HTTP 9000" dir=in action=allow protocol=TCP localport=9000
   ```

   若公司的安全策略不允许 `Everyone`，请由运维将 URL ACL 授予实际运行网关的 Windows 帐号；三组 `AllowedDeviceIps` 也应分别填写设备 IP 白名单。
5. 运行 `start-gateway.cmd`。首次联调时可先运行 `JingdeEapForwarder.exe --self-test`，确认自检退出码为 `0`。
6. 确认日志出现 `Accepted ...` 与 `Forwarded ... to QMS` 后，再以管理员身份运行 `install-autostart.cmd` 配置开机启动。

## 测试监听

运行 `ListenerProbe.exe`，按提示输入监听电脑 IP、端口、协议（`jingde`、`hoson`、`kst`）以及已在 QMS 映射的测试设备编码。也可在命令行运行：

```bat
ListenerProbe.exe 192.168.1.71 8002 jingde JD-TEST-01
ListenerProbe.exe 192.168.1.71 6680 hoson HOSON-TEST-01
ListenerProbe.exe 192.168.1.71 9000 kst KST-TEST-01
```

工具会发送一条状态或登录样例，显示 HTTP 响应。出现 `Accepted into listener queue` 表示监听器已接收并写入本地队列；再查看监听窗口或日志中的 `Forwarded ... to QMS`，才能确认转发成功。测试报文会成为真实记录，请使用已映射的测试设备编码，避免改变生产设备状态。

## 配置与运维

- 默认图形窗口展示完整数据流：`设备请求 -> 本地队列 -> QMS 接口`。选中任一行可查看原始 EAP 报文和转换后的 QMS JSON；“立即补传”可立即尝试发送本地队列。
- “显示列数/显示行数”用于控制窗口内实时流缓存容量；“空闲断线阈值”会在状态栏提示设备超过设定秒数未上报。HTTP 是无状态协议，因此提示表示“长时间未收到上报”，而不是 TCP 连接被强制断开。
- 需要只在后台运行时，用 `start-gateway.cmd --headless`；后台模式仍保存日志和本地队列，但不打开窗口。
- `logs\jingde-eap-forwarder.log`：当天的请求接收、转发成功和失败原因；跨天后会自动归档到 `logs\archive`，并按“本地记录保留（天）”清理历史归档。
- `data\pending-events.json`：待 QMS 补传的数据。程序运行期间不要删除或修改；QMS 返回成功后会自动移除对应数据。
- 转发前监听程序调用 QMS 的 `/api/v1/auth/login` 获取租户范围内的短期 Bearer 令牌，同时提交 `X-Gateway-Token`；令牌临近过期时自动重新登录。若 QMS 返回 401，会重新登录并仅重试该批一次；认证仍失败时保留本地队列，不会丢弃事件。请勿使用日常管理员账号作为服务账号。
- QMS 对已经入库的相同报文返回 HTTP 200 与 `duplicateCount`；监听程序据此确认该批已送达并清除本地待补传项，避免反复发送。数据库唯一键保证相同设备、设备时间和记录号只入库一次。
- 设备按文档须使用 `POST`，正文为 JSON。精德只接收文档中的 3 个 `/RestAPI/*` 方法，并回传文档要求的 `Header`、`Body`、`Result` 结构；森泉按 JSON `method` 路由；科视按 HTTP 路径路由。
- `TransactionID` 是网关的去重记录号的一部分；请设备端按文档生成唯一且稳定的事务号。其前 14 位若为 `yyyyMMddHHmmss`，网关会将其用作事件时间。

## 限制

这版工具负责“设备主动上报 -> QMS”。森泉协议中的 `get_job_name_by_scan`、`check_job_info`、`build_job_info`、`get_labels`、`get_job_path_by_scan` 是 MES 向设备提供工单、标签、路径的业务查询；没有 QMS 对应的业务响应来源时，监听器会明确拒绝而不会伪造数据。拿到业务规则和数据来源后，可在同一网关补上这些响应。
