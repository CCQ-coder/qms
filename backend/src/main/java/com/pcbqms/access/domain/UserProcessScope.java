package com.pcbqms.access.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("qms_user_process_scope")
public class UserProcessScope {
    @TableId
    private String id;
    private String tenantCode;
    private String userName;
    private String processCode;
    private LocalDateTime createdAt;
}
