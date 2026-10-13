package com.pcbqms.inspection.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.inspection.domain.InspectionRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {
}

