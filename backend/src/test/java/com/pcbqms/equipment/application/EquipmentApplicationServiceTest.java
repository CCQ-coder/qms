package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentResponse;
import com.pcbqms.equipment.api.EquipmentTypeResponse;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentAlertMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceItemMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceTemplateItemMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentApplicationServiceTest {
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentMaintenanceMapper maintenanceMapper;
    @Mock private EquipmentMaintenanceTemplateItemMapper maintenanceTemplateItemMapper;
    @Mock private EquipmentMaintenanceItemMapper maintenanceItemMapper;
    @Mock private EquipmentAlertMapper equipmentAlertMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorOnlySeesEquipmentForAssignedProcesses() {
        TenantContext.set("customer-a", "tester.a");
        Equipment fqcDevice = equipment("eq-1", "FQC");
        Equipment maskDevice = equipment("eq-2", "SOLDER_MASK");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(fqcDevice, maskDevice));
        EquipmentApplicationService service = service();

        var result = service.listEquipment();

        assertThat(result).extracting(EquipmentResponse::id).containsExactly("eq-1");
    }

    @Test
    void returnsDueSoonAndOverdueCalibrationStatus() {
        assertThat(EquipmentResponse.from(equipment("eq-1", "FQC")).calibrationStatus()).isEqualTo("DUE_SOON");
        Equipment overdue = equipment("eq-2", "FQC");
        overdue.setCalibrationDueAt(LocalDateTime.now().minusMinutes(1));

        assertThat(EquipmentResponse.from(overdue).calibrationStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void exposesFixedEquipmentTypesIncludingFinishedTestType() {
        EquipmentApplicationService service = service();

        assertThat(service.listEquipmentTypes())
            .extracting(EquipmentTypeResponse::name)
            .containsExactly("生产设备", "制程检测设备", "成品测试设备", "测量设备", "辅助设备");
    }

    @Test
    void deletesEquipmentWithoutMaintenanceRecords() {
        TenantContext.set("customer-a", "admin.a");
        Equipment equipment = equipment("eq-1", "FQC");
        when(equipmentMapper.selectById("eq-1")).thenReturn(equipment);
        when(maintenanceMapper.selectCount(any())).thenReturn(0L);
        EquipmentApplicationService service = service();

        service.deleteEquipment("eq-1");

        verify(equipmentMapper).deleteById("eq-1");
    }

    @Test
    void preventsDeletingEquipmentWithMaintenanceRecords() {
        TenantContext.set("customer-a", "admin.a");
        Equipment equipment = equipment("eq-1", "FQC");
        when(equipmentMapper.selectById("eq-1")).thenReturn(equipment);
        when(maintenanceMapper.selectCount(any())).thenReturn(1L);
        EquipmentApplicationService service = service();

        assertThatIllegalArgumentException().isThrownBy(() -> service.deleteEquipment("eq-1"))
            .withMessage("设备已关联 1 条维护记录，不能删除");
    }

    private Equipment equipment(String id, String processCode) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setTenantCode("customer-a");
        equipment.setAssetNo("ASSET-" + id);
        equipment.setName("测试设备");
        equipment.setEquipmentType("电测");
        equipment.setProcessCode(processCode);
        equipment.setCalibrationDueAt(LocalDateTime.now().plusDays(7));
        return equipment;
    }

    private EquipmentApplicationService service() {
        return new EquipmentApplicationService(equipmentMapper, maintenanceMapper, maintenanceTemplateItemMapper,
            maintenanceItemMapper, equipmentAlertMapper, inspectionAccessScopeService);
    }
}
