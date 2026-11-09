package com.mrp.mrp.service;

import com.mrp.audit.service.AuditService;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.common.security.CurrentUser;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.mrp.repository.ConfigMasterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionCalendarMasterTest {
    @Mock ConfigMasterMapper mapper;
    @Mock AuditService audit;
    @Mock MaterialMapper materials;
    @Mock FactoryMasterService factories;
    ConfigMasterService service;
    final LocalDate start = LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 2);

    @BeforeEach void setup() {
        lenient().when(factories.enabled(anyString())).thenReturn(true);
        service = new ConfigMasterService(mapper, audit, materials, factories);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(9L, "admin"), null));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void legacyUnitAndFractionalWholeMachineCapacityRequireExplicitReconfiguration() {
        var legacy = row(start,"1000"); legacy.setCapacityUnit("件");
        assertThrows(IllegalStateException.class,() -> service.createProductionCalendar(legacy));
        var fractional = row(start,"1000.5");
        assertThrows(IllegalStateException.class,() -> service.createProductionCalendar(fractional));
        verify(mapper,never()).insertProductionCalendar(any());
    }
    @Test void newRestDayDefaultsToFixedMachineUnitWithoutChangingLegacyReads() {
        var rest = row(start,"0"); rest.setProductionDay(false); rest.setCapacityUnit(null);
        when(mapper.insertProductionCalendar(any())).thenReturn(1);
        service.createProductionCalendar(rest);
        assertEquals("台",rest.getCapacityUnit()); assertEquals(BigDecimal.ZERO,rest.getDailyCapacity());
    }
    @Test void monthIncludesMissingDatesAndDoesNotInventProductionDays() {
        when(mapper.selectProductionCalendarRange("爱培科", start, LocalDate.of(2026,10,31))).thenReturn(List.of(row(start,"1000")));
        var rows = service.productionCalendarMonth("爱培科", YearMonth.of(2026,10));
        assertEquals(31, rows.size()); assertEquals(new BigDecimal("1000"), rows.getFirst().getDailyCapacity());
        assertNull(rows.get(1).getProductionDay()); assertNull(rows.get(1).getDailyCapacity());
    }
    @Test void previewPreservesExistingExceptionAndDoesNotMutateMaster() {
        var existing = row(start, "250");
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(existing));
        var p = service.previewCalendarBatch(request(false,null,null));
        assertEquals(1,p.inserted()); assertEquals(0,p.updated()); assertEquals(1,p.preserved());
        assertEquals(new BigDecimal("250"),existing.getDailyCapacity()); verify(mapper,never()).updateProductionCalendar(any());
    }
    @Test void overwritePreviewDoesNotMutateOldValuesNeededByAudit() {
        var existing = row(start,"250");
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(existing));
        var p = service.previewCalendarBatch(request(true,null,null));
        assertEquals(1,p.updated()); assertEquals(new BigDecimal("250"),existing.getDailyCapacity());
        assertEquals(new BigDecimal("1000"),p.rows().getFirst().getDailyCapacity());
    }
    @Test void commitRejectsStaleTargetAndMissingPreviewWithoutWrites() {
        var existing = row(start,"250");
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(existing));
        var p = service.previewCalendarBatch(request(true,null,null));
        var changed = row(start,"500"); changed.setRevision(2);
        when(mapper.lockProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(changed));
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.commitCalendarBatch(request(true,p.fingerprint(),null)));
        assertThrowsExactly(IllegalStateException.class, () -> service.commitCalendarBatch(request(true,null,null)));
        verify(mapper,never()).updateProductionCalendar(any()); verify(mapper,never()).insertProductionCalendar(any());
    }
    @Test void copyFingerprintIncludesSourceAndRejectsSourceChanges() {
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of());
        when(mapper.selectProductionCalendarRange("永惠",start,end)).thenReturn(List.of(row(start,"200"),row(end,"200")));
        var p = service.previewCalendarBatch(request(true,null,"永惠"));
        when(mapper.lockProductionCalendarRange("爱培科",start,end)).thenReturn(List.of());
        when(mapper.lockProductionCalendarRange("永惠",start,end)).thenReturn(List.of(row(start,"300"),row(end,"300")));
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.commitCalendarBatch(request(true,p.fingerprint(),"永惠")));
        verify(mapper,never()).insertProductionCalendar(any());
    }
    @Test void batchCommitUsesPreviewAndWritesAuditForEachChangedDay() {
        var existing = row(start,"250");
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(existing));
        var p = service.previewCalendarBatch(request(true,null,null));
        when(mapper.lockProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(existing));
        when(mapper.updateProductionCalendar(any())).thenReturn(1);
        when(mapper.insertProductionCalendar(any())).thenReturn(1);
        var saved = service.commitCalendarBatch(request(true,p.fingerprint(),null));
        assertEquals(1,saved.updated()); assertEquals(1,saved.inserted());
        verify(mapper).updateProductionCalendar(argThat(r -> new BigDecimal("1000").equals(r.getDailyCapacity()) && r.getRevision()==1));
        verify(mapper).insertProductionCalendar(argThat(r -> r.getCalendarDate().equals(end)));
        verify(audit).log(eq(9L),isNull(),eq("爱培科"),eq("UPDATE"),eq("mrp_production_calendar"),eq("爱培科|2026-10-01"),eq(existing),any(),isNull(),isNull());
    }
    @Test void validationsRejectMissingUnitNegativeRestAndDecimalOverflow() {
        var missing = row(start,"1000"); missing.setCapacityUnit(null);
        assertThrows(IllegalStateException.class,()->service.createProductionCalendar(missing));
        var rest = row(start,"1"); rest.setProductionDay(false);
        assertThrows(IllegalStateException.class,()->service.createProductionCalendar(rest));
        var tooPrecise = row(start,"1.0000001");
        assertThrows(IllegalStateException.class,()->service.createProductionCalendar(tooPrecise));
        var tooLarge = row(start,"1000000000000");
        assertThrows(IllegalStateException.class,()->service.createProductionCalendar(tooLarge));
        verify(mapper,never()).insertProductionCalendar(any());
    }
    @Test void singleDayUpdateRejectsStaleRevision() {
        var current = row(start,"1000"); current.setRevision(3);
        when(mapper.selectProductionCalendarById(1L)).thenReturn(current);
        assertThrowsExactly(ConcurrentChangeException.class,()->service.updateProductionCalendar(1L,row(start,"2000")));
        verify(mapper,never()).updateProductionCalendar(any());
    }
    @Test void singleDayUpdateRejectsLostWriteAsConflict() {
        when(mapper.selectProductionCalendarById(1L)).thenReturn(row(start,"1000"));
        when(mapper.updateProductionCalendar(any())).thenReturn(0);
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.updateProductionCalendar(1L,row(start,"2000")));
        verifyNoInteractions(audit);
    }
    @Test void batchCommitRejectsLostUpdateAsConflict() {
        var current = row(start,"1000");
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(current));
        var preview = service.previewCalendarBatch(request(true,null,null));
        when(mapper.lockProductionCalendarRange("爱培科",start,end)).thenReturn(List.of(current));
        when(mapper.updateProductionCalendar(any())).thenReturn(0);
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.commitCalendarBatch(request(true,preview.fingerprint(),null)));
        verify(mapper,never()).insertProductionCalendar(any()); verifyNoInteractions(audit);
    }
    @Test void batchCommitRejectsConcurrentInsertAsConflict() {
        when(mapper.selectProductionCalendarRange("爱培科",start,end)).thenReturn(List.of());
        var preview = service.previewCalendarBatch(request(true,null,null));
        when(mapper.lockProductionCalendarRange("爱培科",start,end)).thenReturn(List.of());
        when(mapper.insertProductionCalendar(any())).thenThrow(new DuplicateKeyException("date already exists"));
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.commitCalendarBatch(request(true,preview.fingerprint(),null)));
        verifyNoInteractions(audit);
    }
    @Test void creatingDuplicateDayAndFailedInsertAreConflicts() {
        when(mapper.insertProductionCalendar(any())).thenThrow(new DuplicateKeyException("date already exists")).thenReturn(0);
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.createProductionCalendar(row(start,"1000")));
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.createProductionCalendar(row(start,"1000")));
        verifyNoInteractions(audit);
    }
    @Test void calendarImportRejectsConcurrentInsertAndLostUpdateAsConflict() {
        when(mapper.selectProductionCalendarByKey("爱培科",start)).thenReturn(null).thenReturn(row(start,"1000"));
        when(mapper.insertProductionCalendar(any())).thenThrow(new DuplicateKeyException("date already exists"));
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.importProductionCalendar(List.of(row(start,"1000")),"calendar.xlsx"));
        when(mapper.updateProductionCalendar(any())).thenReturn(0);
        assertThrowsExactly(ConcurrentChangeException.class, () -> service.importProductionCalendar(List.of(row(start,"1000")),"calendar.xlsx"));
        verifyNoInteractions(audit);
    }
    @Test void listUsesSqlPaginationAndStatusInsteadOfSlicingInMemory() {
        when(mapper.selectProductionCalendarPage("爱培科",start,LocalDate.of(2026,10,31),null,"UNCONFIGURED",20,20)).thenReturn(List.of());
        when(mapper.countProductionCalendarPage("爱培科",start,LocalDate.of(2026,10,31),null,"UNCONFIGURED")).thenReturn(26);
        var page = service.productionCalendarPage("爱培科",YearMonth.of(2026,10),2,20,null,"UNCONFIGURED");
        assertEquals(26,page.total()); assertEquals(2,page.totalPages());
        verify(mapper).selectProductionCalendarPage("爱培科",start,LocalDate.of(2026,10,31),null,"UNCONFIGURED",20,20);
    }
    ProductionCalendarEntry row(LocalDate date,String capacity) {
        var row = new ProductionCalendarEntry(); row.setId(1L); row.setFactoryCode("爱培科"); row.setCalendarDate(date);
        row.setProductionDay(true); row.setDailyCapacity(new BigDecimal(capacity)); row.setCapacityUnit("台"); row.setRevision(0); return row;
    }
    ConfigMasterService.CalendarBatchRequest request(boolean overwrite,String fingerprint,String copy) {
        return new ConfigMasterService.CalendarBatchRequest("爱培科",start,end,List.of(1,2,3,4,5,6,7),true,new BigDecimal("1000"),"台","正常生产",overwrite,copy,fingerprint);
    }
}
