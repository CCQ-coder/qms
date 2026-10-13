package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
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
    private String initiatedBy;
    private LocalDateTime createdAt;
}

