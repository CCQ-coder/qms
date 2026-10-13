package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentGatewayUnmatchedMessageApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/gateway-unmatched")
@RequiredArgsConstructor
public class EquipmentGatewayUnmatchedMessageController {
    private final EquipmentGatewayUnmatchedMessageApplicationService unmatchedMessageApplicationService;

    @GetMapping
    public ApiResponse<List<EquipmentGatewayUnmatchedMessageResponse>> list() {
        return ApiResponse.of(unmatchedMessageApplicationService.list());
    }
}
