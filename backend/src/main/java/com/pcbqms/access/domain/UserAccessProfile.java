package com.pcbqms.access.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_user_access_profile")
public class UserAccessProfile {
    @TableId
    private String id;
    private String tenantCode;
    private String userName;
    private String displayName;
    private String roleCode;
    private String status;
    private String passwordHash;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
