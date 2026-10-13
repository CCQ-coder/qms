package com.pcbqms.masterdata.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.masterdata.domain.ProductMaster;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMasterMapper extends BaseMapper<ProductMaster> {
}
