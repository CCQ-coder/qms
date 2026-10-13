package com.pcbqms.workorder.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.workorder.domain.ProductionBatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionBatchMapper extends BaseMapper<ProductionBatch> {
}

