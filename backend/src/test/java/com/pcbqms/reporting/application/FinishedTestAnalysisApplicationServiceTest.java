package com.pcbqms.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.reporting.domain.FinishedTestFact;
import com.pcbqms.reporting.infrastructure.persistence.FinishedTestAnalysisMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinishedTestAnalysisApplicationServiceTest {
    @Mock private FinishedTestAnalysisMapper mapper;
    @Mock private InspectionAccessScopeService accessScopeService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void groupsBoardCountsByOrderMaterialDeviceAndDate() {
        TenantContext.set("tenant-a", "supervisor");
        when(accessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor", "SUPERVISOR", true, Set.of()));
        when(mapper.selectFacts(eq("tenant-a"), eq(LocalDateTime.of(2026, 10, 20, 0, 0)), any()))
            .thenReturn(List.of(
                fact("2026-10-20T08:00:00", "WO-1", "M-1", "WTD-21", 10, 2),
                fact("2026-10-21T09:00:00", "WO-1", "M-1", "WTD-22", 5, 0),
                fact("2026-10-21T10:00:00", "WO-2", "M-2", "WTD-21", 4, 1)));

        var result = new FinishedTestAnalysisApplicationService(mapper, accessScopeService, new ObjectMapper())
            .analyze(null, null);

        assertThat(result.totalBoards()).isEqualTo(19);
        assertThat(result.goodBoards()).isEqualTo(16);
        assertThat(result.badBoards()).isEqualTo(3);
        assertThat(result.passRate()).isEqualTo(84.21);
        assertThat(result.byWorkOrder()).extracting(item -> item.key()).containsExactly("WO-1", "WO-2");
        assertThat(result.byWorkOrder().getFirst().totalBoards()).isEqualTo(15);
        assertThat(result.byMaterial()).extracting(item -> item.key()).containsExactly("M-1", "M-2");
        assertThat(result.byEquipment()).extracting(item -> item.key()).containsExactly("WTD-21", "WTD-22");
        assertThat(result.byDate()).extracting(item -> item.key()).containsExactly("2026-10-21", "2026-10-20");
    }

    @Test
    void excludesUnknownQuantitiesFromBoardRateAndClampsDateStart() {
        TenantContext.set("tenant-a", "supervisor");
        when(accessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor", "SUPERVISOR", true, Set.of()));
        when(mapper.selectFacts(eq("tenant-a"), eq(LocalDateTime.of(2026, 10, 20, 0, 0)), any()))
            .thenReturn(List.of(
                fact("2026-10-20T08:00:00", "WO-1", "M-1", "WTD-21", 4, 1),
                fact("2026-10-20T09:00:00", "WO-1", "M-1", "WTD-21", null, 0)));

        var result = new FinishedTestAnalysisApplicationService(mapper, accessScopeService, new ObjectMapper())
            .analyze(LocalDate.of(2024, 1, 1), null);

        assertThat(result.totalBoards()).isEqualTo(4);
        assertThat(result.passRate()).isEqualTo(75);
        assertThat(result.recordCount()).isEqualTo(2);
        assertThat(result.unknownQuantityRecords()).isEqualTo(1);
        verify(mapper).selectFacts(eq("tenant-a"), eq(LocalDateTime.of(2026, 10, 20, 0, 0)), any());
    }

    @Test
    void exportsTheSameBoardGroupsAsTheReport() throws Exception {
        TenantContext.set("tenant-a", "supervisor");
        when(accessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor", "SUPERVISOR", true, Set.of()));
        when(mapper.selectFacts(eq("tenant-a"), eq(LocalDateTime.of(2026, 10, 20, 0, 0)), any()))
            .thenReturn(List.of(fact("2026-10-20T08:00:00", "WO-1", "M-1", "WTD-21", 10, 2)));

        byte[] content = new FinishedTestAnalysisApplicationService(mapper, accessScopeService, new ObjectMapper())
            .exportExcel(null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("生产指令").getRow(1).getCell(0).getStringCellValue()).isEqualTo("WO-1");
            assertThat(workbook.getSheet("生产指令").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(10);
            assertThat(workbook.getSheet("料号").getRow(1).getCell(0).getStringCellValue()).isEqualTo("M-1");
            assertThat(workbook.getSheet("设备").getRow(1).getCell(0).getStringCellValue()).isEqualTo("WTD-21");
            assertThat(workbook.getSheet("日期").getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-10-20");
        }
    }

    private FinishedTestFact fact(String time, String order, String material, String equipment, Integer boards, int bad) {
        FinishedTestFact fact = new FinishedTestFact();
        fact.setInspectedAt(LocalDateTime.parse(time));
        fact.setWorkOrderNo(order);
        fact.setMaterialNo(material);
        fact.setEquipmentAssetNo(equipment);
        fact.setDefectiveQuantity(bad);
        fact.setResultsJson(boards == null ? "{}" : "{\"inspectionQuantity\":" + boards + "}");
        return fact;
    }
}
