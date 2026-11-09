package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.local.repository.BomLocalMapper;
import com.mrp.bom.repository.BomMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.session.ResultHandler;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BomServiceTest {
    @Test
    void treeMarksNonDefaultChildrenWithoutHidingThem() {
        BomDetail detail = new BomDetail();
        detail.setChildCode("5051AA800042");
        detail.setIsDefault("N");
        detail.setChildQty(1L);
        when(localMapper.selectDetailByParentCode("6810AA800157")).thenReturn(List.of(detail));
        var nodes = service.getTreeChildren("6810AA800157");
        assertTrue(nodes.size() == 1);
        assertTrue("N".equals(nodes.get(0).getIsDefault()));
    }


    @Mock
    private BomMapper bomMapper;
    @Mock
    private BomLocalMapper localMapper;

    @InjectMocks
    private BomService service;

    @Test
    void syncUsesShanghaiOrganizationAndNeverWritesWuxiRows() {
        BomDetail shanghai = detail("P-SH", "C-SH", BomService.PLANNING_ORGANIZATION);
        BomDetail wuxi = detail("P-WX", "C-WX", "七十迈科技（无锡）有限公司");
        when(bomMapper.countLatestBom(BomService.PLANNING_ORGANIZATION)).thenReturn(2);
        streamRows(List.of(shanghai, wuxi));
        when(localMapper.countAllDetails()).thenReturn(1);

        var result = service.syncFromOracle();

        assertTrue(Boolean.TRUE.equals(result.get("success")));
        verify(bomMapper).streamLatestBom(eq(BomService.PLANNING_ORGANIZATION), any());
        verify(localMapper).insertDetailBatch(argThat(rows -> rows.size() == 1
                && BomService.PLANNING_ORGANIZATION.equals(rows.get(0).getOrgName())));
        verify(localMapper, never()).insertDetailBatch(argThat(rows -> rows.stream()
                .anyMatch(row -> "七十迈科技（无锡）有限公司".equals(row.getOrgName()))));
        assertFalse(result.containsKey("error"));
    }

    @Test
    void emptyShanghaiResultDoesNotDeleteExistingLocalBom() {
        when(bomMapper.countLatestBom(BomService.PLANNING_ORGANIZATION)).thenReturn(0);

        assertThrows(IllegalStateException.class, service::syncFromOracle);
        verify(localMapper, never()).deleteAllDetails();
        verify(localMapper, never()).deleteAllParents();
        verify(localMapper, never()).insertDetailBatch(anyList());
    }

    @Test
    void defensiveFilterStopsUnexpectedNonShanghaiRows() {
        BomDetail wuxi = detail("P-WX", "C-WX", "七十迈科技（无锡）有限公司");
        when(bomMapper.countLatestBom(BomService.PLANNING_ORGANIZATION)).thenReturn(1);
        streamRows(List.of(wuxi));

        assertThrows(IllegalStateException.class, service::syncFromOracle);
        verify(localMapper, never()).deleteAllDetails();
        verify(localMapper, never()).deleteAllParents();
    }

    @Test
    void reportsRealFetchedRowsAfterEachPage() {
        BomDetail row = detail("P", "C", BomService.PLANNING_ORGANIZATION);
        when(bomMapper.countLatestBom(BomService.PLANNING_ORGANIZATION)).thenReturn(1001);
        streamRows(java.util.Collections.nCopies(1001, row));
        List<String> progress = new ArrayList<>();

        List<BomDetail> rows = service.fetchFromOracle((current, total) -> progress.add(current + "/" + total));

        assertTrue(rows.size() == 1001);
        assertTrue(progress.equals(List.of("0/1001", "1000/1001", "1001/1001")));
        verify(localMapper, never()).deleteAllDetails();
    }

    private void streamRows(List<BomDetail> rows) {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ResultHandler<BomDetail> handler = invocation.getArgument(1);
            DefaultResultContext<BomDetail> context = new DefaultResultContext<>();
            for (BomDetail row : rows) {
                context.nextResultObject(row);
                handler.handleResult(context);
            }
            return null;
        }).when(bomMapper).streamLatestBom(eq(BomService.PLANNING_ORGANIZATION), any());
    }

    private static BomDetail detail(String parent, String child, String organization) {
        BomDetail detail = new BomDetail();
        detail.setParentCode(parent);
        detail.setParentName(parent);
        detail.setChildCode(child);
        detail.setChildName(child);
        detail.setOrgName(organization);
        detail.setVersion("1.0");
        detail.setParentQty(1L);
        detail.setChildQty(1L);
        detail.setIsDefault("Y");
        return detail;
    }
}
