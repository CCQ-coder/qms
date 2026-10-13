package com.pcbqms.administration.api;

import java.util.List;

/** Menu visibility resolved for the current user's QMS role. */
public record MenuAccessResponse(
    String roleCode,
    List<String> activeMenuCodes,
    List<String> permittedMenuCodes
) { }
