package com.pcbqms.gateway.infrastructure;

import com.pcbqms.gateway.config.GatewayProperties;
import com.pcbqms.gateway.domain.GatewayWorkOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ErpWorkOrderReader {
    private final JdbcTemplate jdbcTemplate;
    private final GatewayProperties properties;

    public List<GatewayWorkOrder> findChangedAfter(LocalDateTime cursor) {
        return jdbcTemplate.query(properties.getSourceSql(), (resultSet, rowNumber) -> new GatewayWorkOrder(
            resultSet.getString("work_order_no"), resultSet.getString("product_code"), resultSet.getString("product_name"),
            resultSet.getString("specification"), resultSet.getString("current_process_code"), resultSet.getString("current_process_name"),
            resultSet.getInt("planned_quantity"), resultSet.getString("batch_no"), resultSet.getString("source_key"),
            resultSet.getString("customer_code"), resultSet.getString("internal_product_no"), resultSet.getString("customer_part_no"),
            resultSet.getString("process_technology"), resultSet.getString("material"), resultSet.getString("board_thickness"),
            resultSet.getString("copper_thickness"), resultSet.getString("warehouse_status"), date(resultSet, "material_input_date"),
            resultSet.getBigDecimal("pcs_length"), resultSet.getBigDecimal("pcs_width"), resultSet.getString("material_no"),
            resultSet.getString("material_name"), resultSet.getString("material_specification"),
            resultSet.getBigDecimal("standard_material_consumption"), integer(resultSet, "cumulative_warehouse_quantity"),
            date(resultSet, "required_delivery_date"), resultSet.getString("production_notice_no"), date(resultSet, "input_date"),
            resultSet.getBigDecimal("single_pcs_area"), resultSet.getBigDecimal("a_board_length"), resultSet.getBigDecimal("a_board_width"),
            resultSet.getBigDecimal("b_board_length"), resultSet.getBigDecimal("b_board_width"), resultSet.getString("forming_method"),
            integer(resultSet, "layer_count"), integer(resultSet, "production_notice_quantity"), resultSet.getString("customer_order"),
            resultSet.getString("customer_order_no"), integer(resultSet, "customer_order_quantity"),
            resultSet.getBigDecimal("material_area"), resultSet.getBigDecimal("production_notice_area"), resultSet.getBigDecimal("customer_order_area"),
            optionalString(resultSet, "priority"), optionalString(resultSet, "process_route_version"), optionalString(resultSet, "mi_version"),
            optionalInteger(resultSet, "total_panel_quantity"), optionalInteger(resultSet, "a_board_panel_quantity"),
            optionalInteger(resultSet, "b_board_panel_quantity"), optionalString(resultSet, "panelization_no"),
            optionalDecimal(resultSet, "estimated_yield_rate"), optionalDecimal(resultSet, "planned_material_issue_quantity"),
            optionalInteger(resultSet, "material_input_quantity"), optionalInteger(resultSet, "cumulative_scrap_quantity"),
            optionalInteger(resultSet, "cumulative_defect_quantity"), optionalDecimal(resultSet, "pnl_length"),
            optionalDecimal(resultSet, "pnl_width"), optionalInteger(resultSet, "production_notice_order_quantity"),
            optionalInteger(resultSet, "unreceived_quantity"), optionalDate(resultSet, "last_warehouse_date"),
            resultSet.getTimestamp("source_updated_at").toLocalDateTime()
        ), cursor);
    }

    private static LocalDate date(ResultSet resultSet, String column) throws SQLException {
        java.sql.Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Integer integer(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, Integer.class);
    }

    /** Optional aliases keep existing ERP queries valid while source systems add QMS traceability fields. */
    private static String optionalString(ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static Integer optionalInteger(ResultSet resultSet, String column) {
        try {
            return resultSet.getObject(column, Integer.class);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static java.math.BigDecimal optionalDecimal(ResultSet resultSet, String column) {
        try {
            return resultSet.getBigDecimal(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static LocalDate optionalDate(ResultSet resultSet, String column) {
        try {
            return date(resultSet, column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
