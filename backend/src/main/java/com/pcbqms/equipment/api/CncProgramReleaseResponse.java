package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.CncProgramRelease;
import java.time.LocalDateTime;

public record CncProgramReleaseResponse(
    String id,
    String equipmentId,
    String barcode,
    String programNo,
    String programName,
    String programVersion,
    String fileUrl,
    String checksumSha256,
    Long fileSizeBytes,
    String status,
    LocalDateTime updatedAt
) {
    public static CncProgramReleaseResponse from(CncProgramRelease release) {
        return new CncProgramReleaseResponse(release.getId(), release.getEquipmentId(), release.getBarcode(),
            release.getProgramNo(), release.getProgramName(), release.getProgramVersion(), release.getFileUrl(),
            release.getChecksumSha256(), release.getFileSizeBytes(), release.getStatus(), release.getUpdatedAt());
    }
}
