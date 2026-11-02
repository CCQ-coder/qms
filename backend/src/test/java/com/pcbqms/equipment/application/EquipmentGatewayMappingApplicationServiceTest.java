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

    @org.junit.jupiter.api.BeforeAll
    static void mappingMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""),
            EquipmentGatewayMapping.class);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void supervisorCannotIssueOrRevokeCredentials() {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("admin only"))
            .when(inspectionAccessScopeService).requireTenantAdministrator();
        var service = new EquipmentGatewayMappingApplicationService(mappingMapper, equipmentMapper,
            unmatchedMessageApplicationService, inspectionAccessScopeService);
        assertThatThrownBy(() -> service.issueUploadToken("mapping-1"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.revokeUploadToken("mapping-1"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(mappingMapper);
    }

    @Test
    void uploadTokensAreRandomRotatableRevocableAndAdminOnly() throws Exception {
        TenantContext.set("customer-a", "admin.a");
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-1"); mapping.setTenantCode("customer-a"); mapping.setMachineName("飞针01");
        when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);
        var service = new EquipmentGatewayMappingApplicationService(mappingMapper, equipmentMapper,
            unmatchedMessageApplicationService, inspectionAccessScopeService);
        String first = service.issueUploadToken("mapping-1");
        assertThat(java.util.Base64.getUrlDecoder().decode(first)).hasSize(32);
        assertThat(mapping.getUploadTokenHash()).isEqualTo(java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(first.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        String second = service.issueUploadToken("mapping-1");
        assertThat(second).isNotEqualTo(first);
        service.revokeUploadToken("mapping-1");
        assertThat(mapping.getUploadTokenHash()).isNull();
        verify(inspectionAccessScopeService, org.mockito.Mockito.times(3)).requireTenantAdministrator();
        mapping.setTenantCode("customer-b");
        assertThatThrownBy(() -> service.issueUploadToken("mapping-1")).isInstanceOf(IllegalArgumentException.class);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"machine", "equipment", "both", "neither"})
    void administratorCanReassignAndRenameAnExistingMapping(String changed) {
        String targetId = changed.equals("equipment") || changed.equals("both") ? "equipment-2" : "equipment-1";
        String machine = changed.equals("machine") || changed.equals("both") ? "CNC-15" : "CNC-OLD";
        TenantContext.set("customer-a", "admin.a");
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-1");
        mapping.setTenantCode("customer-a");
        mapping.setEquipmentId("equipment-1");
        mapping.setMachineName("CNC-OLD");
        mapping.setUploadTokenHash("old-device-token-hash");
        Equipment equipment = new Equipment();
        equipment.setId(targetId);
        equipment.setTenantCode("customer-a");
        equipment.setAssetNo("EQ-2");
        equipment.setName("Machine 2");
        when(mappingMapper.selectById("mapping-1")).thenReturn(mapping);
        when(equipmentMapper.selectById(targetId)).thenReturn(equipment);
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping));
        EquipmentGatewayMappingApplicationService service = new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);

        var response = service.update("mapping-1", new SaveEquipmentGatewayMappingRequest(
            targetId, null, machine));

        assertThat(response.equipmentCode()).isEqualTo("EQ-2");
        assertThat(response.machineName()).isEqualTo(machine);
        assertThat(mapping.getEquipmentId()).isEqualTo(targetId);
        assertThat(mapping.getUploadTokenHash()).isEqualTo(changed.equals("neither") ? "old-device-token-hash" : null);
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
