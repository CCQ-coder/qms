package com.pcbqms.equipment.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipmentGatewayMappingControllerTest {
    @Test
    void acceptsAnEditForAnExistingMapping() throws Exception {
        var service = mock(EquipmentGatewayMappingApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EquipmentGatewayMappingController(service)).build();

        mvc.perform(put("/api/v1/equipment/gateway-mappings/mapping-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\":\"equipment-2\",\"machineName\":\"CNC-15\"}"))
            .andExpect(status().isOk());
    }
}
