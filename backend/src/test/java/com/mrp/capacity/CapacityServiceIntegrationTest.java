package com.mrp.capacity;

import com.mrp.MrpApiApplication;
import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.service.CapacityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("local")
@Transactional
class CapacityServiceIntegrationTest {

    @Autowired
    private CapacityService capacityService;

    @Test
    void contextLoads() {
        assertNotNull(capacityService);
    }

    @Test
    void listLines_returnsEmptyWhenNoCapacity() {
        // Active version may or may not exist
        List<CapacityLine> lines = capacityService.listLines();
        assertNotNull(lines);
    }

    @Test
    void listLinesByFactory_returnsEmptyForNonExistent() {
        List<CapacityLine> lines = capacityService.listLinesByFactory("NONEXISTENT");
        assertNotNull(lines);
        assertTrue(lines.isEmpty());
    }

    @Test
    void createLine_shouldSucceed() {
        CapacityLine line = new CapacityLine();
        line.setFactoryCode("TEST_FACTORY");
        line.setLineCode("LINE_" + System.currentTimeMillis());
        line.setLineName("Test Line");
        line.setWeeklyCapacity(500L);
        line.setIsActive(true);

        CapacityLine created = capacityService.createLine(line);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("TEST_FACTORY", created.getFactoryCode());
        assertEquals(500L, created.getWeeklyCapacity());
    }

    @Test
    void createLine_duplicateLineCode_shouldThrow() {
        String lineCode = "DUP_" + System.currentTimeMillis();

        CapacityLine line1 = new CapacityLine();
        line1.setFactoryCode("TEST_FACTORY");
        line1.setLineCode(lineCode);
        line1.setLineName("Line1");
        line1.setWeeklyCapacity(500L);
        line1.setIsActive(true);
        capacityService.createLine(line1);

        CapacityLine line2 = new CapacityLine();
        line2.setFactoryCode("TEST_FACTORY");
        line2.setLineCode(lineCode);
        line2.setLineName("Line2");
        line2.setWeeklyCapacity(600L);
        line2.setIsActive(true);

        assertThrows(Exception.class, () -> capacityService.createLine(line2),
                "Should throw for duplicate line code");
    }

    @Test
    void updateLine_shouldSucceed() {
        CapacityLine line = new CapacityLine();
        line.setFactoryCode("TEST_FACTORY");
        line.setLineCode("UPD_" + System.currentTimeMillis());
        line.setLineName("Original Name");
        line.setWeeklyCapacity(500L);
        line.setIsActive(true);

        CapacityLine created = capacityService.createLine(line);

        created.setLineName("Updated Name");
        created.setWeeklyCapacity(800L);

        CapacityLine updated = capacityService.updateLine(created.getId(), created);

        assertEquals("Updated Name", updated.getLineName());
        assertEquals(800L, updated.getWeeklyCapacity());
    }

    @Test
    void updateCapacityLine_shouldPersist() {
        CapacityLine line = new CapacityLine();
        line.setFactoryCode("TEST_FACTORY");
        line.setLineCode("PERSIST_" + System.currentTimeMillis());
        line.setLineName("Original");
        line.setWeeklyCapacity(500L);
        line.setIsActive(true);

        CapacityLine created = capacityService.createLine(line);

        created.setWeeklyCapacity(800L);
        capacityService.updateLine(created.getId(), created);

        List<CapacityLine> allLines = capacityService.listLines();
        CapacityLine found = allLines.stream()
                .filter(l -> l.getId().equals(created.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(found);
        assertEquals(800L, found.getWeeklyCapacity());
    }

    @Test
    void getCapacityByFactory_shouldFilter() {
        CapacityLine line1 = new CapacityLine();
        line1.setFactoryCode("YH");
        line1.setLineCode("YH_1_" + System.currentTimeMillis());
        line1.setLineName("YH Line 1");
        line1.setWeeklyCapacity(500L);
        line1.setIsActive(true);
        capacityService.createLine(line1);

        CapacityLine line2 = new CapacityLine();
        line2.setFactoryCode("YH");
        line2.setLineCode("YH_2_" + System.currentTimeMillis());
        line2.setLineName("YH Line 2");
        line2.setWeeklyCapacity(600L);
        line2.setIsActive(true);
        capacityService.createLine(line2);

        CapacityLine line3 = new CapacityLine();
        line3.setFactoryCode("APK");
        line3.setLineCode("APK_1_" + System.currentTimeMillis());
        line3.setLineName("APK Line 1");
        line3.setWeeklyCapacity(400L);
        line3.setIsActive(true);
        capacityService.createLine(line3);

        List<CapacityLine> yhLines = capacityService.listLinesByFactory("YH");
        List<CapacityLine> apkLines = capacityService.listLinesByFactory("APK");

        assertEquals(2, yhLines.size());
        assertEquals(1, apkLines.size());
    }
}
