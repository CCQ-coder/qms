package com.pcbqms.equipment.infrastructure.persistence;

import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EquipmentRuntimeChannelMapper {
    @Update("""
        UPDATE qms_equipment_runtime_channel
        SET reported_status = #{reportedStatus}, last_device_time = #{lastDeviceTime},
            last_received_at = #{lastReceivedAt}, updated_at = #{updatedAt}
        WHERE tenant_code = #{tenantCode} AND equipment_id = #{equipmentId}
          AND source_name = #{sourceName} AND last_device_time < #{lastDeviceTime}
        """)
    int updateIfNewer(EquipmentRuntimeChannel channel);

    @Insert("""
        INSERT IGNORE INTO qms_equipment_runtime_channel
            (id, tenant_code, equipment_id, source_name, reported_status,
             last_device_time, last_received_at, updated_at)
        VALUES (#{id}, #{tenantCode}, #{equipmentId}, #{sourceName}, #{reportedStatus},
                #{lastDeviceTime}, #{lastReceivedAt}, #{updatedAt})
        """)
    int insertIfAbsent(EquipmentRuntimeChannel channel);

    /** A concurrent first report may insert between the first update and insert attempt. */
    default int upsertIfNewer(EquipmentRuntimeChannel channel) {
        if (updateIfNewer(channel) != 0) return 1;
        if (insertIfAbsent(channel) != 0) return 1;
        return updateIfNewer(channel);
    }

    @Select("""
        SELECT id, tenant_code, equipment_id, source_name, reported_status,
               last_device_time, last_received_at, updated_at
        FROM qms_equipment_runtime_channel
        WHERE tenant_code = #{tenantCode}
          AND source_name IN ('__FPT_NETWORK__', '__FPT_SOURCE__', '__FPT_TXT_ACTIVITY__')
        """)
    List<EquipmentRuntimeChannel> selectReservedByTenant(@Param("tenantCode") String tenantCode);
}
