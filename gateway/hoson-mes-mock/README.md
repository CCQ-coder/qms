# LYR森泉监听软件

`HosonMesMock.exe` 是独立的 Windows 图形化森泉 HTTP 监听服务。它监听 `POST /api/mes/invoke`，按请求 JSON 的 `method` 返回 MES 应答，并可将处理后的设备数据用 MQTT QoS 1 发送到 S1。

窗口提供监听 IP 和端口、S1 MQTT 服务和逐设备映射、JSON 应答编辑，以及请求和响应记录。S1 配置为 `s1_mqtt_config.json`，待发队列为 `data\s1-pending-events.json`。完整原始 JSON 仍只记录在 `logs\yyyy-MM-dd.jsonl`。S1 建档与联调步骤见 [S1_SETUP.md](S1_SETUP.md)。

## 使用

1. 在 Windows 上双击 `start-mock.cmd` 或 `HosonMesMock.exe`。程序会立即监听 `0.0.0.0:4523`，状态显示为“运行中”；不需要配置 IP、端口或点击“启动服务”。
2. 把森泉设备的 MES 地址设为 `http://<监听电脑IP>:4523/api/mes/invoke`。`0.0.0.0` 表示监听所有网卡，设备地址应填写这台电脑实际可访问的 IP。程序只接收 HTTP POST JSON。
3. 在方法列表中选择接口，编辑右侧应答 JSON 后点击“保存修改”。新应答立即生效，并写入 `mes_server_config.json`；“恢复默认”只把当前方法的编辑框恢复到随程序附带的 `mes_server_defaults.json` 内容，还需点击“保存修改”才会生效。
4. 下方列表显示收到的请求。点击一条记录可查看请求和响应正文；如需手动结束监听，点击“停止服务”。

每次应答都会把 `timestamp` 替换为当前 Unix 毫秒时间；请求含 `id` 时会原样回显，未提供时自动生成唯一数值 ID。设备专用响应配置写在同目录的 `mes_server_device_config.json`：按 `data.device_code`（也兼容顶层 `device_code`）匹配，未匹配的设备仍使用 `mes_server_config.json` 中的通用响应。例如：

```json
{
  "DEVICE-001": {
    "login": { "data": { "token": "device-001-token" } }
  }
}
```

自动化测试或无图形界面的环境可运行 `HosonMesMock.exe 127.0.0.1 4523` 或 `start-mock.cmd 127.0.0.1 4523`。主程序已包含无界面监听功能，发布构建不再生成独立的 `HosonMesMockCli.exe`。带 IP 和端口参数时使用原有无界面监听模式，配置在启动时读取，修改后需重启。示例应答中的工单、标签和路径是固定值；运行时会覆盖模板中的 `id` 和 `timestamp`。

程序使用直接 TCP 监听，不依赖 HTTP.sys URL ACL 配置。若设备在其他电脑上且系统防火墙拦截连接，只需按现场安全策略放行 TCP 入站端口 `4523`。

配置中含模拟工单、标签及路径，`scan_result` 的默认应答为 `success=false`。请在隔离的测试设备或测试网段使用，避免把示例业务数据发给生产设备。

## 自行编译

在安装 .NET Framework 4.x 的 Windows 电脑上运行 `build.cmd`，产物位于项目根目录的 `outputs\hoson-mes-mock`。重复编译会保留该目录已有的 `mes_server_config.json` 修改。运行 `test-layout.cmd` 可验证 HTTP 请求解析、GUI 的自动启动、控件状态和 52/48 自适应布局（执行前请确保端口 4523 未被占用）。`test-mock.sh` 可在有 Mono 和 curl 的环境中验证 HTTP 接口及响应热更新。
