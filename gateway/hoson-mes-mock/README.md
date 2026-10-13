# 森泉 MES 独立 Mock 监听器

`HosonMesMock.exe` 是独立的 Windows HTTP 测试服务，不依赖原 DEMO 的 `MesHttpServer.exe` 或 `jsoncpp.dll`。它监听 `POST /api/mes/invoke`，读取同目录下的 `mes_server_config.json`，按请求 JSON 的 `method` 返回对应的固定应答。配置文件直接取自提供的“MES服务端DEMO_V1.0.zip”，包含 12 个方法的示例应答。

这个版本仅模拟 MES 应答，不向 QMS 转发设备上报。窗口显示请求来源 IP、方法和 HTTP 状态；不会打印可能含密码的完整请求正文。

## 使用

1. 在 Windows 上双击 `start-mock.cmd`，输入本机监听 IPv4 和端口。直接回车分别使用 `127.0.0.1` 和 `6680`；设备从其他电脑访问时，填监听电脑的局域网 IP，或输入 `0.0.0.0` 监听所有网卡。
2. 把森泉设备的 MES 地址设为 `http://<监听电脑IP>:6680/api/mes/invoke`，按设备实际端口调整。程序只接收 HTTP POST JSON。
3. 在窗口中查看收到的 `method` 与 HTTP 状态。按 `Ctrl+C` 停止。

也可直接执行 `HosonMesMock.exe 192.168.1.71 6680`。修改 `mes_server_config.json` 后，重启程序加载新的应答。应答中的 `id`、`timestamp`、工单、标签和路径均为 DEMO 固定示例值，不会自动替换为请求值。

如果使用 `0.0.0.0`，首次启动可能需要以管理员身份运行以下命令，并放行入站端口：

```bat
netsh http add urlacl url=http://+:6680/ user=Everyone
netsh advfirewall firewall add rule name="Hoson MES Mock 6680" dir=in action=allow protocol=TCP localport=6680
```

公司安全策略不允许 `Everyone` 时，将 URL ACL 授予实际运行程序的 Windows 账号。特定 IP 监听时，URL ACL 的地址也应改成该 IP。

配置中含模拟工单、标签及路径，`scan_result` 的默认应答为 `success=false`。请在隔离的测试设备或测试网段使用，避免把示例业务数据发给生产设备。

## 自行编译

在安装 .NET Framework 4.x 的 Windows 电脑上运行 `build.cmd`，产物位于项目根目录的 `outputs\hoson-mes-mock`。`test-mock.sh` 可在有 Mono 和 curl 的环境中验证 HTTP 接口。
