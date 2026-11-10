package com.pcbqms.equipment.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetryProjectionStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EquipmentTelemetryRecordMapper {
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

    /** Affected-row counts for no-op upserts vary with MySQL CLIENT_FOUND_ROWS. Check the persisted UUID. */
    @Select("""
        SELECT id FROM qms_equipment_telemetry
        WHERE tenant_code = #{tenantCode} AND equipment_id = #{equipmentId}
          AND deduplication_key = #{deduplicationKey}
        LIMIT 1
        """)
    String selectIdByDeduplicationKey(
        @Param("tenantCode") String tenantCode, @Param("equipmentId") String equipmentId,
        @Param("deduplicationKey") String deduplicationKey);

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
        <if test="eventType == 'FINISHED_TEST'">
            AND event_type IN ('FLYING_PROBE_TEST_COUNTERS', 'ZHONGBO_TEST_REPORT')
        </if>
        <if test="eventType != null and eventType != 'FINISHED_TEST'">
            AND event_type = #{eventType}
        </if>
        <if test="workOrderNo != null">
            AND (work_order_no LIKE CONCAT('%', #{workOrderNo}, '%')
                 OR EXISTS (
                     SELECT 1 FROM qms_flying_probe_projection projection
                     WHERE projection.telemetry_id COLLATE utf8mb4_0900_ai_ci = qms_equipment_telemetry.id COLLATE utf8mb4_0900_ai_ci
                       AND projection.tenant_code COLLATE utf8mb4_0900_ai_ci = qms_equipment_telemetry.tenant_code COLLATE utf8mb4_0900_ai_ci
                       AND projection.work_order_no LIKE CONCAT('%', #{workOrderNo}, '%')
                 ))
        </if>
        <if test="internalProductNo != null">
            AND material_no LIKE CONCAT('%', #{internalProductNo}, '%')
        </if>
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
            <choose>
                <when test="eventType == 'FLYING_PROBE_TEST_COUNTERS' or eventType == 'ZHONGBO_TEST_REPORT' or eventType == 'FINISHED_TEST'">
                    AND device_time &gt;= #{fromTime}
                </when>
                <otherwise>
                    AND received_at &gt;= #{fromTime}
                </otherwise>
            </choose>
        </if>
        <if test="toTime != null">
            <choose>
                <when test="eventType == 'FLYING_PROBE_TEST_COUNTERS' or eventType == 'ZHONGBO_TEST_REPORT' or eventType == 'FINISHED_TEST'">
                    AND device_time &lt;= #{toTime}
                </when>
                <otherwise>
                    AND received_at &lt;= #{toTime}
                </otherwise>
            </choose>
        </if>
        <if test="cursorTime != null">
            <choose>
                <when test="eventType == 'FLYING_PROBE_TEST_COUNTERS'">
                    AND (device_time &lt; #{cursorTime}
                         OR (device_time = #{cursorTime} AND id &lt; #{cursorId}))
                </when>
                <otherwise>
                    AND (received_at &lt; #{cursorTime}
                         OR (received_at = #{cursorTime} AND id &lt; #{cursorId}))
                </otherwise>
            </choose>
        </if>
        <choose>
            <when test="eventType == 'FLYING_PROBE_TEST_COUNTERS'">
                ORDER BY device_time DESC, id DESC
            </when>
            <otherwise>
                ORDER BY received_at DESC, id DESC
            </otherwise>
        </choose>
        LIMIT #{limit}
        </script>
        """)
    List<EquipmentTelemetryRecord> selectHistory(
        @Param("tenantCode") String tenantCode,
        @Param("equipmentId") String equipmentId,
        @Param("equipmentIds") List<String> equipmentIds,
        @Param("fromTime") LocalDateTime fromTime,
        @Param("toTime") LocalDateTime toTime,
        @Param("cursorTime") LocalDateTime cursorTime,
        @Param("cursorId") String cursorId,
        @Param("limit") int limit,
        @Param("eventType") String eventType,
        @Param("workOrderNo") String workOrderNo,
        @Param("internalProductNo") String internalProductNo
    );

    @Select("""
        <script>
        SELECT telemetry_id, status, reason, inspection_record_id, delta_tested, work_order_no
        FROM qms_flying_probe_projection
        WHERE tenant_code = #{tenantCode}
          AND telemetry_id IN
          <foreach collection="telemetryIds" item="telemetryId" open="(" separator="," close=")">
            #{telemetryId}
          </foreach>
        </script>
        """)
    List<EquipmentTelemetryProjectionStatus> selectProjectionStatuses(
        @Param("tenantCode") String tenantCode,
        @Param("telemetryIds") List<String> telemetryIds
    );
}
