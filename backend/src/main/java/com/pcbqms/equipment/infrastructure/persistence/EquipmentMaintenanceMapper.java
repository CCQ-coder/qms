package com.pcbqms.equipment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.equipment.domain.EquipmentMaintenance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentMaintenanceMapper extends BaseMapper<EquipmentMaintenance> {
}

