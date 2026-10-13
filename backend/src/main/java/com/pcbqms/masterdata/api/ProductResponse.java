package com.pcbqms.masterdata.api;

import com.pcbqms.masterdata.domain.ProductMaster;

public record ProductResponse(String id, String productCode, String productName, String specification, String status) {
    public static ProductResponse from(ProductMaster product) {
        return new ProductResponse(product.getId(), product.getProductCode(), product.getProductName(),
            product.getSpecification(), product.getStatus());
    }
}
