package com.pcbqms.administration.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.administration.domain.AdministrationItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdministrationItemMapper extends BaseMapper<AdministrationItem> {
}
