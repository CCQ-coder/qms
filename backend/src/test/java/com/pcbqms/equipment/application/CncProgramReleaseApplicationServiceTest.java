package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.CncFileRequest;
import com.pcbqms.equipment.domain.CncProgramRelease;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.infrastructure.persistence.CncProgramReleaseMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CncProgramReleaseApplicationServiceTest {
    @Mock private CncProgramReleaseMapper programReleaseMapper;
    @Mock private EquipmentGatewayMappingApplicationService gatewayMappingService;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void returnsOnlyApprovedProgramForTheMappedDeviceAndBarcode() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-a");
        equipment.setAssetNo("CNC-01");
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
        CncProgramRelease release = new CncProgramRelease();
        release.setProgramNo("P100");
        release.setProgramName("外形锣板程序");
        release.setProgramVersion("v2");
        release.setFileUrl("https://files.example.test/cnc/P100-v2.nc");
        release.setChecksumSha256("0".repeat(64));
        release.setFileSizeBytes(2048L);
        when(programReleaseMapper.selectOne(any())).thenReturn(release);

        var response = service().requestFile(new CncFileRequest("operator-7", "cnc-01", "barcode-1"));

        assertThat(response.approved()).isTrue();
        assertThat(response.equipmentCode()).isEqualTo("CNC-01");
        assertThat(response.programNo()).isEqualTo("P100");
        assertThat(response.checksumSha256()).hasSize(64);
    }

    private CncProgramReleaseApplicationService service() {
        return new CncProgramReleaseApplicationService(programReleaseMapper, gatewayMappingService, equipmentMapper,
            inspectionAccessScopeService);
    }
}
