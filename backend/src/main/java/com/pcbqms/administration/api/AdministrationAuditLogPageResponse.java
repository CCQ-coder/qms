package com.pcbqms.administration.api;

import java.util.List;

public record AdministrationAuditLogPageResponse(List<AdministrationAuditLogResponse> items, Long total) { }
