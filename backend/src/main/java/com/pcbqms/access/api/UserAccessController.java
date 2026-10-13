package com.pcbqms.access.api;

import com.pcbqms.access.application.UserAccessAdministrationService;
import com.pcbqms.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access/users")
@RequiredArgsConstructor
public class UserAccessController {
    private final UserAccessAdministrationService userAccessAdministrationService;

    @GetMapping
    public ApiResponse<List<UserAccessResponse>> list() {
        return ApiResponse.of(userAccessAdministrationService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserAccessResponse> create(@Valid @RequestBody SaveUserAccessRequest request) {
        return ApiResponse.of(userAccessAdministrationService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAccessResponse> update(@PathVariable String id, @Valid @RequestBody SaveUserAccessRequest request) {
        return ApiResponse.of(userAccessAdministrationService.update(id, request));
    }
}
