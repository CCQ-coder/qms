package com.pcbqms.equipment.domain;

import java.util.Arrays;

/**
 * 设备台账允许使用的固定类型。类型名称同时是前端展示和业务筛选的稳定值，
 * 不应由用户自由录入。
 */
public enum EquipmentType {
    PRODUCTION("生产设备"),
    PROCESS_INSPECTION("制程检测设备"),
    FINISHED_TEST("成品测试设备"),
    MEASUREMENT("测量设备"),
    AUXILIARY("辅助设备");

    private final String displayName;

    EquipmentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static EquipmentType requireSupported(String displayName) {
        return Arrays.stream(values())
            .filter(type -> type.displayName.equals(displayName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("设备类型必须从固定类型中选择"));
    }
}
