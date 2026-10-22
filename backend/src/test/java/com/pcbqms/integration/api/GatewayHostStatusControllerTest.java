package com.pcbqms.integration.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.integration.application.GatewayHostStatusApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class GatewayHostStatusControllerTest {
    private final GatewayHostStatusApplicationService service = mock(GatewayHostStatusApplicationService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    private org.springframework.test.web.servlet.MockMvc mvc() {
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(
            new GatewayHostStatusController(new GatewayIntegrationAuthentication("gateway-secret"), service))
            .setValidator(validator).build();
    }

    @Test
    void recordsValidStatusAndRejectsInvalidFields() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/api/v1/gateway/host-status")
                .header("X-Gateway-Token", "gateway-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hostName":"SCBGX","sampledAt":"2026-10-22T10:00:00Z","cpuPercent":12.5,
                     "memoryTotalBytes":100,"memoryAvailableBytes":40,"systemDiskTotalBytes":200,
                     "systemDiskFreeBytes":80,"uptimeSeconds":300}
                    """))
            .andExpect(status().isNoContent());
        verify(service).record(any(GatewayHostStatusRequest.class));

        mvc.perform(post("/api/v1/gateway/host-status")
                .header("X-Gateway-Token", "gateway-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"hostName":"","sampledAt":"2026-10-22T10:00:00Z","cpuPercent":101,
                     "memoryTotalBytes":100,"memoryAvailableBytes":40,"systemDiskTotalBytes":200,
                     "systemDiskFreeBytes":80,"uptimeSeconds":300}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listsLatestStatusInApiResponse() throws Exception {
        when(service.listLatest()).thenReturn(List.of(new GatewayHostStatusResponse("SCBGX",
            Instant.parse("2026-10-22T10:00:00Z"), new BigDecimal("12.50"),
            100, 40, 200, 80, 300, Instant.parse("2026-10-22T10:00:01Z"),
            "equipment-scbgx", "SCBGX", "飞针机采集电脑")));

        mvc().perform(get("/api/v1/gateway/host-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].hostName").value("SCBGX"))
            .andExpect(jsonPath("$.data[0].equipmentCode").value("SCBGX"))
            .andExpect(jsonPath("$.data[0].memoryAvailableBytes").value(40));
    }
}
