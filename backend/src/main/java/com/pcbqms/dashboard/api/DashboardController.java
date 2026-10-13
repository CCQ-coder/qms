package com.pcbqms.dashboard.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.dashboard.application.DashboardApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardApplicationService dashboardApplicationService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.of(dashboardApplicationService.summary());
    }
}

