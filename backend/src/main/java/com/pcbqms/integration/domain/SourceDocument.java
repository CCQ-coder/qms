package com.pcbqms.integration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_source_document")
public class SourceDocument {
    @TableId
    private String id;
    private String tenantCode;
    private String documentType;
    private String originalFilename;
    private String storagePath;
    private String status;
    private String extractedPayloadJson;
    private String confirmedPayloadJson;
    private String workOrderId;
    private String inspectionRecordId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
