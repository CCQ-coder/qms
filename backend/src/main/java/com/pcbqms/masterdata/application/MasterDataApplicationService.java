package com.pcbqms.masterdata.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.masterdata.api.CreateProcessRequest;
import com.pcbqms.masterdata.api.CreateProductRequest;
import com.pcbqms.masterdata.api.ProcessResponse;
import com.pcbqms.masterdata.api.ProductResponse;
import com.pcbqms.masterdata.domain.ProcessMaster;
import com.pcbqms.masterdata.domain.ProductMaster;
import com.pcbqms.masterdata.infrastructure.persistence.ProcessMasterMapper;
import com.pcbqms.masterdata.infrastructure.persistence.ProductMasterMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MasterDataApplicationService {
    private static final String PRODUCT_CODE_VALIDATION_KEY = "master-data.product-code.validation-enabled";
    private static final String PRODUCT_CODE_PATTERN_KEY = "master-data.product-code.pattern";

    private final ProductMasterMapper productMasterMapper;
    private final ProcessMasterMapper processMasterMapper;
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<ProductResponse> listProducts() {
        return productMasterMapper.selectList(Wrappers.lambdaQuery(ProductMaster.class)
                .eq(ProductMaster::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(ProductMaster::getProductCode))
            .stream().map(ProductResponse::from).toList();
    }

    public List<ProcessResponse> listProcesses() {
        return processMasterMapper.selectList(Wrappers.lambdaQuery(ProcessMaster.class)
                .eq(ProcessMaster::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(ProcessMaster::getSequenceNo)
                .orderByAsc(ProcessMaster::getProcessCode))
            .stream().map(ProcessResponse::from).toList();
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String tenantCode = TenantContext.tenantCode();
        String productCode = request.productCode().trim();
        validateProductCodeRule(tenantCode, productCode);
        if (productMasterMapper.selectCount(Wrappers.lambdaQuery(ProductMaster.class)
            .eq(ProductMaster::getTenantCode, tenantCode).eq(ProductMaster::getProductCode, productCode)) > 0) {
            throw new IllegalArgumentException("产品编号已存在：" + productCode);
        }
        LocalDateTime now = LocalDateTime.now();
        ProductMaster product = new ProductMaster();
        product.setId(UUID.randomUUID().toString());
        product.setTenantCode(tenantCode);
        product.setProductCode(productCode);
        product.setProductName(request.productName().trim());
        product.setSpecification(normalizeNullable(request.specification()));
        product.setStatus("ACTIVE");
        product.setCreatedBy(TenantContext.actor());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMasterMapper.insert(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProcessResponse createProcess(CreateProcessRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String tenantCode = TenantContext.tenantCode();
        String processCode = request.processCode().trim().toUpperCase(Locale.ROOT);
        if (processMasterMapper.selectCount(Wrappers.lambdaQuery(ProcessMaster.class)
            .eq(ProcessMaster::getTenantCode, tenantCode).eq(ProcessMaster::getProcessCode, processCode)) > 0) {
            throw new IllegalArgumentException("工序编码已存在：" + processCode);
        }
        LocalDateTime now = LocalDateTime.now();
        ProcessMaster process = new ProcessMaster();
        process.setId(UUID.randomUUID().toString());
        process.setTenantCode(tenantCode);
        process.setProcessCode(processCode);
        process.setProcessName(request.processName().trim());
        process.setSequenceNo(request.sequenceNo());
        process.setStatus("ACTIVE");
        process.setCreatedBy(TenantContext.actor());
        process.setCreatedAt(now);
        process.setUpdatedAt(now);
        processMasterMapper.insert(process);
        return ProcessResponse.from(process);
    }

    @Transactional
    public ProductResponse updateProductStatus(String id, String status) {
        inspectionAccessScopeService.requireTenantAdministrator();
        ProductMaster product = requireProduct(id);
        product.setStatus(normalizeStatus(status));
        product.setUpdatedAt(LocalDateTime.now());
        productMasterMapper.updateById(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProcessResponse updateProcessStatus(String id, String status) {
        inspectionAccessScopeService.requireTenantAdministrator();
        ProcessMaster process = requireProcess(id);
        process.setStatus(normalizeStatus(status));
        process.setUpdatedAt(LocalDateTime.now());
        processMasterMapper.updateById(process);
        return ProcessResponse.from(process);
    }

    private ProductMaster requireProduct(String id) {
        ProductMaster product = productMasterMapper.selectOne(Wrappers.lambdaQuery(ProductMaster.class)
            .eq(ProductMaster::getId, id).eq(ProductMaster::getTenantCode, TenantContext.tenantCode()));
        if (product == null) {
            throw new IllegalArgumentException("产品不存在或无权访问");
        }
        return product;
    }

    private ProcessMaster requireProcess(String id) {
        ProcessMaster process = processMasterMapper.selectOne(Wrappers.lambdaQuery(ProcessMaster.class)
            .eq(ProcessMaster::getId, id).eq(ProcessMaster::getTenantCode, TenantContext.tenantCode()));
        if (process == null) {
            throw new IllegalArgumentException("工序不存在或无权访问");
        }
        return process;
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(value) && !"INACTIVE".equals(value)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE 或 INACTIVE");
        }
        return value;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateProductCodeRule(String tenantCode, String productCode) {
        if (!Boolean.parseBoolean(parameterValue(tenantCode, PRODUCT_CODE_VALIDATION_KEY))) {
            return;
        }
        String pattern = parameterValue(tenantCode, PRODUCT_CODE_PATTERN_KEY);
        if (!StringUtils.hasText(pattern)) {
            return;
        }
        try {
            if (!productCode.matches(pattern.trim())) {
                throw new IllegalArgumentException("产品编号不符合当前租户配置的料号规则");
            }
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException("当前租户的料号规则无效，请在系统参数中修正", exception);
        }
    }

    private String parameterValue(String tenantCode, String key) {
        SystemParameter parameter = systemParameterMapper.selectOne(Wrappers.lambdaQuery(SystemParameter.class)
            .eq(SystemParameter::getTenantCode, tenantCode)
            .eq(SystemParameter::getParameterKey, key));
        return parameter == null ? null : parameter.getParameterValue();
    }
}
