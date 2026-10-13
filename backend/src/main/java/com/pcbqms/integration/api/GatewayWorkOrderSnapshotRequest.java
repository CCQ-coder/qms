package com.pcbqms.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Full source-key snapshot emitted after a successful ERP file synchronization. */
public record GatewayWorkOrderSnapshotRequest(
    @NotBlank(message = "不能为空") String sourceKeyPrefix,
    @NotEmpty(message = "不能为空") List<@NotBlank(message = "不能为空") String> sourceKeys
) {
}
