package com.mrp.mrp.service;

import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.repository.DailyCapacityMapper;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigSnapshotServiceTest {
    private final ConfigMasterMapper master = mock(ConfigMasterMapper.class);
    private final ConfigSnapshotService snapshots = new ConfigSnapshotService(master,
            mock(Phase2ScopeConfigMapper.class), mock(DailyCapacityMapper.class));

    @Test void includesMaintainedLeadTimeMonthsAndPreservesForecastMonthOrder() {
        LocalDate october = LocalDate.of(2026, 10, 1), november = october.plusMonths(1), september = october.minusMonths(1);
        when(master.selectBusinessMonths()).thenReturn(List.of(september, october, november));
        assertEquals(List.of(october, november, september), snapshots.dailyPlanningBusinessMonths(List.of(october, november)));
    }

    @Test void unmaintainedLeadTimeMonthDoesNotIntroduceAdditionalCreationGate() {
        LocalDate october = LocalDate.of(2026, 10, 1);
        when(master.selectBusinessMonths()).thenReturn(List.of(october));
        assertEquals(List.of(october), snapshots.dailyPlanningBusinessMonths(List.of(october)));
    }
    @Test void rollingRequiresPreviousMonthEvenWhenNotMaintainedAndSortsChronologically() {
        LocalDate october = LocalDate.of(2026, 10, 1), november = october.plusMonths(1), september = october.minusMonths(1);
        assertEquals(List.of(september, october, november), snapshots.rollingBusinessMonths(List.of(november, october)));
    }

}
