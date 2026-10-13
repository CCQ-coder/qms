package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentRuntimeStatusApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/runtime-statuses")
@RequiredArgsConstructor
public class EquipmentRuntimeStatusController {
    private final EquipmentRuntimeStatusApplicationService runtimeStatusApplicationService;

    @GetMapping
    public ApiResponse<List<EquipmentRuntimeStatusResponse>> list() {
        return ApiResponse.of(runtimeStatusApplicationService.list());
    }
}
