package com.pcbqms.equipment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.equipment.domain.EquipmentBusinessEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentBusinessEventMapper extends BaseMapper<EquipmentBusinessEvent> {
    @Insert("""
        INSERT INTO qms_equipment_business_event
            (id, tenant_code, equipment_id, telemetry_id, event_type, status, payload, created_at, processed_at)
        VALUES
            (#{id}, #{tenantCode}, #{equipmentId}, #{telemetryId}, #{eventType}, #{status},
             #{payload}, #{createdAt}, #{processedAt})
        """)
    int insertWithPayload(EquipmentBusinessEvent event);
}
