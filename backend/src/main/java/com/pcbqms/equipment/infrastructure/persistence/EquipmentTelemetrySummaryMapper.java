package com.pcbqms.equipment.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentTelemetrySummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentTelemetrySummaryMapper {
    @Insert("""
        INSERT INTO qms_equipment_telemetry_minute_summary (
            id, tenant_code, equipment_id, bucket_start, first_device_time, last_device_time, last_received_at,
            sample_count, last_event_type, last_equipment_status, last_program_no, last_operator_name,
            max_progress_percent, last_spindle_status, last_alarm_code, last_alarm_message,
            last_machining_count, max_machining_count, created_at, updated_at
        ) VALUES (
            #{id}, #{tenantCode}, #{equipmentId}, #{bucketStart}, #{firstDeviceTime}, #{lastDeviceTime}, #{lastReceivedAt},
            #{sampleCount}, #{lastEventType}, #{lastEquipmentStatus}, #{lastProgramNo}, #{lastOperatorName},
            #{maxProgressPercent}, #{lastSpindleStatus}, #{lastAlarmCode}, #{lastAlarmMessage},
            #{lastMachiningCount}, #{maxMachiningCount}, #{createdAt}, #{updatedAt}
        ) ON DUPLICATE KEY UPDATE
            sample_count = sample_count + 1,
            first_device_time = LEAST(first_device_time, VALUES(first_device_time)),
            last_event_type = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_event_type), last_event_type),
            last_equipment_status = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_equipment_status), last_equipment_status),
            last_program_no = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_program_no), last_program_no),
            last_operator_name = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_operator_name), last_operator_name),
            max_progress_percent = CASE
                WHEN VALUES(max_progress_percent) IS NULL THEN max_progress_percent
                WHEN max_progress_percent IS NULL THEN VALUES(max_progress_percent)
                ELSE GREATEST(max_progress_percent, VALUES(max_progress_percent))
            END,
            last_spindle_status = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_spindle_status), last_spindle_status),
            last_alarm_code = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_alarm_code), last_alarm_code),
            last_alarm_message = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_alarm_message), last_alarm_message),
            last_machining_count = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_machining_count), last_machining_count),
            max_machining_count = CASE
                WHEN VALUES(max_machining_count) IS NULL THEN max_machining_count
                WHEN max_machining_count IS NULL THEN VALUES(max_machining_count)
                ELSE GREATEST(max_machining_count, VALUES(max_machining_count))
            END,
            last_received_at = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_received_at), last_received_at),
            last_device_time = GREATEST(last_device_time, VALUES(last_device_time)),
            updated_at = VALUES(updated_at)
        """)
    int upsertMinute(EquipmentTelemetrySummary summary);

    @Insert("""
        INSERT INTO qms_equipment_telemetry_hour_summary (
            id, tenant_code, equipment_id, bucket_start, first_device_time, last_device_time, last_received_at,
            sample_count, last_event_type, last_equipment_status, last_program_no, last_operator_name,
            max_progress_percent, last_spindle_status, last_alarm_code, last_alarm_message,
            last_machining_count, max_machining_count, created_at, updated_at
        ) VALUES (
            #{id}, #{tenantCode}, #{equipmentId}, #{bucketStart}, #{firstDeviceTime}, #{lastDeviceTime}, #{lastReceivedAt},
            #{sampleCount}, #{lastEventType}, #{lastEquipmentStatus}, #{lastProgramNo}, #{lastOperatorName},
            #{maxProgressPercent}, #{lastSpindleStatus}, #{lastAlarmCode}, #{lastAlarmMessage},
            #{lastMachiningCount}, #{maxMachiningCount}, #{createdAt}, #{updatedAt}
        ) ON DUPLICATE KEY UPDATE
            sample_count = sample_count + 1,
            first_device_time = LEAST(first_device_time, VALUES(first_device_time)),
            last_event_type = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_event_type), last_event_type),
            last_equipment_status = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_equipment_status), last_equipment_status),
            last_program_no = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_program_no), last_program_no),
            last_operator_name = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_operator_name), last_operator_name),
            max_progress_percent = CASE
                WHEN VALUES(max_progress_percent) IS NULL THEN max_progress_percent
                WHEN max_progress_percent IS NULL THEN VALUES(max_progress_percent)
                ELSE GREATEST(max_progress_percent, VALUES(max_progress_percent))
            END,
            last_spindle_status = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_spindle_status), last_spindle_status),
            last_alarm_code = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_alarm_code), last_alarm_code),
            last_alarm_message = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_alarm_message), last_alarm_message),
            last_machining_count = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_machining_count), last_machining_count),
            max_machining_count = CASE
                WHEN VALUES(max_machining_count) IS NULL THEN max_machining_count
                WHEN max_machining_count IS NULL THEN VALUES(max_machining_count)
                ELSE GREATEST(max_machining_count, VALUES(max_machining_count))
            END,
            last_received_at = IF(VALUES(last_device_time) >= last_device_time, VALUES(last_received_at), last_received_at),
            last_device_time = GREATEST(last_device_time, VALUES(last_device_time)),
            updated_at = VALUES(updated_at)
        """)
    int upsertHour(EquipmentTelemetrySummary summary);
}
