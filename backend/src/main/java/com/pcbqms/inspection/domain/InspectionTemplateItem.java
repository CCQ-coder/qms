package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_inspection_template_item")
public class InspectionTemplateItem {
    @TableId
    private String id;
    private String tenantCode;
    private String templateId;
    private Integer sortOrder;
    private String itemCode;
    private String itemName;
    private String inputType;
    @TableField("is_required")
    private Boolean required;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String unit;
    private String optionsJson;
    private String helpText;
}
