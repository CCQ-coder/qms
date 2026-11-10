package com.pcbqms.equipment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipmentTelemetryControllerTest {
    @Test
    void returnsTodayBadBoardsByMaterial() throws Exception {
        var summary = mock(FinishedTestDeviceSummaryService.class);
        when(summary.todayBadBoards()).thenReturn(new FinishedTestDeviceSummaryService.DailyMaterialBadBoards(
            "2026-11-10", List.of(new FinishedTestDeviceSummaryService.MaterialBadCount("42713", 24))));
        var controller = new EquipmentTelemetryController(
            mock(GatewayIntegrationAuthentication.class), mock(EquipmentTelemetryApplicationService.class),
            mock(EquipmentTelemetryQueryApplicationService.class), summary);

        MockMvcBuilders.standaloneSetup(controller).build()
            .perform(get("/api/v1/equipment/telemetry/today-bad-boards-by-material"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.date").value("2026-11-10"))
            .andExpect(jsonPath("$.data.materials[0].materialNo").value("42713"))
            .andExpect(jsonPath("$.data.materials[0].badCount").value(24));
    }

    @Test
    void acceptsAllDocumentedZhongboFields() throws Exception {
        var service = mock(EquipmentTelemetryApplicationService.class);
        when(service.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));
        var controller = new EquipmentTelemetryController(
            mock(GatewayIntegrationAuthentication.class), service, mock(EquipmentTelemetryQueryApplicationService.class),
            mock(FinishedTestDeviceSummaryService.class)
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/v1/equipment/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"events":[{"username":"probe-user","machineName":"0002","deviceTime":"2026-11-05T15:37:56",
                    "eventType":"ZHONGBO_TEST_REPORT","status":"PROCESSING","recordNo":"ZB-1","machiningCount":1,
                    "materialNo":"19250","workOrderNo":"WO-1","batchNo":"B-1","alarmCode":"E-1","alarmMessage":"detail",
                    "attributes":{"PortNumber":"19250","PanelsTested":1,"ErrorTested":0,"OpenErrors":0,
                    "ShortErrors":0,"totalPoints":22000,"utilizationRate":8.64,"utilizationDuration":285,
                    "Counts":1,"openThresh":20.0,"shortThresh":30000000.0,"hit":4744,"vendorStatus":"Run"}}]}
                    """))
            .andExpect(status().isOk());

        var captured = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(service).receive(captured.capture());
        var event = captured.getValue().events().getFirst();
        assertThat(event.machineName()).isEqualTo("0002");
        assertThat(event.username()).isEqualTo("probe-user");
        assertThat(event.deviceTime().toString()).isEqualTo("2026-11-05T15:37:56");
        assertThat(event.eventType()).isEqualTo("ZHONGBO_TEST_REPORT");
        assertThat(event.status()).isEqualTo("PROCESSING");
        assertThat(event.recordNo()).isEqualTo("ZB-1");
        assertThat(event.machiningCount()).isEqualTo(1);
        assertThat(event.materialNo()).isEqualTo("19250");
        assertThat(event.workOrderNo()).isEqualTo("WO-1");
        assertThat(event.batchNo()).isEqualTo("B-1");
        assertThat(event.alarmCode()).isEqualTo("E-1");
        assertThat(event.alarmMessage()).isEqualTo("detail");
        assertThat(event.attributes()).containsKeys("PortNumber", "PanelsTested", "ErrorTested", "OpenErrors",
            "ShortErrors", "totalPoints", "utilizationRate", "utilizationDuration", "Counts", "openThresh",
            "shortThresh", "hit", "vendorStatus");
    }

    @Test
    void forwardsHistoryEventTypeAndSearchFilters() throws Exception {
        var queryService = mock(EquipmentTelemetryQueryApplicationService.class);
        when(queryService.pageHistory(any())).thenReturn(new EquipmentTelemetryHistoryPageResponse(List.of(), null, 100));
        var controller = new EquipmentTelemetryController(
            mock(GatewayIntegrationAuthentication.class), mock(EquipmentTelemetryApplicationService.class), queryService,
            mock(FinishedTestDeviceSummaryService.class)
        );
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/v1/equipment/telemetry/history")
                .param("eventType", "FLYING_PROBE_TEST_COUNTERS")
                .param("workOrderNo", "WO-1")
                .param("internalProductNo", "02PT42777A01"))
            .andExpect(status().isOk());

        var query = ArgumentCaptor.forClass(EquipmentTelemetryHistoryQuery.class);
        verify(queryService).pageHistory(query.capture());
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(query.getValue());
        assertThat(json.path("eventType").asText()).isEqualTo("FLYING_PROBE_TEST_COUNTERS");
        assertThat(json.path("workOrderNo").asText()).isEqualTo("WO-1");
        assertThat(json.path("internalProductNo").asText()).isEqualTo("02PT42777A01");
    }
}
