package com.pcbqms.integration.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import com.pcbqms.workorder.api.CreateWorkOrderRequest;
import com.pcbqms.workorder.api.WorkOrderResponse;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/work-orders")
@RequiredArgsConstructor
public class GatewayWorkOrderController {
    private final GatewayIntegrationAuthentication gatewayIntegrationAuthentication;
    private final WorkOrderApplicationService workOrderApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkOrderResponse> receive(
        @RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken,
        @Valid @RequestBody GatewayWorkOrderRequest request
    ) {
        gatewayIntegrationAuthentication.verify(gatewayToken);
        return ApiResponse.of(workOrderApplicationService.upsertFromIntegration(new CreateWorkOrderRequest(
            request.workOrderNo(), request.productCode(), request.productName(), request.specification(),
            request.currentProcessCode(), request.currentProcessName(), request.plannedQuantity(), request.batchNo(),
            "GATEWAY", request.sourceKey(), request.sourceVersion(),
            request.customerCode(), request.internalProductNo(), request.customerPartNo(), request.processTechnology(),
            request.material(), request.boardThickness(), request.copperThickness(), request.warehouseStatus(), request.releasedAt(),
            request.materialInputDate(), request.pcsLength(), request.pcsWidth(), request.materialNo(), request.materialName(),
            request.materialSpecification(), request.standardMaterialConsumption(), request.cumulativeWarehouseQuantity(),
            request.requiredDeliveryDate(), request.productionNoticeNo(), request.inputDate(), request.singlePcsArea(),
            request.aBoardLength(), request.aBoardWidth(), request.bBoardLength(), request.bBoardWidth(), request.formingMethod(),
            request.layerCount(), request.productionNoticeQuantity(), request.customerOrder(), request.customerOrderNo(),
            request.customerOrderQuantity(), request.materialArea(), request.productionNoticeArea(), request.customerOrderArea(),
            request.priority(), request.processRouteVersion(), request.miVersion(), request.totalPanelQuantity(),
            request.aBoardPanelQuantity(), request.bBoardPanelQuantity(), request.panelizationNo(), request.estimatedYieldRate(),
            request.plannedMaterialIssueQuantity(), request.materialInputQuantity(), request.cumulativeScrapQuantity(),
            request.cumulativeDefectQuantity(), request.pnlLength(), request.pnlWidth(), request.productionNoticeOrderQuantity(),
            request.unreceivedQuantity(), request.lastWarehouseDate()
        )));
    }

    /**
     * A complete ERP export can tell us which previously synchronized records
     * disappeared at source. We mark only this gateway's records inactive;
     * inspection and traceability data is deliberately never deleted.
     */
    @PostMapping("/snapshot")
    public ApiResponse<GatewayWorkOrderSnapshotResponse> reconcileSnapshot(
        @RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken,
        @Valid @RequestBody GatewayWorkOrderSnapshotRequest request
    ) {
        gatewayIntegrationAuthentication.verify(gatewayToken);
        return ApiResponse.of(new GatewayWorkOrderSnapshotResponse(
            workOrderApplicationService.deactivateMissingGatewayWorkOrders(
                request.sourceKeyPrefix(), request.sourceKeys())
        ));
    }
}
