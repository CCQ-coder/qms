package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_capa")
public class Capa {
    @TableId
    private String id;
    private String tenantCode;
    private String capaNo;
    private String nonconformityId;
    private String problemDescription;
    private String teamMembers;
    private String containmentAction;
    private String rootCause;
    private String correctiveAction;
    private String preventiveAction;
    private String verificationResult;
    private String recognitionNotes;
    private String status;
    private String initiatedBy;
    private String approvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
