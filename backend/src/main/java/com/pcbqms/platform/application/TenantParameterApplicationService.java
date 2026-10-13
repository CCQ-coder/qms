package com.pcbqms.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.platform.api.TenantParameterResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantParameterApplicationService {
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public TenantParameterResponse get(String key) {
        SystemParameter parameter = find(key);
        return new TenantParameterResponse(key, parameter == null ? "" : parameter.getParameterValue());
    }

    public TenantParameterResponse update(String key, String value) {
        inspectionAccessScopeService.requireTenantAdministrator();
        SystemParameter parameter = find(key);
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = parameter == null;
        if (parameter == null) {
            parameter = new SystemParameter();
            parameter.setId(UUID.randomUUID().toString());
            parameter.setTenantCode(TenantContext.tenantCode());
            parameter.setParameterKey(key);
        }
        parameter.setParameterValue(value.trim());
        parameter.setUpdatedBy(TenantContext.actor());
        parameter.setUpdatedAt(now);
        if (isNew) {
            systemParameterMapper.insert(parameter);
        } else {
            systemParameterMapper.updateById(parameter);
        }
        return new TenantParameterResponse(parameter.getParameterKey(), parameter.getParameterValue());
    }

    private SystemParameter find(String key) {
        return systemParameterMapper.selectOne(Wrappers.lambdaQuery(SystemParameter.class)
            .eq(SystemParameter::getTenantCode, TenantContext.tenantCode())
            .eq(SystemParameter::getParameterKey, key));
    }
}
