# LYR森泉监听软件：S1 MQTT 建档与联调

LYR 不需要 S1 新增 HTTP 接口。每台现场设备在 S1 的“LYR森泉设备”产品下单独建档，并使用自己的 MQTT Client ID、用户名和密码。

## S1 产品模型

创建产品 `LYR森泉设备`，并添加以下属性（字符串历史值最多 64 字符）：`run_status`（`0`未知、`1`运行、`2`故障、`3`待机）、`host_ip`、`alarm_code`、`alarm_message`、`work_order_no`、`feed_status`、`last_scan_result`、`last_scan_content`、`last_print_result`（`0`失败、`1`成功）、`software_version`。

创建事件：`device_login`、`status_changed`、`material_feed`、`scan_result`、`print_result`、`recipe_reported`、`version_reported`。事件备注最多 200 字符。

## LYR 配置

在“**S1 MQTT服务器配置**”填写 Broker、端口、TLS，勾选启用转发。通过“设备映射”逐台录入森泉来源设备号（`data.device_code`）、S1 产品 ID、序列号、Client ID、用户名和密码。密码只由当前 Windows 用户的 DPAPI 保护保存，界面始终掩码显示。

- 属性 Topic：`/{productId}/{serialNumber}/property/post`
- 事件 Topic：`/{productId}/{serialNumber}/event/post`
- 发布使用 QoS 1；只在 PUBACK 后删除 `data\s1-pending-events.json` 中的记录。Broker 离线时以最多 60 秒的指数退避补传，因此事件为至少一次语义。

完整原始 JSON 仅写入本地 `logs\yyyy-MM-dd.jsonl`，不会传入 S1。

## token 与联调

设备先调用 `login`，LYR 返回设备专属 `LYR-` token。后续上报用 token 找回来源设备；token 空闲 24 小时失效，软件重启后也需重新登录。未知/过期 token 保留 MES 正常应答但不转发 S1。

先建两台测试设备：分别登录并上报 `device_status`，确认属性/事件落到正确设备；断开 Broker 再恢复，确认待发队列归零并补传。TLS 环境需让 LYR 主机信任 Broker 证书并放行出站 TCP 端口。
