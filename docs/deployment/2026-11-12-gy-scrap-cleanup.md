# 共赢 QMS 报废数据清理与表单更新

- 分支 `gy`，提交 `90f48ce`、`56bf747`；发布到 `106.227.69.144`。后端 JAR SHA-256 `155a1e9805f2d0830fa49263dbe57e5345df44043d3574c44fd0b9d315ce467c`，前端 `index.html` SHA-256 `ab76af7e3c3bd7d98dcee312beab8bdf2960bd936f5bae5e9be4811e29b9cf87`。
- 按用户确认，仅清理 `customer-b` 的 `qms_nonconformity` 和 `qms_nonconformity_detail`，清理前各 69 条，清理后各 0 条。`qms_capa` 原有 0 条。原数据备份在 `/root/qms-backups/gy-scrap-clear-20261112/`，两张表的导出均核对为 69 行。保留 `qms_source_document` 的一条已完成报废导入任务；同一文件的再次上传仍会按原有去重逻辑返回已完成任务。
- 将租户参数 `quality.auto-create-ncr` 显式设为 `false`。此前 51 条设备来源报废单创建于 2026-11-10 至 2026-11-12 11:03；当前代码的设备处置同步方法没有生产调用者，缺省参数也按关闭处理。
- V54 为报废明细增加手工料号字段。导入报废单编号改为通用 `NCR-YYYYMMDD-序号`。前端调整筛选按钮位置、模板下载入口和手工填写字段，并移除行号展示。
- 发布前备份程序文件在 `/root/qms-backups/gy-scrap-ui-20261112-1720/`。后端 241 项、前端 31 项测试通过，前端生产构建通过。线上 Flyway V54 成功，健康检查 `UP`，页面和模板接口返回 200。线上页面核对可选面积初始为空，筛选字段可见。
