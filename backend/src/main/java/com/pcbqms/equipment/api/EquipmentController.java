package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {
    private final EquipmentApplicationService equipmentApplicationService;

    @GetMapping
    public ApiResponse<List<EquipmentResponse>> listEquipment() {
        return ApiResponse.of(equipmentApplicationService.listEquipment());
    }

    @GetMapping("/query")
    public ApiResponse<EquipmentPageResponse> queryEquipment(@ModelAttribute EquipmentQueryRequest request) {
        return ApiResponse.of(equipmentApplicationService.pageEquipment(request));
    }

    @GetMapping("/types")
    public ApiResponse<List<EquipmentTypeResponse>> listEquipmentTypes() {
        return ApiResponse.of(equipmentApplicationService.listEquipmentTypes());
    }

    @DeleteMapping("/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipment(@PathVariable String equipmentId) {
        equipmentApplicationService.deleteEquipment(equipmentId);
    }

    @GetMapping("/templates")
    public ApiResponse<List<MaintenanceTemplateItemResponse>> listMaintenanceTemplateItems(
        @RequestParam(required = false) String equipmentType,
        @RequestParam(required = false) String maintenanceType
    ) {
        return ApiResponse.of(equipmentApplicationService.listMaintenanceTemplateItems(equipmentType, maintenanceType));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MaintenanceTemplateItemResponse> saveMaintenanceTemplateItem(
        @Valid @RequestBody SaveMaintenanceTemplateItemRequest request
    ) {
        return ApiResponse.of(equipmentApplicationService.saveMaintenanceTemplateItem(request));
    }

    @DeleteMapping("/templates/{templateItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaintenanceTemplateItem(@PathVariable String templateItemId) {
        equipmentApplicationService.deleteMaintenanceTemplateItem(templateItemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EquipmentResponse> createEquipment(@Valid @RequestBody CreateEquipmentRequest request) {
        return ApiResponse.of(equipmentApplicationService.createOrUpdateEquipment(request));
    }

    @PutMapping("/{equipmentId}")
    public ApiResponse<EquipmentResponse> updateEquipment(
        @PathVariable String equipmentId,
        @Valid @RequestBody UpdateEquipmentRequest request
    ) {
        return ApiResponse.of(equipmentApplicationService.updateEquipment(equipmentId, request));
    }

    @GetMapping("/maintenance")
    public ApiResponse<List<EquipmentMaintenanceResponse>> listMaintenance(@RequestParam(required = false) String status) {
        return ApiResponse.of(equipmentApplicationService.listMaintenance(status));
    }

    @GetMapping("/maintenance/{maintenanceId}/items")
    public ApiResponse<List<MaintenanceItemResponse>> listMaintenanceItems(@PathVariable String maintenanceId) {
        return ApiResponse.of(equipmentApplicationService.listMaintenanceItems(maintenanceId));
    }

    @PostMapping("/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EquipmentMaintenanceResponse> createMaintenance(@Valid @RequestBody CreateMaintenanceRequest request) {
        return ApiResponse.of(equipmentApplicationService.createMaintenance(request));
    }

    @PostMapping("/maintenance/{maintenanceId}/complete")
    public ApiResponse<EquipmentMaintenanceResponse> completeMaintenance(
        @PathVariable String maintenanceId,
        @Valid @RequestBody CompleteMaintenanceRequest request
    ) {
        return ApiResponse.of(equipmentApplicationService.completeMaintenance(maintenanceId, request));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<EquipmentAlertResponse>> listEquipmentAlerts(@RequestParam(required = false) String status) {
        return ApiResponse.of(equipmentApplicationService.listEquipmentAlerts(status));
    }
}
