package com.mrp.mrp.service;

import com.mrp.mrp.controller.MrpDailyPlanningController;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MrpDailyPlanExportServiceTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportsAllPagedPlanRowsWithDailyColumnsAndWithoutCapacityLoads() throws Exception {
        grant("mrp_plan:export");
        RollingPlanningQueryService query = mock(RollingPlanningQueryService.class);
        List<MrpDailyPlanningController.GridRow> rows = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            boolean machine = i % 2 == 0;
            rows.add(new MrpDailyPlanningController.GridRow("r" + i, "永惠", "WH", "永惠仓",
                    "ROOT" + i, machine ? "ROOT" + i : "TERM" + i, machine ? "MACHINE_OUTPUT" : "PCBA",
                    "SELF_MADE", "", "台", Map.of("2026-08-03", "3"), Map.of(), "3"));
        }
        when(query.grid(eq(42L), anyInt(), eq(100), isNull(), isNull(), eq("MACHINE_OUTPUT"), eq(false))).thenAnswer(invocation -> {
            int page = invocation.getArgument(1);
            int from = (page - 1) * 100;
            int to = Math.min(from + 100, rows.size());
            return response(rows.subList(from, to), page, rows.size());
        });

        byte[] exported = new MrpDailyPlanExportService(query).export(42L);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheet("整机排产");
            assertNotNull(sheet);
            assertEquals("整机排产", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("整机料号", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("来源月份", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("2026-08-03", sheet.getRow(2).getCell(7).getStringCellValue());
            for (var row : sheet) {
                for (var cell : row) {
                    assertFalse(cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && "整机负荷".equals(cell.getStringCellValue()));
                }
            }
            assertEquals(103, sheet.getLastRowNum());
            assertEquals(3, sheet.getRow(3).getCell(6).getNumericCellValue());
            assertEquals("整机产出 / 台", sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("PCBA / 自制", sheet.getRow(54).getCell(3).getStringCellValue());
            assertEquals(3, sheet.getRow(54).getCell(7).getNumericCellValue());
            assertEquals(sheet.getRow(54).getCell(6).getNumericCellValue(), sheet.getRow(54).getCell(7).getNumericCellValue());
        }
        verify(query, times(2)).grid(eq(42L), anyInt(), eq(100), isNull(), isNull(), eq("MACHINE_OUTPUT"), eq(false));
    }

    @Test
    void exportViewRequiresItsOwnPermissionBeforeQueryingPlanData() throws Exception {
        RollingPlanningQueryService query = mock(RollingPlanningQueryService.class);
        MrpDailyPlanExportService service = new MrpDailyPlanExportService(query);

        grant("mrp_shortage:view");
        ResponseStatusException machineDenied = assertThrows(ResponseStatusException.class,
                () -> service.export(42L, "MACHINE_OUTPUT"));
        assertEquals(HttpStatus.FORBIDDEN, machineDenied.getStatusCode());
        verifyNoInteractions(query);

        when(query.grid(eq(42L), eq(1), eq(100), isNull(), isNull(), eq("TERMINAL"), eq(false)))
                .thenReturn(response(List.of(), 1, 0));
        assertEquals(0, service.export(42L, "TERMINAL").length);

        grant("mrp_plan:export");
        ResponseStatusException terminalDenied = assertThrows(ResponseStatusException.class,
                () -> service.export(42L, "TERMINAL"));
        assertEquals(HttpStatus.FORBIDDEN, terminalDenied.getStatusCode());
        verify(query, times(1)).grid(anyLong(), anyInt(), anyInt(), any(), any(), any(), anyBoolean());
    }

    private static void grant(String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "fixture", null, List.of(new SimpleGrantedAuthority(permission))));
    }

    private static MrpDailyPlanningController.DailyGridResponse response(
            List<MrpDailyPlanningController.GridRow> rows, int page, int total) {
        return new MrpDailyPlanningController.DailyGridResponse(rows, total, page, 100,
                List.of(LocalDate.of(2026, 8, 3)), List.of(), List.of(), List.of(), 7L,
                "TERMINAL", "MACHINE_OUTPUT", "v4-rolling-business-weeks");
    }
}
