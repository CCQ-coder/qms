package com.pcbqms.inspection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.inspection.application.FlyingProbeInspectionProjectionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    @Test
    void manualBatchBindsSelectedFilesToTheChosenOrderAndWakesEachRun() {
        when(projectionService.bindRetryableUnboundManually("first", "WO-42")).thenReturn("run-1");
        when(projectionService.bindRetryableUnboundManually("second", "WO-42")).thenReturn("run-2");

        var response = new FlyingProbeProjectionController(projectionService)
            .batchBindWorkOrder(new FlyingProbeProjectionController.BatchBindRequest(
                List.of("first", "second", "first"), "WO-42"));

        assertThat(response.data()).isEqualTo(new FlyingProbeProjectionController.BatchBindResponse(2, 0, 0));
        verify(projectionService).wakeManuallyBoundRun("run-1");
        verify(projectionService).wakeManuallyBoundRun("run-2");
    }

    @Test
    void manualBatchReportsStaleAndFailedFilesWithoutHidingTheSuccessfulBinding() {
        when(projectionService.bindRetryableUnboundManually("matched", "WO-42")).thenReturn("run-1");
        when(projectionService.bindRetryableUnboundManually("stale", "WO-42"))
            .thenThrow(new IllegalArgumentException("already handled"));
        when(projectionService.bindRetryableUnboundManually("failed", "WO-42"))
            .thenThrow(new IllegalStateException("database unavailable"));

        var response = new FlyingProbeProjectionController(projectionService)
            .batchBindWorkOrder(new FlyingProbeProjectionController.BatchBindRequest(
                List.of("matched", "stale", "failed"), "WO-42"));

        assertThat(response.data()).isEqualTo(new FlyingProbeProjectionController.BatchBindResponse(1, 1, 1));
        verify(projectionService).wakeManuallyBoundRun("run-1");
    }

    @Test
    void manualBatchRejectsAnEmptySelection() throws Exception {
        MockMvcBuilders.standaloneSetup(new FlyingProbeProjectionController(projectionService)).build()
            .perform(post("/api/v1/inspection/flying-probe-projections/batch-bind-work-order")
                .contentType(MediaType.APPLICATION_JSON).content("{\"telemetryIds\":[],\"workOrderNo\":\"WO-42\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(projectionService);
    }

    @Test
    void manualBatchDoesNotHidePermissionFailures() {
        when(projectionService.bindRetryableUnboundManually("protected", "WO-42"))
            .thenThrow(new AccessDeniedException("not authorized"));

        assertThatThrownBy(() -> new FlyingProbeProjectionController(projectionService)
            .batchBindWorkOrder(new FlyingProbeProjectionController.BatchBindRequest(List.of("protected"), "WO-42")))
            .isInstanceOf(AccessDeniedException.class);
    }
}
