package com.pcbqms.gateway.infrastructure;

import com.pcbqms.gateway.config.GatewayProperties;
import com.pcbqms.gateway.domain.GatewayWorkOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class QmsWorkOrderClient {
    private final GatewayProperties properties;

    public void push(GatewayWorkOrder source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workOrderNo", source.workOrderNo());
        payload.put("productCode", source.productCode());
        payload.put("productName", source.productName());
        payload.put("specification", source.specification());
        payload.put("currentProcessCode", source.currentProcessCode());
        payload.put("currentProcessName", source.currentProcessName());
        payload.put("plannedQuantity", source.plannedQuantity());
        payload.put("batchNo", source.batchNo());
        payload.put("sourceKey", source.sourceKey());
        payload.put("sourceVersion", source.sourceUpdatedAt().toString());
        payload.put("customerCode", source.customerCode());
        payload.put("internalProductNo", source.internalProductNo());
        payload.put("customerPartNo", source.customerPartNo());
        payload.put("processTechnology", source.processTechnology());
        payload.put("material", source.material());
        payload.put("boardThickness", source.boardThickness());
        payload.put("copperThickness", source.copperThickness());
        payload.put("warehouseStatus", source.warehouseStatus());
        payload.put("releasedAt", source.materialInputDate());
        payload.put("materialInputDate", source.materialInputDate());
        payload.put("pcsLength", source.pcsLength());
        payload.put("pcsWidth", source.pcsWidth());
        payload.put("materialNo", source.materialNo());
        payload.put("materialName", source.materialName());
        payload.put("materialSpecification", source.materialSpecification());
        payload.put("standardMaterialConsumption", source.standardMaterialConsumption());
        payload.put("cumulativeWarehouseQuantity", source.cumulativeWarehouseQuantity());
        payload.put("requiredDeliveryDate", source.requiredDeliveryDate());
        payload.put("productionNoticeNo", source.productionNoticeNo());
        payload.put("inputDate", source.inputDate());
        payload.put("singlePcsArea", source.singlePcsArea());
        payload.put("aBoardLength", source.aBoardLength());
        payload.put("aBoardWidth", source.aBoardWidth());
        payload.put("bBoardLength", source.bBoardLength());
        payload.put("bBoardWidth", source.bBoardWidth());
        payload.put("formingMethod", source.formingMethod());
        payload.put("layerCount", source.layerCount());
        payload.put("productionNoticeQuantity", source.productionNoticeQuantity());
        payload.put("customerOrder", source.customerOrder());
        payload.put("customerOrderNo", source.customerOrderNo());
        payload.put("customerOrderQuantity", source.customerOrderQuantity());
        payload.put("materialArea", source.materialArea());
        payload.put("productionNoticeArea", source.productionNoticeArea());
        payload.put("customerOrderArea", source.customerOrderArea());
        payload.put("priority", source.priority());
        payload.put("processRouteVersion", source.processRouteVersion());
        payload.put("miVersion", source.miVersion());
        payload.put("totalPanelQuantity", source.totalPanelQuantity());
        payload.put("aBoardPanelQuantity", source.aBoardPanelQuantity());
        payload.put("bBoardPanelQuantity", source.bBoardPanelQuantity());
        payload.put("panelizationNo", source.panelizationNo());
        payload.put("estimatedYieldRate", source.estimatedYieldRate());
        payload.put("plannedMaterialIssueQuantity", source.plannedMaterialIssueQuantity());
        payload.put("materialInputQuantity", source.materialInputQuantity());
        payload.put("cumulativeScrapQuantity", source.cumulativeScrapQuantity());
        payload.put("cumulativeDefectQuantity", source.cumulativeDefectQuantity());
        payload.put("pnlLength", source.pnlLength());
        payload.put("pnlWidth", source.pnlWidth());
        payload.put("productionNoticeOrderQuantity", source.productionNoticeOrderQuantity());
        payload.put("unreceivedQuantity", source.unreceivedQuantity());
        payload.put("lastWarehouseDate", source.lastWarehouseDate());
        RestClient.create(properties.getQmsBaseUrl())
            .post()
            .uri("/api/v1/integrations/work-orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Tenant-Code", properties.getTenantCode())
            .header("X-QMS-Actor", "gateway.customer-b")
            .header("X-Gateway-Token", properties.getSharedToken())
            .headers(headers -> {
                if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
                    headers.setBearerAuth(properties.getBearerToken());
                }
            })
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }
}
