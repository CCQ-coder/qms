package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/gateway-mappings")
@RequiredArgsConstructor
public class EquipmentGatewayMappingController {
    private final EquipmentGatewayMappingApplicationService mappingApplicationService;

    @GetMapping
    public ApiResponse<List<EquipmentGatewayMappingResponse>> list() {
        return ApiResponse.of(mappingApplicationService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EquipmentGatewayMappingResponse> create(@Valid @RequestBody SaveEquipmentGatewayMappingRequest request) {
        return ApiResponse.of(mappingApplicationService.create(request));
    }

    @PutMapping("/{mappingId}")
    public ApiResponse<EquipmentGatewayMappingResponse> update(@PathVariable String mappingId,
            @Valid @RequestBody SaveEquipmentGatewayMappingRequest request) {
        return ApiResponse.of(mappingApplicationService.update(mappingId, request));
    }

    @DeleteMapping("/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String mappingId) {
        mappingApplicationService.delete(mappingId);
    }
}
