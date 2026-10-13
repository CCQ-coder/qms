package com.pcbqms.gateway.application;

import com.pcbqms.gateway.domain.GatewayWorkOrder;
import com.pcbqms.gateway.config.GatewayProperties;
import com.pcbqms.gateway.infrastructure.CursorStore;
import com.pcbqms.gateway.infrastructure.ErpWorkOrderReader;
import com.pcbqms.gateway.infrastructure.QmsWorkOrderClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderSyncJob {
    private final CursorStore cursorStore;
    private final ErpWorkOrderReader erpWorkOrderReader;
    private final QmsWorkOrderClient qmsWorkOrderClient;
    private final GatewayProperties properties;

    @Scheduled(fixedDelayString = "${gateway.fixed-delay-ms}")
    public void synchronize() {
        LocalDateTime cursor = cursorStore.read();
        LocalDateTime queryCursor = cursor.minusSeconds(Math.max(0, properties.getCursorOverlapSeconds()));
        List<GatewayWorkOrder> changedWorkOrders = erpWorkOrderReader.findChangedAfter(queryCursor);
        LocalDateTime nextCursor = cursor;
        for (GatewayWorkOrder workOrder : changedWorkOrders) {
            qmsWorkOrderClient.push(workOrder);
            if (workOrder.sourceUpdatedAt().isAfter(nextCursor)) {
                nextCursor = workOrder.sourceUpdatedAt();
            }
        }
        if (nextCursor.isAfter(cursor)) {
            cursorStore.write(nextCursor);
        }
        log.info("工单同步完成，查询起点={}，游标={}，成功同步 {} 条", queryCursor, nextCursor, changedWorkOrders.size());
    }
}
