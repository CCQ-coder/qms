# 共赢 QMS 2026-11-10 部署记录

- 发布 `gy` 提交 `aca35bc` 至 `106.227.69.144`：后端 `/data/qms/qms-api.jar`，前端 `/data/1panel/www/sites/qms/index/dist`。
- 发布前备份位于服务器 `/root/qms-backups/gy-deploy-20261110-092843/`，包含原 JAR、前端、配置、上传目录和 `qms` 数据库转储。回退程序可使用原 JAR 和前端备份；整库恢复会覆盖备份后的业务写入，不能作为普通回退。
- 发布前发现 `qms.flyway_schema_history` 及 4 张业务表缺失。核对 V43–V47 结构后建立 V47 基线；从 `/root/qms-backups/flying-probe-manual-20261102-175940/qms.sql.gz` 恢复 `qms_administration_item`、`qms_administration_audit_log`、`qms_capa`、`qms_cnc_program_release`。其中菜单表恢复 36 条，其余 3 表在该备份中为空。由于缺失期间的表数据无法从当前库取得，需由业务方核对 11 月 2 日后的菜单配置。
- 首次执行 V48 时，因菜单表缺失而留下部分 DDL 和失败记录；撤销这些半成品变更后重新迁移，V48、V49 均成功。恢复用临时数据库已删除。
- 线上 QMS 容器发布端口为主机 `8180`。同步修正 QMS OpenResty 代理转发端口，并将 `.env` 的 `QMS_WEB_CORS_ALLOWEDORIGINS` 改为导出环境变量；Nginx 配置测试通过后已重载。
- 验证：后端 192 项测试、前端 25 项测试及生产构建通过；公网首页、JS/CSS、API 健康检查返回 200；空登录请求直连和公网均返回 400 并带正确 CORS 来源；无令牌设备上传返回 401。
