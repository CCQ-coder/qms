package com.pcbqms.inspection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
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
    void batchMatchReportsMatchedRemainingSkippedAndFailedRecords() {
        when(projectionService.retryUnboundNow("matched")).thenReturn(true);
        when(projectionService.retryUnboundNow("remaining")).thenReturn(false);
        when(projectionService.retryUnboundNow("stale")).thenThrow(new IllegalArgumentException("already handled"));
        when(projectionService.retryUnboundNow("failed")).thenThrow(new IllegalStateException("database unavailable"));

        var response = new FlyingProbeProjectionController(projectionService)
            .batchMatchByMaterial(new FlyingProbeProjectionController.BatchMatchRequest(
                List.of("matched", "remaining", "stale", "failed")));

        assertThat(response.data()).isEqualTo(new FlyingProbeProjectionController.BatchMatchResponse(1, 1, 1, 1));
    }

    @Test
    void batchMatchRejectsAnEmptySelection() throws Exception {
        MockMvcBuilders.standaloneSetup(new FlyingProbeProjectionController(projectionService)).build()
            .perform(post("/api/v1/inspection/flying-probe-projections/batch-match-by-material")
                .contentType(MediaType.APPLICATION_JSON).content("{\"telemetryIds\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(projectionService);
    }

    @Test
    void batchMatchDoesNotHidePermissionFailures() {
        when(projectionService.retryUnboundNow("protected"))
            .thenThrow(new AccessDeniedException("not authorized"));

        assertThatThrownBy(() -> new FlyingProbeProjectionController(projectionService)
            .batchMatchByMaterial(new FlyingProbeProjectionController.BatchMatchRequest(List.of("protected"))))
            .isInstanceOf(AccessDeniedException.class);
    }
}
