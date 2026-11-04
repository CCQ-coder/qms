package com.pcbqms.reporting.infrastructure.persistence;

import com.pcbqms.reporting.domain.FinishedTestFact;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FinishedTestAnalysisMapper {
    @Select("""
        <script>
        SELECT record.inspected_at, work_order.work_order_no,
               work_order.internal_product_no AS material_no, record.equipment_asset_no,
               record.defective_quantity, record.results_json
        FROM qms_inspection_record record
        JOIN qms_inspection_task task ON task.id = record.task_id AND task.tenant_code = record.tenant_code
        LEFT JOIN qms_work_order work_order ON work_order.id = task.work_order_id
            AND work_order.tenant_code = record.tenant_code
        WHERE record.tenant_code = #{tenantCode} AND task.process_code = 'FQC'
          AND record.inspected_at &gt;= #{fromTime}
        <if test="toExclusive != null">
          AND record.inspected_at &lt; #{toExclusive}
        </if>
        </script>
        """)
    List<FinishedTestFact> selectFacts(@Param("tenantCode") String tenantCode,
                                       @Param("fromTime") LocalDateTime fromTime,
                                       @Param("toExclusive") LocalDateTime toExclusive);
}
