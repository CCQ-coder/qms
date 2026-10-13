package com.pcbqms.access.api;

import com.pcbqms.access.domain.UserAccessProfile;
import java.util.List;

public record UserAccessResponse(String id, String userName, String displayName, String roleCode,
                                 String status, List<String> processCodes) {
    public static UserAccessResponse from(UserAccessProfile profile, List<String> processCodes) {
        return new UserAccessResponse(profile.getId(), profile.getUserName(), profile.getDisplayName(),
            profile.getRoleCode(), profile.getStatus(), processCodes);
    }
}
