package com.pcbqms.equipment.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EquipmentTelemetryRecordMapper {
    @Select("""
        SELECT DISTINCT equipment_id FROM (
            SELECT equipment_id FROM qms_equipment_telemetry
            WHERE tenant_code = #{tenantCode} AND event_type = 'FLYING_PROBE_TEST_COUNTERS'
            UNION
            SELECT equipment_id FROM qms_equipment_telemetry_minute_summary
            WHERE tenant_code = #{tenantCode} AND last_event_type = 'FLYING_PROBE_TEST_COUNTERS'
            UNION
            SELECT equipment_id FROM qms_equipment_telemetry_hour_summary
            WHERE tenant_code = #{tenantCode} AND last_event_type = 'FLYING_PROBE_TEST_COUNTERS'
        ) flying_probe_equipment
        """)
    List<String> selectFlyingProbeEquipmentIds(@Param("tenantCode") String tenantCode);

    /** MySQL performs the final idempotency check so gateway retries are concurrency-safe. */
    @Insert("""
        INSERT INTO qms_equipment_telemetry (
            id, tenant_code, equipment_id, source_username, machine_name, device_time, received_at,
            event_type, equipment_status, program_no, program_version, material_no, work_order_no, batch_no,
            operator_name, progress_percent, spindle_status, alarm_code, alarm_message, machining_count,
            source_record_no, deduplication_key, raw_payload, created_at
        ) VALUES (
            #{id}, #{tenantCode}, #{equipmentId}, #{sourceUsername}, #{machineName}, #{deviceTime}, #{receivedAt},
            #{eventType}, #{equipmentStatus}, #{programNo}, #{programVersion}, #{materialNo}, #{workOrderNo}, #{batchNo},
            #{operatorName}, #{progressPercent}, #{spindleStatus}, #{alarmCode}, #{alarmMessage}, #{machiningCount},
            #{sourceRecordNo}, #{deduplicationKey}, #{rawPayload}, #{createdAt}
        ) ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(EquipmentTelemetryRecord record);

    @Select("""
        SELECT id, tenant_code, equipment_id, source_username, machine_name, device_time, received_at,
               event_type, equipment_status, program_no, program_version, material_no, work_order_no, batch_no,
               operator_name, progress_percent, spindle_status, alarm_code, alarm_message, machining_count,
               source_record_no, deduplication_key, raw_payload, created_at
        FROM qms_equipment_telemetry
        WHERE tenant_code = #{tenantCode}
        ORDER BY received_at DESC
        LIMIT #{limit}
        """)
    List<EquipmentTelemetryRecord> selectRecentByTenant(@Param("tenantCode") String tenantCode, @Param("limit") int limit);

    @Select("""
        <script>
        SELECT id, tenant_code, equipment_id, source_username, machine_name, device_time, received_at,
               event_type, equipment_status, program_no, program_version, material_no, work_order_no, batch_no,
               operator_name, progress_percent, spindle_status, alarm_code, alarm_message, machining_count,
               source_record_no, deduplication_key, raw_payload, created_at
        FROM qms_equipment_telemetry
        WHERE tenant_code = #{tenantCode}
        <if test="equipmentId != null and equipmentId != ''">
            AND equipment_id = #{equipmentId}
        </if>
        <if test="equipmentIds != null and equipmentIds.size() > 0">
            AND equipment_id IN
            <foreach collection="equipmentIds" item="allowedEquipmentId" open="(" separator="," close=")">
                #{allowedEquipmentId}
            </foreach>
        </if>
        <if test="fromTime != null">
            AND received_at &gt;= #{fromTime}
        </if>
        <if test="toTime != null">
            AND received_at &lt;= #{toTime}
        </if>
        <if test="cursorReceivedAt != null">
            AND (received_at &lt; #{cursorReceivedAt}
                 OR (received_at = #{cursorReceivedAt} AND id &lt; #{cursorId}))
        </if>
        ORDER BY received_at DESC, id DESC
        LIMIT #{limit}
        </script>
        """)
    List<EquipmentTelemetryRecord> selectHistory(
        @Param("tenantCode") String tenantCode,
        @Param("equipmentId") String equipmentId,
        @Param("equipmentIds") List<String> equipmentIds,
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime,
        @Param("cursorReceivedAt") LocalDateTime cursorReceivedAt,
        @Param("cursorId") String cursorId,
        @Param("limit") int limit
    );
}
