package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.CreateInspectionTemplateRequest;
import com.pcbqms.inspection.api.InspectionTemplateItemRequest;
import com.pcbqms.inspection.api.InspectionTemplateItemResponse;
import com.pcbqms.inspection.api.InspectionTemplateResponse;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.domain.InspectionTemplateItem;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateItemMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InspectionTemplateApplicationService {
    private static final Set<String> INPUT_TYPES = Set.of("TEXT", "NUMBER", "SELECT", "BOOLEAN");
    private final InspectionTemplateMapper inspectionTemplateMapper;
    private final InspectionTemplateItemMapper inspectionTemplateItemMapper;
    private final ObjectMapper objectMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<InspectionTemplateResponse> list() {
        return inspectionTemplateMapper.selectList(Wrappers.lambdaQuery(InspectionTemplate.class)
                .eq(InspectionTemplate::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(InspectionTemplate::getProcessCode)
                .orderByDesc(InspectionTemplate::getUpdatedAt))
            .stream().map(this::toResponse).toList();
    }

    public InspectionTemplateResponse get(String id) {
        return toResponse(requireTemplate(id));
    }

    @Transactional
    public InspectionTemplateResponse create(CreateInspectionTemplateRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String tenantCode = TenantContext.tenantCode();
        String templateCode = request.templateCode().trim().toUpperCase(Locale.ROOT);
        if (inspectionTemplateMapper.selectCount(Wrappers.lambdaQuery(InspectionTemplate.class)
            .eq(InspectionTemplate::getTenantCode, tenantCode)
            .eq(InspectionTemplate::getTemplateCode, templateCode)) > 0) {
            throw new IllegalArgumentException("模板编码已存在：" + templateCode);
        }
        validateItems(request.items());
        LocalDateTime now = LocalDateTime.now();
        InspectionTemplate template = new InspectionTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setTenantCode(tenantCode);
        template.setTemplateCode(templateCode);
        template.setName(request.name().trim());
        template.setProductCode(normalizeNullable(request.productCode()));
        template.setProcessCode(request.processCode().trim());
        template.setProcessName(request.processName().trim());
        template.setTaskType(request.taskType().trim().toUpperCase(Locale.ROOT));
        template.setVersionNo(1);
        template.setStatus("ACTIVE");
        template.setCreatedBy(TenantContext.actor());
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        inspectionTemplateMapper.insert(template);
        for (InspectionTemplateItemRequest itemRequest : request.items()) {
            InspectionTemplateItem item = new InspectionTemplateItem();
            item.setId(UUID.randomUUID().toString());
            item.setTenantCode(tenantCode);
            item.setTemplateId(template.getId());
            item.setSortOrder(itemRequest.sortOrder());
            item.setItemCode(itemRequest.itemCode().trim());
            item.setItemName(itemRequest.itemName().trim());
            item.setInputType(itemRequest.inputType().trim().toUpperCase(Locale.ROOT));
            item.setRequired(itemRequest.required());
            item.setLowerLimit(itemRequest.lowerLimit());
            item.setUpperLimit(itemRequest.upperLimit());
            item.setUnit(normalizeNullable(itemRequest.unit()));
            item.setOptionsJson(toJson(itemRequest.options()));
            item.setHelpText(normalizeNullable(itemRequest.helpText()));
            inspectionTemplateItemMapper.insert(item);
        }
        return toResponse(template);
    }

    @Transactional
    public InspectionTemplateResponse updateStatus(String id, String value) {
        inspectionAccessScopeService.requireTenantAdministrator();
        InspectionTemplate template = requireTemplate(id);
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new IllegalArgumentException("模板状态仅支持 ACTIVE 或 INACTIVE");
        }
        template.setStatus(status);
        template.setUpdatedAt(LocalDateTime.now());
        inspectionTemplateMapper.updateById(template);
        return toResponse(template);
    }

    public void assertActiveTemplateBelongsToTenant(String templateId) {
        if (!isActiveTemplate(templateId)) {
            throw new IllegalArgumentException("检验模板不存在、无权访问或未启用，不能用于新任务");
        }
    }

    public boolean isActiveTemplate(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return true;
        }
        InspectionTemplate template = inspectionTemplateMapper.selectOne(Wrappers.lambdaQuery(InspectionTemplate.class)
            .eq(InspectionTemplate::getId, templateId)
            .eq(InspectionTemplate::getTenantCode, TenantContext.tenantCode()));
        return template != null && "ACTIVE".equals(template.getStatus());
    }

    /**
     * Enforces the task template when a record is submitted. This keeps a client or offline replay from
     * submitting an incomplete form or a PASS verdict with measurements outside the configured limits.
     */
    public void validateRecordResults(String templateId, Map<String, Object> results, String verdict) {
        if (!StringUtils.hasText(templateId)) {
            return;
        }
        InspectionTemplate template = requireTemplate(templateId);
        Map<String, Object> resultValues = results == null ? Map.of() : results;
        List<InspectionTemplateItem> items = inspectionTemplateItemMapper.selectList(
            Wrappers.lambdaQuery(InspectionTemplateItem.class)
                .eq(InspectionTemplateItem::getTenantCode, template.getTenantCode())
                .eq(InspectionTemplateItem::getTemplateId, template.getId())
                .orderByAsc(InspectionTemplateItem::getSortOrder));
        for (InspectionTemplateItem item : items) {
            Object value = extractResultValue(resultValues.get(item.getItemCode()));
            if (Boolean.TRUE.equals(item.getRequired()) && !hasResultValue(value)) {
                throw new IllegalArgumentException("请填写必填检验项：" + item.getItemName());
            }
            if (!"NUMBER".equals(item.getInputType()) || !hasResultValue(value)) {
                validateEnumeratedValue(item, value);
                continue;
            }
            BigDecimal numericValue = toBigDecimal(value, item.getItemName());
            boolean outOfRange = (item.getLowerLimit() != null && numericValue.compareTo(item.getLowerLimit()) < 0)
                || (item.getUpperLimit() != null && numericValue.compareTo(item.getUpperLimit()) > 0);
            if (outOfRange && "PASS".equals(verdict)) {
                throw new IllegalArgumentException("检验项“" + item.getItemName() + "”超出控制范围，结论必须为不合格");
            }
        }
    }

    private InspectionTemplate requireTemplate(String id) {
        InspectionTemplate template = inspectionTemplateMapper.selectOne(Wrappers.lambdaQuery(InspectionTemplate.class)
            .eq(InspectionTemplate::getId, id)
            .eq(InspectionTemplate::getTenantCode, TenantContext.tenantCode()));
        if (template == null) {
            throw new IllegalArgumentException("检验模板不存在或无权访问");
        }
        return template;
    }

    private InspectionTemplateResponse toResponse(InspectionTemplate template) {
        List<InspectionTemplateItemResponse> items = inspectionTemplateItemMapper.selectList(
                Wrappers.lambdaQuery(InspectionTemplateItem.class)
                    .eq(InspectionTemplateItem::getTenantCode, template.getTenantCode())
                    .eq(InspectionTemplateItem::getTemplateId, template.getId())
                    .orderByAsc(InspectionTemplateItem::getSortOrder))
            .stream().map(item -> InspectionTemplateItemResponse.from(item, fromJson(item.getOptionsJson()))).toList();
        return InspectionTemplateResponse.from(template, items);
    }

    private void validateItems(List<InspectionTemplateItemRequest> items) {
        Set<String> codes = new java.util.HashSet<>();
        for (InspectionTemplateItemRequest item : items) {
            String inputType = item.inputType().trim().toUpperCase(Locale.ROOT);
            if (!INPUT_TYPES.contains(inputType)) {
                throw new IllegalArgumentException("检验项录入类型仅支持 TEXT、NUMBER、SELECT 或 BOOLEAN");
            }
            String itemCode = item.itemCode().trim();
            if (!codes.add(itemCode)) {
                throw new IllegalArgumentException("检验项编码不能重复：" + itemCode);
            }
            if (item.lowerLimit() != null && item.upperLimit() != null
                && item.lowerLimit().compareTo(item.upperLimit()) > 0) {
                throw new IllegalArgumentException("检验项下限不能大于上限：" + item.itemName());
            }
            if ("SELECT".equals(inputType) && (item.options() == null || item.options().isEmpty())) {
                throw new IllegalArgumentException("下拉选择类型必须至少配置一个选项：" + item.itemName());
            }
        }
    }

    private Object extractResultValue(Object rawValue) {
        if (rawValue instanceof Map<?, ?> detail) {
            return detail.get("value");
        }
        return rawValue;
    }

    private boolean hasResultValue(Object value) {
        return value != null && (!(value instanceof String text) || StringUtils.hasText(text));
    }

    private BigDecimal toBigDecimal(Object value, String itemName) {
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("检验项“" + itemName + "”必须填写数值");
        }
    }

    private void validateEnumeratedValue(InspectionTemplateItem item, Object value) {
        if (!hasResultValue(value)) {
            return;
        }
        if ("SELECT".equals(item.getInputType())) {
            String selected = value.toString().trim();
            if (!fromJson(item.getOptionsJson()).contains(selected)) {
                throw new IllegalArgumentException("检验项“" + item.getItemName() + "”的选项不在模板允许范围内");
            }
        }
        if ("BOOLEAN".equals(item.getInputType()) && !(value instanceof Boolean)
            && !"true".equalsIgnoreCase(value.toString().trim()) && !"false".equalsIgnoreCase(value.toString().trim())) {
            throw new IllegalArgumentException("检验项“" + item.getItemName() + "”仅支持是或否");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values.stream().filter(StringUtils::hasText).map(String::trim).toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("检验项选项无法序列化", exception);
        }
    }

    private List<String> fromJson(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
