package com.pcbqms.inspection.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
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
            work_order_no, tested_count, good_count, bad_count, status, updated_at
        ) VALUES (
            #{runKey}, #{tenantCode}, #{equipmentId}, #{sourceFile}, #{sourceStartTime}, #{materialNo},
            #{workOrderNo}, 0, 0, 0, 'ACTIVE', #{updatedAt}
        ) ON DUPLICATE KEY UPDATE run_key = run_key
        """)
    int insertRunIfAbsent(FlyingProbeRun run);

    @Select("SELECT * FROM qms_flying_probe_run WHERE run_key = #{runKey} FOR UPDATE")
    FlyingProbeRun lockRun(@Param("runKey") String runKey);

    @Update("""
        UPDATE qms_flying_probe_run SET last_device_time = #{lastDeviceTime},
            tested_count = #{testedCount}, good_count = #{goodCount}, bad_count = #{badCount},
            status = #{status}, reason = #{reason}, updated_at = #{updatedAt}
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
