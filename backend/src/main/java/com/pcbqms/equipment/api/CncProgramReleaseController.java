package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.CncProgramReleaseApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cnc/program-releases")
@RequiredArgsConstructor
public class CncProgramReleaseController {
    private final CncProgramReleaseApplicationService programReleaseApplicationService;

    @GetMapping
    public ApiResponse<List<CncProgramReleaseResponse>> list() {
        return ApiResponse.of(programReleaseApplicationService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CncProgramReleaseResponse> save(@Valid @RequestBody SaveCncProgramReleaseRequest request) {
        return ApiResponse.of(programReleaseApplicationService.save(request));
    }
}
