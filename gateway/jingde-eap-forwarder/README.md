# 设备监听器已迁移

精德 EAP、森泉 MES 与科视 LDI 的 Windows 设备监听器现由独立项目维护：

- 源码仓库：<https://github.com/CCQ-coder/qms-edge-agent>
- 首个抽离版本：`v1.0.0-extracted`

QMS 不再构建、发布或维护该监听器的源码。监听器仍通过 QMS Telemetry v1 接口
`POST /api/v1/equipment/telemetry` 上报事件，并继续使用 `X-Gateway-Token` 认证。

请不要在此目录恢复或新增可编辑的监听器源码、构建脚本或现场配置；所有后续变更必须在
`qms-edge-agent` 仓库完成并按其发布流程交付。
