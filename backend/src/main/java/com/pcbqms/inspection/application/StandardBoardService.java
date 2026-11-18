package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StandardBoardService {
    public static final String PARAMETER_KEY = "quality.finished-test.standard-boards";
    private final SystemParameterMapper parameterMapper;
    private final FlyingProbeProjectionMapper projectionMapper;

    public Set<String> materialNumbers() {
        SystemParameter parameter = parameterMapper.selectOne(Wrappers.lambdaQuery(SystemParameter.class)
            .eq(SystemParameter::getTenantCode, TenantContext.tenantCode())
            .eq(SystemParameter::getParameterKey, PARAMETER_KEY));
        String value = parameter == null ? "FZV1" : parameter.getParameterValue();
        return Arrays.stream(value.split("[,，\\s]+"))
            .map(valuePart -> valuePart.trim().toUpperCase(Locale.ROOT))
            .filter(StringUtils::hasText)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean contains(String materialNo) {
        return StringUtils.hasText(materialNo)
            && materialNumbers().contains(materialNo.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> inspectionRecordIds() {
        Set<String> materials = materialNumbers();
        return materials.isEmpty() ? Set.of() : Set.copyOf(projectionMapper.selectInspectionRecordIdsByMaterials(
            TenantContext.tenantCode(), List.copyOf(materials)));
    }
}
