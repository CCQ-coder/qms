package com.pcbqms.inspection.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.inspection.domain.InspectionRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {
    @Select("""
        <script>
        SELECT record.*<if test="includeTotal">, COUNT(*) OVER() AS total_count</if> FROM qms_inspection_record record
        JOIN qms_inspection_task task ON task.id = record.task_id AND task.tenant_code = record.tenant_code
        LEFT JOIN qms_work_order work_order ON work_order.id = task.work_order_id
          AND work_order.tenant_code = record.tenant_code
        WHERE record.tenant_code = #{tenantCode} AND task.status = 'COMPLETED'
          AND (task.task_type = 'FINISHED' OR task.process_code = 'FQC')
          AND record.inspected_at >= #{dataStart}
        <if test="equipmentAssetNo != null">
          AND record.equipment_asset_no = #{equipmentAssetNo}
        </if>
        <if test="workOrderNo != null">
          AND work_order.work_order_no LIKE CONCAT('%', #{workOrderNo}, '%')
        </if>
        <if test="customerOrderNo != null">
          AND work_order.customer_order_no LIKE CONCAT('%', #{customerOrderNo}, '%')
        </if>
        <if test="internalProductNo != null">
          AND work_order.internal_product_no LIKE CONCAT('%', #{internalProductNo}, '%')
        </if>
        <if test="processCodes != null">
          AND task.process_code IN
          <foreach collection="processCodes" item="code" open="(" separator="," close=")">#{code}</foreach>
        </if>
        <if test="cursorTime != null">
          AND (record.inspected_at &lt; #{cursorTime}
               OR (record.inspected_at = #{cursorTime} AND record.id &lt; #{cursorId}))
        </if>
        ORDER BY record.inspected_at DESC, record.id DESC
        <choose>
          <when test="offset != null">LIMIT #{limit} OFFSET #{offset}</when>
          <otherwise>LIMIT #{limit}</otherwise>
        </choose>
        </script>
        """)
    List<InspectionRecord> selectFinishedPage(
        @Param("tenantCode") String tenantCode, @Param("dataStart") LocalDateTime dataStart,
        @Param("processCodes") List<String> processCodes,
        @Param("equipmentAssetNo") String equipmentAssetNo,
        @Param("workOrderNo") String workOrderNo,
        @Param("customerOrderNo") String customerOrderNo,
        @Param("internalProductNo") String internalProductNo,
        @Param("cursorTime") LocalDateTime cursorTime, @Param("cursorId") String cursorId,
        @Param("limit") int limit, @Param("offset") Long offset,
        @Param("includeTotal") boolean includeTotal);
}

