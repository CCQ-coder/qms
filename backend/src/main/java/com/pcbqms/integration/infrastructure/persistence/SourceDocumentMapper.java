package com.pcbqms.integration.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.integration.domain.SourceDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceDocumentMapper extends BaseMapper<SourceDocument> {
}

