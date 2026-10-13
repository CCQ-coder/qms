package com.pcbqms.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.access.domain.UserAccessProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccessProfileMapper extends BaseMapper<UserAccessProfile> {
}
