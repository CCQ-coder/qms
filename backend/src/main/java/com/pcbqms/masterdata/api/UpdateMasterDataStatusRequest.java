package com.pcbqms.masterdata.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateMasterDataStatusRequest(@NotBlank(message = "不能为空") String status) {
}
