package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_nonconformity")
public class Nonconformity {
    @TableId
    private String id;
    private String tenantCode;
    private String ncrNo;
    private String inspectionRecordId;
    private String workOrderId;
    private String batchId;
    private String status;
    private LocalDate detectionDate;
    private String shiftName;
    private String processCode;
    private String processName;
    private String responsibleDepartment;
    private String remark;
    private String dispositionMethod;
    private String batchNo;
    private String initiatedBy;
    private LocalDateTime createdAt;
}

