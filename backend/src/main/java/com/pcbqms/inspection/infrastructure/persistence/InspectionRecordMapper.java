package com.pcbqms.inspection.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcbqms.inspection.domain.InspectionRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {
    @Select("""
        <script>
        SELECT record.* FROM qms_inspection_record record
        JOIN qms_inspection_task task ON task.id = record.task_id AND task.tenant_code = record.tenant_code
        WHERE record.tenant_code = #{tenantCode} AND task.status = 'COMPLETED'
          AND (task.task_type = 'FINISHED' OR task.process_code = 'FQC')
        <if test="processCodes != null">
          AND task.process_code IN
          <foreach collection="processCodes" item="code" open="(" separator="," close=")">#{code}</foreach>
        </if>
        <if test="cursorTime != null">
          AND (record.inspected_at &lt; #{cursorTime}
               OR (record.inspected_at = #{cursorTime} AND record.id &lt; #{cursorId}))
        </if>
        ORDER BY record.inspected_at DESC, record.id DESC
        LIMIT #{limit}
        </script>
        """)
    List<InspectionRecord> selectFinishedPage(
        @Param("tenantCode") String tenantCode, @Param("processCodes") List<String> processCodes,
        @Param("cursorTime") LocalDateTime cursorTime, @Param("cursorId") String cursorId,
        @Param("limit") int limit);
}

