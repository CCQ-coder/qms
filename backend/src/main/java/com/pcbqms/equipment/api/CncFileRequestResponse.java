package com.pcbqms.equipment.api;

/** The gateway turns an approved response into its device-specific #OpenFile reply. */
public record CncFileRequestResponse(
    boolean approved,
    String message,
    String equipmentCode,
    String programNo,
    String programName,
    String programVersion,
    String fileUrl,
    String checksumSha256,
    Long fileSizeBytes
) {
    public static CncFileRequestResponse rejected(String equipmentCode, String message) {
        return new CncFileRequestResponse(false, message, equipmentCode, null, null, null, null, null, null);
    }
}
