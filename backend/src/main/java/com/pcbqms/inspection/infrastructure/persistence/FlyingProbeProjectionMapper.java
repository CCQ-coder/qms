package com.pcbqms.inspection.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.inspection.api.FlyingProbeRetryableRecordResponse;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.domain.FlyingProbeRun;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FlyingProbeProjectionMapper {
    @Select("""
        <script>
        WITH ranked AS (
            SELECT telemetry_id, tenant_code, equipment_id, machine_name, material_no, device_time,
                   source_file, source_start_time, reason, run_key, work_order_no,
                   cumulative_tested, cumulative_good, cumulative_bad,
                   ROW_NUMBER() OVER (PARTITION BY COALESCE(run_key, telemetry_id)
                       ORDER BY device_time, received_at, telemetry_id) AS row_no,
                   MAX(device_time) OVER (PARTITION BY COALESCE(run_key, telemetry_id)) AS latest_device_time
            FROM qms_flying_probe_projection
            WHERE tenant_code = #{tenantCode} AND status = 'RETRYABLE'
        )
        SELECT p.telemetry_id AS id, e.asset_no AS equipment_code, p.machine_name, p.material_no,
               p.device_time, p.source_file, p.source_start_time, p.reason,
               COALESCE(r.work_order_no, p.work_order_no) AS work_order_no,
               p.cumulative_tested, p.cumulative_good, p.cumulative_bad
        FROM ranked p
        LEFT JOIN qms_equipment e ON e.id = p.equipment_id COLLATE utf8mb4_0900_ai_ci
            AND e.tenant_code = p.tenant_code COLLATE utf8mb4_0900_ai_ci
        LEFT JOIN qms_flying_probe_run r ON r.run_key = p.run_key AND r.tenant_code = p.tenant_code
        WHERE p.row_no = 1
        <if test="internalProductNo != null">
            AND p.material_no LIKE CONCAT('%', #{internalProductNo}, '%')
        </if>
        <if test="workOrderNo != null">
            AND COALESCE(r.work_order_no, p.work_order_no) LIKE CONCAT('%', #{workOrderNo}, '%')
        </if>
        ORDER BY p.latest_device_time DESC, p.telemetry_id DESC
        LIMIT #{pageSize} OFFSET #{offset}
        </script>
        """)
    List<FlyingProbeRetryableRecordResponse> selectRetryableGroups(
        @Param("tenantCode") String tenantCode, @Param("pageSize") int pageSize, @Param("offset") long offset,
        @Param("workOrderNo") String workOrderNo, @Param("internalProductNo") String internalProductNo);

    @Select("""
        <script>
        WITH ranked AS (
            SELECT tenant_code, telemetry_id, run_key, material_no, work_order_no,
                   ROW_NUMBER() OVER (PARTITION BY COALESCE(run_key, telemetry_id)
                       ORDER BY device_time, received_at, telemetry_id) AS row_no
            FROM qms_flying_probe_projection
            WHERE tenant_code = #{tenantCode} AND status = 'RETRYABLE'
        )
        SELECT COUNT(*) FROM ranked p
        LEFT JOIN qms_flying_probe_run r ON r.run_key = p.run_key AND r.tenant_code = p.tenant_code
        WHERE p.row_no = 1
        <if test="internalProductNo != null">
            AND p.material_no LIKE CONCAT('%', #{internalProductNo}, '%')
        </if>
        <if test="workOrderNo != null">
            AND COALESCE(r.work_order_no, p.work_order_no) LIKE CONCAT('%', #{workOrderNo}, '%')
        </if>
        </script>
        """)
    long countRetryableGroups(@Param("tenantCode") String tenantCode,
                              @Param("workOrderNo") String workOrderNo,
                              @Param("internalProductNo") String internalProductNo);

    @Select("""
        SELECT telemetry.id, telemetry.tenant_code, telemetry.equipment_id, telemetry.machine_name,
               telemetry.material_no, telemetry.device_time, telemetry.received_at, telemetry.event_type,
               telemetry.source_record_no, telemetry.raw_payload
        FROM qms_equipment_telemetry telemetry
        WHERE telemetry.tenant_code = #{tenantCode}
          AND telemetry.event_type = 'FLYING_PROBE_TEST_COUNTERS'
          AND telemetry.device_time >= #{cutoff}
          AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(telemetry.raw_payload, '$.attributes.startTime')), '')
              >= DATE_FORMAT(#{cutoff}, '%Y-%m-%dT%H:%i:%s')
          AND NOT EXISTS (
              SELECT 1 FROM qms_flying_probe_projection projection
              WHERE projection.telemetry_id = telemetry.id
          )
        ORDER BY telemetry.device_time, telemetry.received_at, telemetry.id
        LIMIT #{limit}
        """)
    List<EquipmentTelemetryRecord> selectRecentUnprojected(
        @Param("tenantCode") String tenantCode, @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Select("""
        SELECT telemetry.id, telemetry.tenant_code, telemetry.equipment_id, telemetry.machine_name,
               telemetry.material_no, telemetry.device_time, telemetry.received_at, telemetry.event_type,
               telemetry.source_record_no, telemetry.raw_payload
        FROM qms_equipment_telemetry telemetry
        WHERE telemetry.tenant_code = #{tenantCode}
          AND telemetry.event_type = 'FLYING_PROBE_TEST_COUNTERS'
          AND (telemetry.device_time < #{cutoff}
               OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(telemetry.raw_payload, '$.attributes.startTime')), '')
                  < DATE_FORMAT(#{cutoff}, '%Y-%m-%dT%H:%i:%s'))
          AND NOT EXISTS (
              SELECT 1 FROM qms_flying_probe_projection projection
              WHERE projection.telemetry_id = telemetry.id
          )
        ORDER BY telemetry.device_time, telemetry.received_at, telemetry.id
        LIMIT #{limit}
        """)
    List<EquipmentTelemetryRecord> selectOlderUnprojected(
        @Param("tenantCode") String tenantCode, @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Select("""
        SELECT * FROM qms_flying_probe_projection
        WHERE tenant_code = #{tenantCode} AND status = 'RETRYABLE' AND retry_after <= #{now}
          AND source_start_time >= #{cutoff}
        ORDER BY device_time, received_at, telemetry_id
        LIMIT #{limit}
        """)
    List<FlyingProbeProjection> selectRecentRetryable(
        @Param("tenantCode") String tenantCode, @Param("now") LocalDateTime now,
        @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Select("""
        SELECT * FROM qms_flying_probe_projection
        WHERE tenant_code = #{tenantCode} AND status = 'RETRYABLE' AND retry_after <= #{now}
          AND (source_start_time < #{cutoff} OR source_start_time IS NULL)
        ORDER BY device_time, received_at, telemetry_id
        LIMIT #{limit}
        """)
    List<FlyingProbeProjection> selectOlderRetryable(
        @Param("tenantCode") String tenantCode, @Param("now") LocalDateTime now,
        @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Insert("""
        INSERT INTO qms_flying_probe_projection (
            telemetry_id, tenant_code, equipment_id, machine_name, material_no, device_time, received_at,
            source_record_no, status, raw_payload, processed_at
        ) VALUES (
            #{id}, #{tenantCode}, #{equipmentId}, #{machineName}, #{materialNo}, #{deviceTime}, #{receivedAt},
            #{sourceRecordNo}, 'PENDING', #{rawPayload}, CURRENT_TIMESTAMP
        ) ON DUPLICATE KEY UPDATE telemetry_id = telemetry_id
        """)
    int claim(EquipmentTelemetryRecord telemetry);

    @Select("SELECT * FROM qms_flying_probe_projection WHERE telemetry_id = #{telemetryId} FOR UPDATE")
    FlyingProbeProjection lockProjection(@Param("telemetryId") String telemetryId);

    @Update("""
        UPDATE qms_flying_probe_projection SET
            source_file = #{sourceFile}, source_start_time = #{sourceStartTime}, run_key = #{runKey},
            cumulative_tested = #{cumulativeTested}, cumulative_good = #{cumulativeGood},
            cumulative_bad = #{cumulativeBad}, delta_tested = #{deltaTested}, delta_good = #{deltaGood},
            delta_bad = #{deltaBad}, work_order_no = #{workOrderNo}, batch_no = #{batchNo},
            inspection_task_id = #{inspectionTaskId}, inspection_record_id = #{inspectionRecordId},
            status = #{status}, reason = #{reason}, retry_after = #{retryAfter}, processed_at = #{processedAt}
        WHERE telemetry_id = #{telemetryId}
        """)
    int updateProjection(FlyingProbeProjection projection);

    @Update("""
        UPDATE qms_flying_probe_projection SET retry_after = CURRENT_TIMESTAMP
        WHERE tenant_code = #{tenantCode} AND run_key = #{runKey} AND status = 'RETRYABLE'
        """)
    int wakeRetryableRun(@Param("tenantCode") String tenantCode, @Param("runKey") String runKey);

    @Select("""
        SELECT COUNT(*) FROM qms_flying_probe_projection
        WHERE tenant_code = #{tenantCode} AND equipment_id = #{equipmentId} AND run_key = #{runKey}
          AND status = 'RETRYABLE' AND telemetry_id <> #{telemetryId}
          AND (device_time < #{deviceTime}
               OR (device_time = #{deviceTime} AND received_at < #{receivedAt})
               OR (device_time = #{deviceTime} AND received_at = #{receivedAt} AND telemetry_id < #{telemetryId}))
        """)
    long countEarlierUnresolved(FlyingProbeProjection projection);

    @Insert("""
        INSERT INTO qms_flying_probe_run (
            run_key, tenant_code, equipment_id, source_file, source_start_time, material_no,
            work_order_no, binding_source, bound_by, bound_at,
            tested_count, good_count, bad_count, status, updated_at
        ) VALUES (
            #{runKey}, #{tenantCode}, #{equipmentId}, #{sourceFile}, #{sourceStartTime}, #{materialNo},
            #{workOrderNo}, #{bindingSource}, #{boundBy}, #{boundAt},
            0, 0, 0, 'ACTIVE', #{updatedAt}
        ) ON DUPLICATE KEY UPDATE run_key = run_key
        """)
    int insertRunIfAbsent(FlyingProbeRun run);

    @Select("SELECT * FROM qms_flying_probe_run WHERE run_key = #{runKey} FOR UPDATE")
    FlyingProbeRun lockRun(@Param("runKey") String runKey);

    @Update("""
        UPDATE qms_flying_probe_run SET last_device_time = #{lastDeviceTime},
            tested_count = #{testedCount}, good_count = #{goodCount}, bad_count = #{badCount},
            status = #{status}, reason = #{reason}, binding_source = #{bindingSource},
            bound_by = #{boundBy}, bound_at = #{boundAt}, updated_at = #{updatedAt}
        WHERE run_key = #{runKey}
        """)
    int updateRun(FlyingProbeRun run);

    @Update("""
        UPDATE qms_equipment_telemetry SET work_order_no = #{workOrderNo}, batch_no = #{batchNo}
        WHERE tenant_code = #{tenantCode} AND id = #{telemetryId}
        """)
    int linkTelemetry(FlyingProbeProjection projection);

    @Update("""
        UPDATE qms_equipment_runtime_status SET work_order_no = #{workOrderNo}, batch_no = #{batchNo},
            updated_at = CURRENT_TIMESTAMP
        WHERE tenant_code = #{tenantCode} AND equipment_id = #{equipmentId}
          AND last_source_record_no = #{sourceRecordNo} AND last_device_time = #{deviceTime}
        """)
    int linkCurrentRuntime(FlyingProbeProjection projection);
}
