package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.SaveEquipmentGatewayMappingRequest;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentGatewayMappingMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentGatewayMappingApplicationServiceTest {
    @Mock private EquipmentGatewayMappingMapper mappingMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private EquipmentGatewayUnmatchedMessageApplicationService unmatchedMessageApplicationService;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void letsSupervisorsAndAdministratorsManageGatewayMappings() {
        TenantContext.set("customer-a", "supervisor.a");
        EquipmentGatewayMappingApplicationService service = new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);

        service.list();

        verify(inspectionAccessScopeService).requireSupervisorOrAdministrator();
        verify(inspectionAccessScopeService, never()).requireTenantAdministrator();
    }

    @Test
    void listsAnOldMappingWithNoTargetSoItCanBeRepaired() {
        TenantContext.set("customer-a", "admin.a");
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-1");
        mapping.setTenantCode("customer-a");
        mapping.setMachineName("CNC-15");
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping));
        EquipmentGatewayMappingApplicationService service = new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);

        var mappings = service.list();

        assertThat(mappings).hasSize(1);
        assertThat(mappings.getFirst().id()).isEqualTo("mapping-1");
        assertThat(mappings.getFirst().equipmentName()).isNull();
    }

    @Test
    void administratorCanReassignAndRenameAnExistingMapping() {
        TenantContext.set("customer-a", "admin.a");
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-1");
        mapping.setTenantCode("customer-a");
        mapping.setEquipmentId(null);
        mapping.setMachineName("CNC-OLD");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-2");
        equipment.setTenantCode("customer-a");
        equipment.setAssetNo("EQ-2");
        equipment.setName("Machine 2");
        when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);
        when(equipmentMapper.selectById("equipment-2")).thenReturn(equipment);
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping));
        EquipmentGatewayMappingApplicationService service = new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);

        var response = service.update("mapping-1", new SaveEquipmentGatewayMappingRequest(
            "equipment-2", null, " cnc-15 "));

        assertThat(response.equipmentCode()).isEqualTo("EQ-2");
        assertThat(response.machineName()).isEqualTo("CNC-15");
        assertThat(mapping.getEquipmentId()).isEqualTo("equipment-2");
        verify(mappingMapper).updateById(mapping);
    }

    @Test
    void administratorCannotEditAnotherTenantsMapping() {
        TenantContext.set("customer-a", "admin.a");
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-1");
        mapping.setTenantCode("customer-b");
        when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);
        EquipmentGatewayMappingApplicationService service = new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.update("mapping-1", new SaveEquipmentGatewayMappingRequest(
            "equipment-2", null, "CNC-15")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(mappingMapper, never()).updateById(any(EquipmentGatewayMapping.class));
    }
}
