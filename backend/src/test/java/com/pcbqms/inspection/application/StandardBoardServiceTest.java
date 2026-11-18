package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardBoardServiceTest {
    @Mock private SystemParameterMapper parameterMapper;
    @Mock private FlyingProbeProjectionMapper projectionMapper;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void excludesFzv1UntilTheTenantSavesItsOwnList() {
        TenantContext.set("tenant-a", "admin");

        var service = new StandardBoardService(parameterMapper, projectionMapper);

        assertThat(service.contains(" fzv1 ")).isTrue();
        assertThat(service.contains("PART-1")).isFalse();
    }

    @Test
    void recognizesSeveralExactMaterialNumbersWithoutCaseOrWhitespaceDifferences() {
        TenantContext.set("tenant-a", "admin");
        SystemParameter parameter = new SystemParameter();
        parameter.setParameterValue(" FZV1\nstd-2, FZV1，STD-3 ");
        when(parameterMapper.selectOne(any())).thenReturn(parameter);
        var service = new StandardBoardService(parameterMapper, projectionMapper);

        assertThat(service.materialNumbers()).isEqualTo(Set.of("FZV1", "STD-2", "STD-3"));
        assertThat(service.contains("std-2")).isTrue();
        assertThat(service.contains("STD-20")).isFalse();
        when(projectionMapper.selectInspectionRecordIdsByMaterials(any(), any())).thenReturn(List.of("record-1"));
        assertThat(service.inspectionRecordIds()).containsExactly("record-1");
    }
}
