package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_inspection_task")
public class InspectionTask {
    @TableId
    private String id;
    private String tenantCode;
    private String taskNo;
    private String workOrderId;
    private String batchId;
    private String processCode;
    private String processName;
    private String taskType;
    private String templateId;
    private String sourceType;
    private String status;
    private String assigneeName;
    private LocalDateTime dueAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
