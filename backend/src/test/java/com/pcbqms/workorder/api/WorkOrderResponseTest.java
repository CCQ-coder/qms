package com.pcbqms.workorder.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcbqms.workorder.domain.WorkOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOrderResponseTest {
    @Test
    void closedActiveWorkOrderIsReportedAsCompleted() {
        WorkOrder workOrder = workOrder("ACTIVE", "CLOSED");

        assertThat(WorkOrderResponse.from(workOrder, List.of()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void openAndInactiveWorkOrdersKeepTheirStatus() {
        assertThat(WorkOrderResponse.from(workOrder("ACTIVE", "PENDING"), List.of()).status()).isEqualTo("ACTIVE");
        assertThat(WorkOrderResponse.from(workOrder("INACTIVE", "CLOSED"), List.of()).status()).isEqualTo("INACTIVE");
    }

    private WorkOrder workOrder(String status, String warehouseStatus) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setStatus(status);
        workOrder.setWarehouseStatus(warehouseStatus);
        return workOrder;
    }
}
