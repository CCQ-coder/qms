package com.pcbqms.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_cnc_program_release")
public class CncProgramRelease {
    @TableId
    private String id;
    private String tenantCode;
    private String equipmentId;
    private String barcode;
    private String programNo;
    private String programName;
    private String programVersion;
    private String fileUrl;
    private String checksumSha256;
    private Long fileSizeBytes;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
