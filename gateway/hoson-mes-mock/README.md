# 森泉 MES 独立 Mock 监听器

`HosonMesMock.exe` 是独立的 Windows 图形化 HTTP 测试服务，不依赖原 DEMO 的 `MesHttpServer.exe` 或 `jsoncpp.dll`。它监听 `POST /api/mes/invoke`，读取同目录下的 `mes_server_config.json`，按请求 JSON 的 `method` 返回对应应答。配置文件取自提供的“MES服务端DEMO_V1.0.zip”，包含 12 个方法的示例应答。

窗口提供监听 IP 和端口、服务启动与停止、各方法的 JSON 应答编辑，以及请求和响应记录。它仅模拟 MES 应答，不向 QMS 转发设备上报。记录只保存在程序内存中，可能包含请求中的密码或令牌；关闭程序或点击“清空日志”即可清除。

## 使用

1. 在 Windows 上双击 `start-mock.cmd` 或 `HosonMesMock.exe`。程序启动时不会自动监听；默认 IP 为 `0.0.0.0`、端口为 `6680`，可在窗口中修改后点击“启动服务”。
2. 把森泉设备的 MES 地址设为 `http://<监听电脑IP>:6680/api/mes/invoke`，按实际端口调整。`0.0.0.0` 表示监听所有网卡，设备地址应填写这台电脑实际可访问的 IP。程序只接收 HTTP POST JSON。
3. 在方法列表中选择接口，编辑右侧应答 JSON 后点击“保存修改”。新应答立即生效，并写入 `mes_server_config.json`；“恢复默认”只把当前方法的编辑框恢复到随程序附带的 `mes_server_defaults.json` 内容，还需点击“保存修改”才会生效。
4. 下方列表显示收到的请求。点击一条记录可查看请求和响应正文；点击“停止服务”结束监听。

自动化测试或无图形界面的环境可运行 `HosonMesMockCli.exe 127.0.0.1 6680`，也可执行 `HosonMesMock.exe 127.0.0.1 6680` 或 `start-mock.cmd 127.0.0.1 6680`。带 IP 和端口参数时使用原有无界面监听模式，配置在启动时读取，修改后需重启。原始示例应答中的 `id`、`timestamp`、工单、标签和路径是固定值，不会自动替换为请求值。

如果使用 `0.0.0.0`，首次启动可能需要以管理员身份运行以下命令，并放行入站端口：

```bat
netsh http add urlacl url=http://+:6680/ user=Everyone
netsh advfirewall firewall add rule name="Hoson MES Mock 6680" dir=in action=allow protocol=TCP localport=6680
```

公司安全策略不允许 `Everyone` 时，将 URL ACL 授予实际运行程序的 Windows 账号。特定 IP 监听时，URL ACL 的地址也应改成该 IP。

配置中含模拟工单、标签及路径，`scan_result` 的默认应答为 `success=false`。请在隔离的测试设备或测试网段使用，避免把示例业务数据发给生产设备。

## 自行编译

在安装 .NET Framework 4.x 的 Windows 电脑上运行 `build.cmd`，产物位于项目根目录的 `outputs\hoson-mes-mock`。重复编译会保留该目录已有的 `mes_server_config.json` 修改。`test-mock.sh` 可在有 Mono 和 curl 的环境中验证 HTTP 接口及响应热更新。
