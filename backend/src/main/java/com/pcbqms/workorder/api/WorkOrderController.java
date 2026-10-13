package com.pcbqms.workorder.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderApplicationService workOrderApplicationService;

    @GetMapping
    public ApiResponse<List<WorkOrderResponse>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.of(workOrderApplicationService.list(keyword));
    }

    @GetMapping("/query")
    public ApiResponse<WorkOrderPageResponse> query(@ModelAttribute WorkOrderQueryRequest query) {
        return ApiResponse.of(workOrderApplicationService.page(query));
    }

    @GetMapping("/scan/{code}")
    public ApiResponse<ScanLookupResponse> scan(@PathVariable String code) {
        return ApiResponse.of(workOrderApplicationService.lookupByScanCode(code));
    }
}
