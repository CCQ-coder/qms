package com.pcbqms.gateway.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.gateway.config.GatewayProperties;
import com.pcbqms.gateway.domain.GatewayWorkOrder;
import com.pcbqms.gateway.infrastructure.CursorStore;
import com.pcbqms.gateway.infrastructure.ErpWorkOrderReader;
import com.pcbqms.gateway.infrastructure.QmsWorkOrderClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderSyncJobTest {
    @Mock private CursorStore cursorStore;
    @Mock private ErpWorkOrderReader erpWorkOrderReader;
    @Mock private QmsWorkOrderClient qmsWorkOrderClient;

    @Test
    void readsWithOverlapAndAdvancesCursorAfterAllPushesSucceed() {
        LocalDateTime cursor = LocalDateTime.of(2026, 9, 4, 8, 0);
        GatewayProperties properties = new GatewayProperties();
        properties.setCursorOverlapSeconds(10);
        GatewayWorkOrder workOrder = workOrder(cursor.plusSeconds(5));
        when(cursorStore.read()).thenReturn(cursor);
        when(erpWorkOrderReader.findChangedAfter(cursor.minusSeconds(10))).thenReturn(List.of(workOrder));
        WorkOrderSyncJob job = new WorkOrderSyncJob(cursorStore, erpWorkOrderReader, qmsWorkOrderClient, properties);

        job.synchronize();

        verify(qmsWorkOrderClient).push(workOrder);
        verify(cursorStore).write(workOrder.sourceUpdatedAt());
    }

    @Test
    void doesNotAdvanceCursorWhenAnyPushFails() {
        LocalDateTime cursor = LocalDateTime.of(2026, 9, 4, 8, 0);
        GatewayProperties properties = new GatewayProperties();
        GatewayWorkOrder workOrder = workOrder(cursor.plusSeconds(5));
        when(cursorStore.read()).thenReturn(cursor);
        when(erpWorkOrderReader.findChangedAfter(any())).thenReturn(List.of(workOrder));
        doThrow(new IllegalStateException("QMS unavailable")).when(qmsWorkOrderClient).push(workOrder);
        WorkOrderSyncJob job = new WorkOrderSyncJob(cursorStore, erpWorkOrderReader, qmsWorkOrderClient, properties);

        assertThatThrownBy(job::synchronize).isInstanceOf(IllegalStateException.class);

        verify(cursorStore, never()).write(any());
    }

    private GatewayWorkOrder workOrder(LocalDateTime updatedAt) {
        return new GatewayWorkOrder("WO-001", "PCB-001", "双面板", null, "FQC", "成品检验", 100,
            "BATCH-001", "source-1", updatedAt);
    }
}
