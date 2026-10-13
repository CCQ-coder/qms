package com.pcbqms.workorder.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_work_order")
public class WorkOrder {
    @TableId
    private String id;
    private String tenantCode;
    private String workOrderNo;
    private String productCode;
    private String productName;
    private String specification;
    private String customerCode;
    private String internalProductNo;
    private String customerPartNo;
    private String processTechnology;
    private String material;
    private String boardThickness;
    private String copperThickness;
    private String warehouseStatus;
    private LocalDate releasedAt;
    private LocalDate materialInputDate;
    private BigDecimal pcsLength;
    private BigDecimal pcsWidth;
    private String materialNo;
    private String materialName;
    private String materialSpecification;
    private BigDecimal standardMaterialConsumption;
    private Integer materialInputQuantity;
    private Integer cumulativeWarehouseQuantity;
    private Integer cumulativeScrapQuantity;
    private Integer cumulativeDefectQuantity;
    private LocalDate requiredDeliveryDate;
    private String productionNoticeNo;
    private LocalDate inputDate;
    private BigDecimal singlePcsArea;
    private BigDecimal pnlLength;
    private BigDecimal pnlWidth;
    private BigDecimal aBoardLength;
    private BigDecimal aBoardWidth;
    private BigDecimal bBoardLength;
    private BigDecimal bBoardWidth;
    private String formingMethod;
    private Integer layerCount;
    private Integer productionNoticeQuantity;
    private Integer productionNoticeOrderQuantity;
    private String customerOrder;
    private String customerOrderNo;
    private Integer customerOrderQuantity;
    private BigDecimal materialArea;
    private BigDecimal productionNoticeArea;
    private BigDecimal customerOrderArea;
    private Integer unreceivedQuantity;
    private LocalDate lastWarehouseDate;
    private Integer totalPanelQuantity;
    private Integer aBoardPanelQuantity;
    private Integer bBoardPanelQuantity;
    private String panelizationNo;
    private BigDecimal estimatedYieldRate;
    private BigDecimal plannedMaterialIssueQuantity;
    private String currentProcessCode;
    private String currentProcessName;
    private Integer plannedQuantity;
    private Integer completedQuantity;
    private String priority;
    private String processRouteVersion;
    private String miVersion;
    private String qualityStatus;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private String sourcePayloadHash;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
