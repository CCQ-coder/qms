package com.pcbqms.equipment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipmentTelemetryControllerTest {
    @Test
    void forwardsOptionalEventTypeFromHistoryRequest() throws Exception {
        var queryService = mock(EquipmentTelemetryQueryApplicationService.class);
        when(queryService.pageHistory(any())).thenReturn(new EquipmentTelemetryHistoryPageResponse(List.of(), null, 100));
        var controller = new EquipmentTelemetryController(
            mock(GatewayIntegrationAuthentication.class), mock(EquipmentTelemetryApplicationService.class), queryService
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/v1/equipment/telemetry/history")
                .param("eventType", "FLYING_PROBE_TEST_COUNTERS"))
            .andExpect(status().isOk());

        var query = ArgumentCaptor.forClass(EquipmentTelemetryHistoryQuery.class);
        verify(queryService).pageHistory(query.capture());
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(query.getValue());
        assertThat(json.path("eventType").asText()).isEqualTo("FLYING_PROBE_TEST_COUNTERS");
    }
}
