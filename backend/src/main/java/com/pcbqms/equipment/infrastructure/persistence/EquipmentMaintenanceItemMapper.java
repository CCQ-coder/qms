package com.pcbqms.equipment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.equipment.domain.EquipmentMaintenanceItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentMaintenanceItemMapper extends BaseMapper<EquipmentMaintenanceItem> {
}
