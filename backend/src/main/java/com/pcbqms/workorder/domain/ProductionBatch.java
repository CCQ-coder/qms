package com.pcbqms.workorder.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_production_batch")
public class ProductionBatch {
    @TableId
    private String id;
    private String tenantCode;
    private String workOrderId;
    private String batchNo;
    private Integer plannedQuantity;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

