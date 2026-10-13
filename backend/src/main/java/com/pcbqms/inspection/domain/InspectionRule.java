package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_inspection_rule")
public class InspectionRule {
    @TableId
    private String id;
    private String tenantCode;
    private String name;
    private String productCode;
    private String processCode;
    private String processName;
    private String taskType;
    private String templateId;
    private Boolean enabled;
    private Integer priority;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
