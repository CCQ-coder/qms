package com.pcbqms.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.platform.api.TenantParameterResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantParameterApplicationService {
    private static final String FLYING_PROBE_PATTERN_KEY = "flying-probe.material-extract-pattern";
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public TenantParameterResponse get(String key) {
        SystemParameter parameter = find(key);
        return new TenantParameterResponse(key, parameter == null ? "" : parameter.getParameterValue());
    }

    public TenantParameterResponse update(String key, String value) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty() && !FLYING_PROBE_PATTERN_KEY.equals(key)) {
            throw new IllegalArgumentException("参数值不能为空");
        }
        if (("ui.browser-title".equals(key) || "ui.sidebar-title".equals(key)) && normalizedValue.length() > 40) {
            throw new IllegalArgumentException("标题不能超过 40 个字符");
        }
        if (FLYING_PROBE_PATTERN_KEY.equals(key) && !normalizedValue.isEmpty()) {
            try {
                if (Pattern.compile(normalizedValue).matcher("").groupCount() != 1) {
                    throw new IllegalArgumentException("飞针料号提取规则必须有且只有一个捕获组");
                }
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("飞针料号提取规则不是有效正则表达式", exception);
            }
        }
        SystemParameter parameter = find(key);
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = parameter == null;
        if (parameter == null) {
            parameter = new SystemParameter();
            parameter.setId(UUID.randomUUID().toString());
            parameter.setTenantCode(TenantContext.tenantCode());
            parameter.setParameterKey(key);
        }
        parameter.setParameterValue(normalizedValue);
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
