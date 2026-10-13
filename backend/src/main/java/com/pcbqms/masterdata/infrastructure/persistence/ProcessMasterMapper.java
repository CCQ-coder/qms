package com.pcbqms.masterdata.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.masterdata.domain.ProcessMaster;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessMasterMapper extends BaseMapper<ProcessMaster> {
}
