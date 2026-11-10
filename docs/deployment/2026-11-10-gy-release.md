# 共赢 QMS 2026-11-10 部署记录

- 发布 `gy` 提交 `aca35bc` 至 `106.227.69.144`：后端 `/data/qms/qms-api.jar`，前端 `/data/1panel/www/sites/qms/index/dist`。
- 发布前备份位于服务器 `/root/qms-backups/gy-deploy-20261110-092843/`，包含原 JAR、前端、配置、上传目录和 `qms` 数据库转储。回退程序可使用原 JAR 和前端备份；整库恢复会覆盖备份后的业务写入，不能作为普通回退。
- 发布前发现 `qms.flyway_schema_history` 及 4 张业务表缺失。核对 V43–V47 结构后建立 V47 基线；从 `/root/qms-backups/flying-probe-manual-20261102-175940/qms.sql.gz` 恢复 `qms_administration_item`、`qms_administration_audit_log`、`qms_capa`、`qms_cnc_program_release`。其中菜单表恢复 36 条，其余 3 表在该备份中为空。由于缺失期间的表数据无法从当前库取得，需由业务方核对 11 月 2 日后的菜单配置。
- 首次执行 V48 时，因菜单表缺失而留下部分 DDL 和失败记录；撤销这些半成品变更后重新迁移，V48、V49 均成功。恢复用临时数据库已删除。
- 线上 QMS 容器发布端口为主机 `8180`。同步修正 QMS OpenResty 代理转发端口，并将 `.env` 的 `QMS_WEB_CORS_ALLOWEDORIGINS` 改为导出环境变量；Nginx 配置测试通过后已重载。
- 验证：后端 192 项测试、前端 25 项测试及生产构建通过；公网首页、JS/CSS、API 健康检查返回 200；空登录请求直连和公网均返回 400 并带正确 CORS 来源；无令牌设备上传返回 401。

## 14:30 不良品处理与工单状态更新

- 从 `gy` 工作区构建发布至同一台共赢服务器。后端 `/data/qms/qms-api.jar` SHA-256 为 `7f33c27a1bfc631fafb2b9c032a27adc358afe9509c22f8569558deb62e66973`；最终前端 `index.html` SHA-256 为 `09a3a46a0c63089d4f2cd3f90dd8da1f54a6b104d589b803bf484b918baa18a8`。
- 发布前完整备份位于 `/root/qms-backups/gy-deploy-20261110-142804/`，包含原 JAR、前端、应用配置、上传目录和 `qms` 数据库转储，数据库压缩包已通过完整性检查。发布包留在 `/root/qms-release-20261110-1428/`。
- 后端重启后，Flyway V50、V51 均成功；数据库新增不良品明细人工调整标记和工序责任部门字段。共赢生产库的成品测试工序为编码 `001`、名称 `FQC`，前端最终版本按现有工序资料选中它，并在未配置映射时默认带入“测试”。
- 验证：后端 200 项测试、前端 30 项测试和生产构建通过；公网首页及最终 JS/CSS 返回 200，空登录请求返回预期 400，容器健康检查返回 `UP`，启动后无错误日志。未用真实账号执行页面操作。
