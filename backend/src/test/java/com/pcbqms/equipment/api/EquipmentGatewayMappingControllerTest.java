package com.pcbqms.equipment.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipmentGatewayMappingControllerTest {
    @Test
    void acceptsAnIgnoredSourceWithoutATargetEquipment() throws Exception {
        var service = mock(EquipmentGatewayMappingApplicationService.class);
        var request = new SaveEquipmentGatewayMappingRequest(null, null, "IGNORED-PC", true);
        when(service.update("mapping-1", request)).thenReturn(new EquipmentGatewayMappingResponse(
            "mapping-1", null, null, null, null, "IGNORED-PC", LocalDateTime.now(), false, null, true));
        var mvc = MockMvcBuilders.standaloneSetup(new EquipmentGatewayMappingController(service)).build();
        mvc.perform(put("/api/v1/equipment/gateway-mappings/mapping-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineName\":\"IGNORED-PC\",\"ignored\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ignored").value(true));
        verify(service).update("mapping-1", request);
    }

    @Test
    void updatesTheRequestedMappingThroughPut() throws Exception {
        var service = mock(EquipmentGatewayMappingApplicationService.class);
        var request = new SaveEquipmentGatewayMappingRequest("equipment-2", null, "CNC-15");
        when(service.update("mapping-1", request)).thenReturn(new EquipmentGatewayMappingResponse(
            "mapping-1", "equipment-2", "EQ-2", "Machine 2", null, "CNC-15", LocalDateTime.now(), true, null));
        var mvc = MockMvcBuilders.standaloneSetup(new EquipmentGatewayMappingController(service)).build();

        mvc.perform(put("/api/v1/equipment/gateway-mappings/mapping-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\":\"equipment-2\",\"machineName\":\"CNC-15\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("mapping-1"))
            .andExpect(jsonPath("$.data.equipmentId").value("equipment-2"))
            .andExpect(jsonPath("$.data.valid").value(true));
        verify(service).update("mapping-1", request);
    }

    @Test
    void rejectsABlankTargetBeforeCallingTheService() throws Exception {
        var service = mock(EquipmentGatewayMappingApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EquipmentGatewayMappingController(service)).build();

        mvc.perform(put("/api/v1/equipment/gateway-mappings/mapping-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\":\" \",\"machineName\":\"CNC-15\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
