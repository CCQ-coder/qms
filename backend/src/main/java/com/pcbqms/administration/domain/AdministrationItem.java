package com.pcbqms.administration.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_administration_item")
public class AdministrationItem {
    @TableId
    private String id;
    private String tenantCode;
    private String catalogType;
    private String itemCode;
    private String itemName;
    private String parentCode;
    private String description;
    private String status;
    private Integer sortOrder;
    private String attributesJson;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
