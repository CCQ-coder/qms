package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.api.SaveEquipmentGatewayMappingRequest;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentGatewayMappingMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.time.LocalDateTime;

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
    void ignoresASourceWithoutResolvingATargetDevice() {
        TenantContext.set("customer-a", "gateway");
        var ignored = mapping(null, "IGNORED-PC");
        ignored.setIgnored(true);
        when(mappingMapper.selectList(any())).thenReturn(List.of(ignored));
        assertThatThrownBy(() -> service().resolve(null, "IGNORED-PC"))
            .isInstanceOf(EquipmentGatewayMappingApplicationService.IgnoredEquipmentGatewayMappingException.class);
        verify(equipmentMapper, never()).selectById(any());
    }

    @Test
    void createsAnIgnoredSourceWithoutAnEquipmentAndRemovesPendingMessages() {
        TenantContext.set("customer-a", "admin");
        when(mappingMapper.selectList(any())).thenReturn(List.of());
        var result = service().create(new SaveEquipmentGatewayMappingRequest(null, null, " ignored-pc ", true));
        assertThat(result.ignored()).isTrue();
        assertThat(result.equipmentId()).isNull();
        assertThat(result.mappingIssue()).isNull();
        verify(equipmentMapper, never()).selectById(any());
        verify(unmatchedMessageApplicationService).removeResolvedSource(null, "IGNORED-PC");
    }

    @Test
    void canRestoreAnIgnoredSourceToAValidEquipment() {
        TenantContext.set("customer-a", "admin");
        var ignored = mapping(null, "IGNORED-PC");
        ignored.setIgnored(true);
        when(mappingMapper.selectById(ignored.getId())).thenReturn(ignored);
        when(mappingMapper.selectList(any())).thenReturn(List.of(ignored));
        when(equipmentMapper.selectById("valid")).thenReturn(equipment("valid", "customer-a"));
        var result = service().update(ignored.getId(), new SaveEquipmentGatewayMappingRequest("valid", null, "IGNORED-PC", false));
        assertThat(result.ignored()).isFalse();
        assertThat(result.valid()).isTrue();
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
    void classifiesMissingTargetAsSkippableWithoutDeletingTheMapping() {
        TenantContext.set("customer-a", "gateway");
        var mapping = mapping("deleted-device", "OLD-PC");
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping));

        assertThatThrownBy(() -> service().resolve(null, "OLD-PC"))
            .isInstanceOf(EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException.class)
            .hasMessageContaining("映射失效");
    }

    @Test
    void neverResolvesAnEquipmentOwnedByAnotherTenant() {
        TenantContext.set("customer-a", "gateway");
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("other", "OLD-PC")));
        when(equipmentMapper.selectById("other")).thenReturn(equipment("other", "customer-b"));

        assertThatThrownBy(() -> service().resolve(null, "OLD-PC"))
            .isInstanceOf(EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException.class);
    }

    @Test
    void listsValidAndBrokenMappingsWithoutExposingOtherTenantEquipment() {
        TenantContext.set("customer-a", "admin");
        when(mappingMapper.selectList(any())).thenReturn(List.of(
            mapping("valid", "CNC-15"), mapping("deleted", "OLD-PC"), mapping("other", "OTHER-PC")));
        when(equipmentMapper.selectById("valid")).thenReturn(equipment("valid", "customer-a"));
        when(equipmentMapper.selectById("deleted")).thenReturn(null);
        when(equipmentMapper.selectById("other")).thenReturn(equipment("other", "customer-b"));

        var result = service().list();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).valid()).isTrue();
        assertThat(result.get(0).equipmentName()).isEqualTo("Equipment valid");
        for (var row : result.subList(1, 3)) {
            assertThat(row.valid()).isFalse();
            assertThat(row.equipmentName()).isNull();
            assertThat(row.equipmentCode()).isNull();
            assertThat(row.mappingIssue()).isNotBlank();
        }
    }

    @Test
    void stillRejectsConflictingMappingsRatherThanChoosingADevice() {
        TenantContext.set("customer-a", "gateway");
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("one", "CNC-15"), mapping("two", "CNC-15")));

        assertThatThrownBy(() -> service().resolve("source", "CNC-15"))
            .isInstanceOf(IllegalArgumentException.class)
            .isNotInstanceOf(EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException.class)
            .hasMessageContaining("不同 QMS 设备");
    }

    @Test
    void repairsAnExistingMappingInPlaceAndClearsOnlyTheResolvedSource() {
        TenantContext.set("customer-a", "supervisor");
        var original = mapping("deleted-device", "OLD-PC");
        original.setCreatedBy("original-user");
        original.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(mappingMapper.selectById(original.getId())).thenReturn(original);
        when(equipmentMapper.selectById("replacement")).thenReturn(equipment("replacement", "customer-a"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(original));

        var result = service().update(original.getId(), new SaveEquipmentGatewayMappingRequest("replacement", null, " old-pc "));

        assertThat(result.id()).isEqualTo("mapping-deleted-device");
        assertThat(result.equipmentId()).isEqualTo("replacement");
        assertThat(result.machineName()).isEqualTo("OLD-PC");
        assertThat(result.valid()).isTrue();
        assertThat(original.getCreatedBy()).isEqualTo("original-user");
        assertThat(original.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
        assertThat(original.getUpdatedAt()).isNotNull();
        verify(mappingMapper).updateById(original);
        verify(unmatchedMessageApplicationService).removeResolvedSource(null, "OLD-PC");
        verify(inspectionAccessScopeService).requireSupervisorOrAdministrator();
    }

    @Test
    void renamesTheSourceWithoutClearingWarningsForTheOldName() {
        TenantContext.set("customer-a", "supervisor");
        var original = mapping("valid", "OLD-NAME");
        when(mappingMapper.selectById(original.getId())).thenReturn(original);
        when(equipmentMapper.selectById("valid")).thenReturn(equipment("valid", "customer-a"));
        when(mappingMapper.selectList(any())).thenReturn(List.of());

        var result = service().update(original.getId(), new SaveEquipmentGatewayMappingRequest("valid", null, " new-name "));

        assertThat(result.machineName()).isEqualTo("NEW-NAME");
        verify(unmatchedMessageApplicationService).removeResolvedSource(null, "NEW-NAME");
        verify(unmatchedMessageApplicationService, never()).removeResolvedSource(null, "OLD-NAME");
    }

    @Test
    void rejectsAnEditThatWouldTakeAnotherMappingsSource() {
        TenantContext.set("customer-a", "supervisor");
        var original = mapping("valid", "ORIGINAL");
        when(mappingMapper.selectById(original.getId())).thenReturn(original);
        when(equipmentMapper.selectById("replacement")).thenReturn(equipment("replacement", "customer-a"));
        when(mappingMapper.selectList(any())).thenReturn(List.of(mapping("other", "TAKEN")));

        assertThatThrownBy(() -> service().update(original.getId(), new SaveEquipmentGatewayMappingRequest("replacement", null, "TAKEN")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已经映射");
        assertThat(original.getEquipmentId()).isEqualTo("valid");
        assertThat(original.getMachineName()).isEqualTo("ORIGINAL");
        verify(mappingMapper, never()).updateById(any(EquipmentGatewayMapping.class));
        verify(unmatchedMessageApplicationService, never()).removeResolvedSource(any(), any());
    }

    @Test
    void rejectsEditingAnotherTenantsMapping() {
        TenantContext.set("customer-a", "supervisor");
        var original = mapping("other", "OLD-PC");
        original.setTenantCode("customer-b");
        when(mappingMapper.selectById(original.getId())).thenReturn(original);

        assertThatThrownBy(() -> service().update(original.getId(), new SaveEquipmentGatewayMappingRequest("valid", null, "OLD-PC")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无权操作");
        verify(mappingMapper, never()).updateById(any(EquipmentGatewayMapping.class));
        verify(equipmentMapper, never()).selectById(any());
    }

    @Test
    void rejectsChangingTheTargetToAnotherTenantsEquipment() {
        TenantContext.set("customer-a", "supervisor");
        var original = mapping("valid", "OLD-PC");
        when(mappingMapper.selectById(original.getId())).thenReturn(original);
        when(equipmentMapper.selectById("other")).thenReturn(equipment("other", "customer-b"));

        assertThatThrownBy(() -> service().update(original.getId(), new SaveEquipmentGatewayMappingRequest("other", null, "OLD-PC")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无权操作");
        verify(mappingMapper, never()).updateById(any(EquipmentGatewayMapping.class));
    }

    @Test
    void requiresManagementPermissionBeforeReadingOrEditing() {
        doThrow(new IllegalArgumentException("无管理权限")).when(inspectionAccessScopeService).requireSupervisorOrAdministrator();

        assertThatThrownBy(() -> service().update("mapping", new SaveEquipmentGatewayMappingRequest("valid", null, "PC")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("无管理权限");
        verify(mappingMapper, never()).selectById(any());
        verify(mappingMapper, never()).updateById(any(EquipmentGatewayMapping.class));
    }

    private EquipmentGatewayMappingApplicationService service() {
        return new EquipmentGatewayMappingApplicationService(
            mappingMapper, equipmentMapper, unmatchedMessageApplicationService, inspectionAccessScopeService);
    }

    private EquipmentGatewayMapping mapping(String equipmentId, String machineName) {
        var mapping = new EquipmentGatewayMapping();
        mapping.setId("mapping-" + equipmentId);
        mapping.setTenantCode("customer-a");
        mapping.setEquipmentId(equipmentId);
        mapping.setMachineName(machineName);
        return mapping;
    }

    private Equipment equipment(String id, String tenant) {
        var equipment = new Equipment();
        equipment.setId(id);
        equipment.setTenantCode(tenant);
        equipment.setAssetNo(id);
        equipment.setName("Equipment " + id);
        return equipment;
    }
}
