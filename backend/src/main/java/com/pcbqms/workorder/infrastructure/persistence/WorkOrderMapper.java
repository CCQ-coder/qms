package com.pcbqms.workorder.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {
    @Select("SELECT * FROM qms_work_order WHERE tenant_code = #{tenantCode} "
        + "AND UPPER(TRIM(SUBSTRING_INDEX(erp_combined_panel_no, '_', 1))) = #{combinedPanelNo}")
    List<WorkOrder> findByCombinedPanelNo(@Param("tenantCode") String tenantCode,
        @Param("combinedPanelNo") String combinedPanelNo);
}

