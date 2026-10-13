package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_inspection_template")
public class InspectionTemplate {
    @TableId
    private String id;
    private String tenantCode;
    private String templateCode;
    private String name;
    private String productCode;
    private String processCode;
    private String processName;
    private String taskType;
    private Integer versionNo;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
