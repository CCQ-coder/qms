package com.pcbqms.inspection.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.inspection.domain.Nonconformity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NonconformityMapper extends BaseMapper<Nonconformity> {
}

