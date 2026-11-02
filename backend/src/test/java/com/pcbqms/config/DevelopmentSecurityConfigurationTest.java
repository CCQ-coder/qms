package com.pcbqms.config;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter.DeviceIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProductionSecurityConfigurationTest.ProbeController.class)
@ContextConfiguration(classes = ProductionSecurityConfigurationTest.TestApplication.class)
@Import({DevelopmentSecurityConfiguration.class, ProductionSecurityConfigurationTest.ProbeController.class})
@TestPropertySource(properties = "qms.security.development-mode=true")
class DevelopmentSecurityConfigurationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @Test void developmentModeCannotBypassDeviceTokens() throws Exception {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<DeviceIdentity>>any(), any()))
            .thenReturn(java.util.List.of(new DeviceIdentity("customer-b", "飞针01")));
        for (String path : java.util.List.of("/api/v1/flying-probe/device-files", "/api/v1/flying-probe/device-host-status")) {
            mvc.perform(post(path).header("X-Tenant-Code", "customer-b")).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("X-Device-Token", "A".repeat(43))).andExpect(status().isNoContent());
        }
    }
}
