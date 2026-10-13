package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.domain.InspectionTemplateItem;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateItemMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionTemplateApplicationServiceTest {
    @Mock private InspectionTemplateMapper inspectionTemplateMapper;
    @Mock private InspectionTemplateItemMapper inspectionTemplateItemMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsPassVerdictWhenNumberExceedsConfiguredUpperLimit() {
        InspectionTemplateApplicationService service = serviceWithThicknessItem();

        assertThatThrownBy(() -> service.validateRecordResults("template-fqc",
            Map.of("THICKNESS", Map.of("value", "1.80")), "PASS"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("超出控制范围");
    }

    @Test
    void acceptsInRangeNumberAndAllowsFailVerdictForAdditionalVisualDefects() {
        InspectionTemplateApplicationService service = serviceWithThicknessItem();

        assertThatCode(() -> service.validateRecordResults("template-fqc",
            Map.of("THICKNESS", Map.of("value", "1.60")), "PASS")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateRecordResults("template-fqc",
            Map.of("THICKNESS", Map.of("value", "1.80")), "FAIL")).doesNotThrowAnyException();
    }

    @Test
    void rejectsSelectionOutsideTemplateOptions() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTemplate template = new InspectionTemplate();
        template.setId("template-fqc");
        template.setTenantCode("customer-a");
        InspectionTemplateItem item = new InspectionTemplateItem();
        item.setTemplateId("template-fqc");
        item.setTenantCode("customer-a");
        item.setItemCode("APPEARANCE");
        item.setItemName("外观");
        item.setInputType("SELECT");
        item.setRequired(true);
        item.setOptionsJson("[\"合格\",\"轻微划伤\"]");
        when(inspectionTemplateMapper.selectOne(any())).thenReturn(template);
        when(inspectionTemplateItemMapper.selectList(any())).thenReturn(List.of(item));
        InspectionTemplateApplicationService service = new InspectionTemplateApplicationService(inspectionTemplateMapper,
            inspectionTemplateItemMapper, new ObjectMapper(), inspectionAccessScopeService);

        assertThatThrownBy(() -> service.validateRecordResults("template-fqc",
            Map.of("APPEARANCE", Map.of("value", "未定义状态")), "FAIL"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不在模板允许范围内");
    }

    private InspectionTemplateApplicationService serviceWithThicknessItem() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTemplate template = new InspectionTemplate();
        template.setId("template-fqc");
        template.setTenantCode("customer-a");
        InspectionTemplateItem item = new InspectionTemplateItem();
        item.setTemplateId("template-fqc");
        item.setTenantCode("customer-a");
        item.setItemCode("THICKNESS");
        item.setItemName("板厚");
        item.setInputType("NUMBER");
        item.setRequired(true);
        item.setLowerLimit(new BigDecimal("1.50"));
        item.setUpperLimit(new BigDecimal("1.70"));
        when(inspectionTemplateMapper.selectOne(any())).thenReturn(template);
        when(inspectionTemplateItemMapper.selectList(any())).thenReturn(List.of(item));
        return new InspectionTemplateApplicationService(inspectionTemplateMapper, inspectionTemplateItemMapper,
            new ObjectMapper(), inspectionAccessScopeService);
    }
}
