package com.pcbqms.inspection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.pcbqms.inspection.application.FlyingProbeInspectionProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlyingProbeProjectionControllerTest {
    @Mock private FlyingProbeInspectionProjectionService projectionService;

    @Test
    void bindingRemainsSuccessfulWhenTheSeparateRetryWakeFails() {
        when(projectionService.bindManually("telemetry-1", "WO-1")).thenReturn("run-1");
        doThrow(new IllegalStateException("temporary database error"))
            .when(projectionService).wakeManuallyBoundRun("run-1");

        var response = new FlyingProbeProjectionController(projectionService)
            .bindWorkOrder("telemetry-1", new BindFlyingProbeWorkOrderRequest("WO-1"));

        assertThat(response.data()).isEqualTo("WO-1");
    }
}
