package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_system_parameter")
public class SystemParameter {
    @TableId
    private String id;
    private String tenantCode;
    private String parameterKey;
    private String parameterValue;
    private String updatedBy;
    private LocalDateTime updatedAt;
}

