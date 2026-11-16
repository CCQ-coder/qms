package com.pcbqms.administration.api;

import java.util.List;
import java.util.Map;

/** Menu visibility resolved for the current user's QMS role. */
public record MenuAccessResponse(
    String roleCode,
    List<String> activeMenuCodes,
    List<String> permittedMenuCodes,
    Map<String, String> menuNames
) { }
