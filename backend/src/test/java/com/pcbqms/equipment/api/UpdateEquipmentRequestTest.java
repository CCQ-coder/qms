package com.pcbqms.equipment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateEquipmentRequestTest {
    @Test
    void acceptsEditableEquipmentStatus() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        UpdateEquipmentRequest request = objectMapper.readValue("""
            {
              "name": "飞机机",
              "equipmentType": "成品测试设备",
              "status": "DISABLED"
            }
            """, UpdateEquipmentRequest.class);

        assertThat(objectMapper.convertValue(request, Map.class))
            .containsEntry("status", "DISABLED");
    }
}
