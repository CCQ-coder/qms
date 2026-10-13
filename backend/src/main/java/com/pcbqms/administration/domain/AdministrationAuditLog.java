package com.pcbqms.administration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_administration_audit_log")
public class AdministrationAuditLog {
    @TableId
    private String id;
    private String tenantCode;
    private String moduleCode;
    private String operation;
    private String targetCode;
    private String summary;
    private String actor;
    private LocalDateTime createdAt;
}
