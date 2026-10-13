# 客户 B 数据同步网关

该程序部署在客户 B 内网，使用**只读**数据库账号读取工单数据，再通过 HTTPS 主动推送到云端 QMS；云端不需要连接客户内网。

## 配置步骤

1. 网关默认以 MySQL 8 只读连接 `customer_erp`；通过 `SOURCE_DB_URL`、`SOURCE_DB_USERNAME` 和 `SOURCE_DB_PASSWORD` 配置客户 B 的实际 MySQL 实例。将 `gateway.source-sql` 改为客户 B 实际数据库查询，必须输出以下别名：
   `work_order_no`、`product_code`、`product_name`、`specification`、`current_process_code`、`current_process_name`、`planned_quantity`、`batch_no`、`source_key`、`customer_code`、`internal_product_no`、`customer_part_no`、`process_technology`、`material`、`board_thickness`、`copper_thickness`、`warehouse_status`、`material_input_date`、`pcs_length`、`pcs_width`、`material_no`、`material_name`、`material_specification`、`standard_material_consumption`、`cumulative_warehouse_quantity`、`required_delivery_date`、`production_notice_no`、`input_date`、`single_pcs_area`、`a_board_length`、`a_board_width`、`b_board_length`、`b_board_width`、`forming_method`、`layer_count`、`production_notice_quantity`、`customer_order`、`customer_order_no`、`customer_order_quantity`、`material_area`、`production_notice_area`、`customer_order_area`、`source_updated_at`。
   如 ERP 已维护优先级、工艺路线版本或 MI 版本，可额外输出 `priority`、`process_route_version`、`mi_version`；这三个别名为可选，不提供时 QMS 保留现有值或使用默认值。
   需要展示截图中的拼板与投料信息时，可额外输出 `total_panel_quantity`、`a_board_panel_quantity`、`b_board_panel_quantity`、`panelization_no`、`estimated_yield_rate`、`planned_material_issue_quantity`；这些别名同样为可选。
   生产指示资料中黄色必填项新增别名为 `material_input_quantity`、`cumulative_scrap_quantity`、`cumulative_defect_quantity`、`pnl_length`、`pnl_width`、`production_notice_order_quantity`、`unreceived_quantity`、`last_warehouse_date`；为便于既有 ERP 平滑升级，这些别名暂为可选。
2. 设置 `SOURCE_DB_*`、`QMS_API_BASE_URL`、`QMS_TENANT_CODE=customer-b` 和 `QMS_GATEWAY_SHARED_TOKEN`。
3. `QMS_GATEWAY_SHARED_TOKEN` 必须与云端 QMS 的 `QMS_GATEWAY_SHARED_TOKEN` 保持一致。启用 JWT 认证后，还需将 QMS 为相应租户服务账号签发的访问令牌配置为 `QMS_GATEWAY_BEARER_TOKEN`；令牌必须具备 `tenant_code=customer-b` 声明，过期后需重新获取并更新。
4. 首次启动会从最近一天开始同步；生产启用前应按客户上线窗口预置 `GATEWAY_CURSOR_FILE`，避免全量重复读取。可通过 `GATEWAY_CURSOR_OVERLAP_SECONDS` 调整重叠读取窗口，默认 10 秒。

网关会在整批工单全部成功推送后原子更新同步游标；任一条失败都会保留原游标并在下次调度时重试。重叠窗口会带来少量重复推送，但 QMS 按工单号幂等更新，不会生成重复工单。生产版本应补充失败告警、加密本地配置和 TLS 证书校验。
