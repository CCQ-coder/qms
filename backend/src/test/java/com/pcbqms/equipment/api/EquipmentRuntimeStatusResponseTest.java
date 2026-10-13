package com.pcbqms.equipment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentRuntimeStatusResponseTest {
    @Test
    void showsOnePhysicalMachineWithTwoIndependentProgramsAndAlarmPriority() {
        Equipment equipment = new Equipment();
        equipment.setId("equipment-21");
        equipment.setAssetNo("CNC-21");
        equipment.setName("锣机#21");
        EquipmentRuntimeStatus runtime = new EquipmentRuntimeStatus();
        runtime.setLastDeviceTime(LocalDateTime.of(2026, 10, 10, 10, 0));
        runtime.setLastReceivedAt(LocalDateTime.of(2026, 10, 10, 2, 0));
        var first = program("CNC-21_1", "PROCESSING", true, "PROGRAM-A", null);
        var second = program("CNC-21_2", "ALARM", true, "PROGRAM-B", "断刀报警");

        var response = EquipmentRuntimeStatusResponse.from(equipment, runtime, true, List.of(first, second));

        assertThat(response.equipmentCode()).isEqualTo("CNC-21");
        assertThat(response.effectiveStatus()).isEqualTo("ALARM");
        assertThat(response.alarmMessage()).isEqualTo("断刀报警");
        assertThat(response.programNo()).isNull();
        assertThat(response.programs()).extracting(EquipmentRuntimeProgramResponse::programNo)
            .containsExactly("PROGRAM-A", "PROGRAM-B");

        var offlineAlarm = program("CNC-21_2", "OFFLINE", false, "PROGRAM-B", "断刀报警");
        var recovered = EquipmentRuntimeStatusResponse.from(equipment, runtime, true, List.of(first, offlineAlarm));
        assertThat(recovered.effectiveStatus()).isEqualTo("PROCESSING");
        assertThat(recovered.alarmMessage()).isNull();
    }

    private EquipmentRuntimeProgramResponse program(String sourceName, String status, boolean online,
                                                    String programNo, String alarmMessage) {
        return new EquipmentRuntimeProgramResponse(sourceName, status, online, programNo, null,
            BigDecimal.TEN, "11", null, alarmMessage, 100,
            LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 2, 0));
    }
}
