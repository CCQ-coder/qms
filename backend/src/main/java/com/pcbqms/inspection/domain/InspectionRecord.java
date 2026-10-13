package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_inspection_record")
public class InspectionRecord {
    @TableId
    private String id;
    private String tenantCode;
    private String taskId;
    private String verdict;
    private Integer defectiveQuantity;
    private String defectSummary;
    private String defectLocation;
    private String resultsJson;
    private String attachmentUrlsJson;
    private String equipmentAssetNo;
    private String clientRequestId;
    private String inspectedBy;
    private LocalDateTime inspectedAt;
    private LocalDateTime createdAt;
}
