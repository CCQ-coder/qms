package com.pcbqms.inspection.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_nonconformity_detail")
public class NonconformityDetail {
    @TableId
    private String id;
    private String tenantCode;
    private String nonconformityId;
    private Integer lineNo;
    private String workOrderId;
    private String processCode;
    private String processName;
    private String unit;
    private BigDecimal singlePcsArea;
    private Integer scrapQuantity;
    private BigDecimal scrapArea;
    private String scrapReason;
    private String remark;
    private String status;
}
