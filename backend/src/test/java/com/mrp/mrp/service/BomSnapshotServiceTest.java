package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BomSnapshotServiceTest {

    @Mock
    private BomSnapshotMapper snapshotMapper;
    @Mock
    private BomSnapshotDetailMapper detailMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BomSnapshotService bomSnapshotService;

    private BomSnapshot testSnapshot;
    private BomSnapshotDetail testDetail;

    @BeforeEach
    void setUp() {
        testSnapshot = new BomSnapshot();
        testSnapshot.setId(1L);
        testSnapshot.setSourceVersion("bom_parent+bom_detail");
        testSnapshot.setStatus("READY");
        testSnapshot.setChecksum("test-checksum");
        testSnapshot.setEffectiveAt(LocalDate.now());

        testDetail = new BomSnapshotDetail();
        testDetail.setId(1L);
        testDetail.setSnapshotId(1L);
        testDetail.setParentCode("P001");
        testDetail.setChildCode("C001");
        testDetail.setParentQty(BigDecimal.ONE);
        testDetail.setChildQty(BigDecimal.valueOf(2));
        testDetail.setBomVersion("1.0");
        testDetail.setEffectiveDate(LocalDate.now());
        testDetail.setOrganizationCode("ORG001");
        testDetail.setBodyScope("FULL");
    }

    @Test
    void testGetSnapshot() {
        // Given
        when(snapshotMapper.selectById(1L)).thenReturn(testSnapshot);

        // When
        BomSnapshot result = bomSnapshotService.getSnapshot(1L);

        // Then
        assertNotNull(result);
        assertEquals("READY", result.getStatus());
    }

    @Test
    void testGetAllSnapshots() {
        // Given
        when(snapshotMapper.selectAll()).thenReturn(List.of(testSnapshot));

        // When
        List<BomSnapshot> result = bomSnapshotService.getAllSnapshots();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetCurrentSnapshot() {
        when(snapshotMapper.selectLatestReady()).thenReturn(testSnapshot);

        BomSnapshot result = bomSnapshotService.getCurrentSnapshot();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("READY", result.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentBomIncludesNonDefaultEdgeAndRejectsChangesBeforeCalculation() {
        var edge = new BomSnapshotDetail();
        edge.setParentCode("6810AA800157"); edge.setChildCode("5051AA800042");
        edge.setParentQty(BigDecimal.ONE); edge.setChildQty(BigDecimal.ONE);
        edge.setBomVersion("1.1"); edge.setOrganizationCode("上海七十迈数字科技有限公司");
        // The source query must not filter NC's HBDEFAULT=N attribute.
        when(jdbcTemplate.query(argThat(sql -> sql.contains("FROM bom_detail d") && !sql.contains("is_default")),
                any(Object[].class), any(RowMapper.class))).thenReturn(List.of(edge), List.of());
        var current = bomSnapshotService.loadCurrentBom();
        assertEquals("5051AA800042", current.edges().getFirst().getChildCode());
        var batch = new MrpCalcBatch(); batch.setBomSourceChecksum(current.checksum());
        assertThrows(IllegalStateException.class, () -> bomSnapshotService.edgesForBatch(batch));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyCurrentBomReportsZeroForPreparationButCannotCreateBatch() {
        when(jdbcTemplate.query(contains("FROM bom_detail d"), any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of());
        assertEquals(0, bomSnapshotService.countCurrentBomEdges());
        assertThrows(IllegalStateException.class, () -> bomSnapshotService.loadCurrentBom());
    }

    @Test
    void historicalBatchStillReadsHistoricalEdges() {
        var batch = new MrpCalcBatch(); batch.setBomSnapshotId(35L);
        when(detailMapper.selectBySnapshotId(35L)).thenReturn(List.of(testDetail));
        assertEquals(List.of(testDetail), bomSnapshotService.edgesForBatch(batch));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newSnapshotReadsOnlyShanghaiOrganization() {
        LocalDate effectiveDate = LocalDate.of(2026, 10, 16);
        when(jdbcTemplate.query(contains("WHERE d.org_name = ?"), any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of());

        BomSnapshot result = bomSnapshotService.createSnapshot(effectiveDate);

        assertEquals("EMPTY", result.getStatus());
        verify(jdbcTemplate).query(contains("WHERE d.org_name = ?"),
                aryEq(new Object[]{"上海七十迈数字科技有限公司", effectiveDate}), any(RowMapper.class));
        verify(detailMapper, never()).insertBatch(anyList());
    }

    @Test
    void mixedHistoricalSnapshotCannotBeUsedForNewPlanning() {
        when(jdbcTemplate.queryForObject(contains("organization_code <> ?"), eq(Integer.class),
                eq(33L), eq("上海七十迈数字科技有限公司"))).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> bomSnapshotService.validatePlanningOrganization(33L));
        verifyNoInteractions(detailMapper, snapshotMapper);
    }

    @Test
    void testGetSnapshotDetails() {
        // Given
        when(detailMapper.selectBySnapshotId(1L)).thenReturn(List.of(testDetail));

        // When
        List<BomSnapshotDetail> result = bomSnapshotService.getSnapshotDetails(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("P001", result.get(0).getParentCode());
    }

    @Test
    void testGetChildren() {
        // Given
        when(detailMapper.selectByParentCode(1L, "P001")).thenReturn(List.of(testDetail));

        // When
        List<BomSnapshotDetail> result = bomSnapshotService.getChildren(1L, "P001");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("C001", result.get(0).getChildCode());
    }

    @Test
    void testCompareVersions() {
        // Test numeric comparison
        assertEquals(0, BomSnapshotService.compareVersions("1.0", "1.0"));
        assertEquals(-1, BomSnapshotService.compareVersions("1.0", "2.0"));
        assertEquals(1, BomSnapshotService.compareVersions("2.0", "1.0"));

        // Test null handling
        assertEquals(-1, BomSnapshotService.compareVersions(null, "1.0"));
        assertEquals(1, BomSnapshotService.compareVersions("1.0", null));
        assertEquals(0, BomSnapshotService.compareVersions(null, null));

        // Test lexicographic fallback
        assertEquals(-1, BomSnapshotService.compareVersions("a", "b"));
        assertEquals(1, BomSnapshotService.compareVersions("b", "a"));
    }

    @Test
    void summarizeErrors_shouldBoundPayloadAndKeepTotalCount() {
        List<String> errors = new ArrayList<>();
        for (int i = 1; i <= 2000; i++) {
            errors.add("BOM_ORG_AMBIGUOUS: parent=P" + i + ", child=C" + i + " 库存组织为空");
        }

        String summary = BomSnapshotService.summarizeErrors(errors);

        assertTrue(summary.startsWith("共发现 2000 个BOM问题"));
        assertTrue(summary.contains("前 50 条"));
        assertTrue(summary.contains("其余 1950 条"));
        assertFalse(summary.contains("parent=P51,"));
        assertTrue(summary.length() < 10_000, "API and logs should receive a bounded error summary");
    }

    @Test
    void testComputeChecksum() {
        // Given
        List<BomSnapshotDetail> details = List.of(testDetail);

        // When
        String checksum = bomSnapshotService.computeChecksum(details);

        // Then
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
        // Checksum should be deterministic
        assertEquals(checksum, bomSnapshotService.computeChecksum(details));
    }

    @Test
    void testComputeChecksumWithMultipleDetails() {
        // Given
        BomSnapshotDetail detail2 = new BomSnapshotDetail();
        detail2.setParentCode("P002");
        detail2.setChildCode("C002");
        detail2.setParentQty(BigDecimal.ONE);
        detail2.setChildQty(BigDecimal.valueOf(3));
        detail2.setBomVersion("1.0");
        detail2.setOrganizationCode("ORG001");
        
        List<BomSnapshotDetail> details = List.of(testDetail, detail2);

        // When
        String checksum = bomSnapshotService.computeChecksum(details);

        // Then
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
    }

    @Test
    void testComputeChecksumWithEmptyDetails() {
        // Given
        List<BomSnapshotDetail> details = Collections.emptyList();

        // When
        String checksum = bomSnapshotService.computeChecksum(details);

        // Then
        assertNotNull(checksum);
    }
}
