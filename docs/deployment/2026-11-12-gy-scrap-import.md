# 共赢 QMS 报废明细导入与设备批量关联发布

- 分支 `gy`，提交 `3dd2881`、`88ee99c`，服务器 `106.227.69.144`。后端 `/data/qms/qms-api.jar`，前端 `/data/1panel/www/sites/qms/index/dist`。
- V53 增加报废 Excel 导入去重和进度字段、未关联报废明细字段、设备记录手工取消关联标记。线上 Flyway V53 成功，健康接口返回 `UP`。
- 首次发布前完整备份：`/root/qms-backups/gy-scrap-import-20261112-135648/`，含数据库、上传目录、JAR、前端和配置，压缩包完整性通过。反馈修正发布前程序备份：`/root/qms-backups/gy-scrap-import-followup-20261112-1401/`。发布包分别存于 `/root/qms-release-scrap-import-20261112-1357/` 和 `/root/qms-release-scrap-import-final-20261112/`。
- 最终线上后端 SHA-256 `87915244e9f0aae3784c9dc90eb62873fdda000d0082ab100af0b24efaf41469`，前端 `index.html` SHA-256 `13a3256b7e0d0f1550b28fc10c5a159c18f3ad5648783415e4af436429d387d8`；公网响应和本地构建一致。
- 后端 236 项、前端 32 项测试通过，生产构建通过。公网新导入接口未登录返回 401。样表只读解析 400 行、0 错误；未将样表上传生产环境。
- 常规程序回退使用上述 JAR 与前端备份并重启 `qms`；V53 是加列及放宽空值约束，旧程序可读取原有数据。恢复整库会覆盖备份之后的业务写入，不应用作普通回退。
