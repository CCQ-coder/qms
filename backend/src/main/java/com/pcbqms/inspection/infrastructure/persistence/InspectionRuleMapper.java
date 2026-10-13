package com.pcbqms.inspection.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.inspection.domain.InspectionRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InspectionRuleMapper extends BaseMapper<InspectionRule> {
}

