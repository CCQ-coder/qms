package com.pcbqms.equipment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.equipment.domain.EquipmentGatewayUnmatchedMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentGatewayUnmatchedMessageMapper extends BaseMapper<EquipmentGatewayUnmatchedMessage> {
}
