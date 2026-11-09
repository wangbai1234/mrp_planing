package com.mrp.mrp;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.mrp.repository.MrpTerminalDemandMapper;
import com.mrp.mrp.repository.MrpWorkbenchReviewMapper;
import com.mrp.mrp.service.MrpWorkbenchReviewService;
import com.mrp.mrp.service.MrpCalculationService;
import com.mrp.mrp.service.MrpShortageExportService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** A material is one list row even if several source months and warehouses contribute. */
@Transactional
class ShortageSummaryIntegrationTest extends BaseIntegrationTest {
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired MrpTerminalDemandMapper demands;
    @Autowired MrpDecisionIssueMapper issues;
    @Autowired MrpWorkbenchReviewMapper reviewRows;
    @Autowired MrpWorkbenchReviewService reviews;
    @Autowired MrpCalculationService calculation;
    @Autowired MrpShortageExportService shortageExport;

    @Test void materialSummaryKeepsQuantitySeparateFromWarehousePoolsAndSourceFilters() {
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('永惠','MATERIAL_REVIEW',1,'v5-shifted-business-weeks')");
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES"
                +"(?,?,'永惠','A','PART','RAW_MATERIAL','PURCHASED','2026-10-01',4.000001),"
                +"(?,?,'永惠','B','PART','RAW_MATERIAL','PURCHASED','2026-11-01',6.000002)",run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,warehouse_code,inventory_org,material_code,available_qty,allocated_qty) VALUES"
                +"(?,1,'永惠','W1','ORG','PART',10,2),(?,1,'永惠','W2','ORG','PART',20,3)",batch,batch);
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,factory_code,risk_type,warehouse_code,material_code) VALUES(?,?,'永惠','WARNING_STOCK_BREACH','W2','PART'),(?,?,'永惠','STOCK_POLICY_MISSING',NULL,'PART'),(?,?,'爱培科','STOCK_POLICY_MISSING',NULL,'PART')",batch,run,batch,run,batch,run);
        jdbc.update("INSERT INTO mrp_decision_issue(batch_id,issue_key,issue_type,scope_type,scope_key,status) VALUES(?,'PART-ISSUE','INVENTORY_MISSING','MATERIAL','PART','OPEN')",batch);

        assertEquals(1,demands.countShortageSummary(run,null,null,null,null));
        MrpTerminalDemand all=demands.selectShortageSummary(run,batch,batch,batch,null,null,null,null,0,20).getFirst();
        assertEquals(0,new BigDecimal("10.000003").compareTo(all.getQuantity()));
        assertEquals(2,all.getInventoryPoolCount());
        assertEquals(0,new BigDecimal("30").compareTo(all.getAvailableQty()));
        assertEquals(0,new BigDecimal("5").compareTo(all.getAllocatedQty()));
        assertEquals(2,all.getWarehouseRiskCount());
        assertEquals(1,all.getPendingIssueCount());
        assertEquals(2,all.getAffectedMachineCount());
        assertEquals(2,demands.countShortagePools(batch,"永惠","PART"));
        assertEquals("W2",demands.selectShortagePools(batch,"永惠","PART",1,1).getFirst().getWarehouseCode());

        MrpTerminalDemand october=demands.selectShortageSummary(run,batch,batch,batch,null,null,
                LocalDate.of(2026,10,1),null,0,20).getFirst();
        assertEquals(0,new BigDecimal("4.000001").compareTo(october.getQuantity()));
        assertEquals(2,october.getInventoryPoolCount());
        assertEquals(0,new BigDecimal("30").compareTo(october.getAvailableQty()));
    }

    @Test void historicalBatchDefaultsToMaterialSummaryAndKeepsSourceDetails() throws Exception {
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('永惠','PUBLISHED',1,'v4-rolling-business-weeks')");
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'PUBLISHED')",batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES"
                +"(?,?,'永惠','A','PART','RAW_MATERIAL','PURCHASED','2026-10-01',4),"
                +"(?,?,'永惠','B','PART','RAW_MATERIAL','PURCHASED','2026-11-01',6)",run,batch,run,batch);

        var summary=calculation.getShortagePage(batch,null,null,null,null,null,false,1,20);
        assertEquals(1,summary.total());
        assertEquals(0,new BigDecimal("10").compareTo(summary.items().getFirst().getQuantity()));
        var details=calculation.getShortagePage(batch,null,null,null,null,null,true,1,20);
        assertEquals(2,details.total());
        assertEquals(Set.of("A","B"),details.items().stream().map(MrpTerminalDemand::getRootMaterialCode).collect(java.util.stream.Collectors.toSet()));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(shortageExport.export(batch,null,null,null,null)))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(2,sheet.getPhysicalNumberOfRows());
            assertEquals("末级净需求",sheet.getRow(0).getCell(4).getStringCellValue());
            assertEquals(10,sheet.getRow(1).getCell(4).getNumericCellValue());
            assertEquals("历史未评估",sheet.getRow(1).getCell(5).getStringCellValue());
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(shortageExport.export(batch,null,LocalDate.of(2026,10,1),null,null)))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("筛选范围净需求",sheet.getRow(0).getCell(4).getStringCellValue());
            assertEquals(4,sheet.getRow(1).getCell(4).getNumericCellValue());
        }
    }

    @Test void exportKeepsKnownRiskWithoutPoolAndPreservesLargeDecimal() throws Exception {
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('永惠','MATERIAL_REVIEW',1,'v5-shifted-business-weeks')");
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,'永惠','ROOT','PART','RAW_MATERIAL','PURCHASED','2026-10-01',123456789012.123456)",run,batch);
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,factory_code,risk_type,warehouse_code,material_code) VALUES(?,?,'永惠','STOCK_POLICY_MISSING',NULL,'PART')",batch,run);
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(shortageExport.export(batch,null,null,null,null)))) {
            var row=workbook.getSheetAt(0).getRow(1);
            assertEquals(CellType.STRING,row.getCell(4).getCellType());
            assertEquals("123456789012.123456",row.getCell(4).getStringCellValue());
            assertEquals("无冻结库存池",row.getCell(5).getStringCellValue());
            assertEquals("1 项库存风险",row.getCell(6).getStringCellValue());
            assertEquals("待处理",row.getCell(8).getStringCellValue());
        }
    }

    @Test void summaryDoesNotAttachAnotherFactoryIssuesOrAmbiguousLegacyRisks() {
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('ALL','MATERIAL_REVIEW',1,'v5-shifted-business-weeks')");
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES"
                +"(?,?,'永惠','ROOT','PART','RAW_MATERIAL','PURCHASED','2026-10-01',4),"
                +"(?,?,'爱培科','ROOT','PART','RAW_MATERIAL','PURCHASED','2026-10-01',6),"
                +"(?,?,'永惠','ROOT','SOLO','RAW_MATERIAL','PURCHASED','2026-10-01',1)",run,batch,run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_decision_issue(batch_id,factory_code,issue_key,issue_type,scope_type,scope_key,status) VALUES"
                +"(?,'永惠','YH-PART','INVENTORY_MISSING','MATERIAL','PART','OPEN'),"
                +"(?,'爱培科','AP-PART','INVENTORY_MISSING','MATERIAL','PART','OPEN'),"
                +"(?,NULL,'OLD-PART','INVENTORY_MISSING','MATERIAL','PART','OPEN'),"
                +"(?,NULL,'OLD-SOLO','INVENTORY_MISSING','MATERIAL','SOLO','OPEN')",batch,batch,batch,batch);
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,factory_code,risk_type,material_code) VALUES"
                +"(?,?,NULL,'STOCK_POLICY_MISSING','PART'),"
                +"(?,?,NULL,'STOCK_POLICY_MISSING','SOLO')",batch,run,batch,run);
        var rows=demands.selectShortageSummary(run,batch,batch,batch,null,null,null,null,0,20);
        var yhPart=rows.stream().filter(d->"永惠".equals(d.getFactoryCode())&&"PART".equals(d.getTerminalMaterialCode())).findFirst().orElseThrow();
        var apPart=rows.stream().filter(d->"爱培科".equals(d.getFactoryCode())&&"PART".equals(d.getTerminalMaterialCode())).findFirst().orElseThrow();
        var solo=rows.stream().filter(d->"SOLO".equals(d.getTerminalMaterialCode())).findFirst().orElseThrow();
        assertEquals(1,yhPart.getPendingIssueCount());
        assertEquals(1,apPart.getPendingIssueCount());
        assertEquals(0,yhPart.getWarehouseRiskCount());
        assertEquals(0,apPart.getWarehouseRiskCount());
        assertEquals(1,solo.getPendingIssueCount());
        assertEquals(1,solo.getWarehouseRiskCount());
    }

    @Test void generatedIssuesForSameMaterialInTwoFactoriesPersistIndependently() {
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('ALL','WAITING_DECISION',1,'v5-shifted-business-weeks')");
        var roots=List.of(new NetRequirementEngine.RootDemand("ROOT",BigDecimal.ONE,LocalDate.of(2026,10,1)));
        for (String factory : List.of("永惠","爱培科")) {
            var generated=new NetRequirementEngine(List.of(),Map.of(),Map.of(),Map.of(),List.of(),
                    Set.of(),factory,null,null,batch,Set.of("ROOT"),true).explodeRoots(roots).issues;
            generated.forEach(issue->issue.setBatchId(batch));
            issues.insertBatch(generated);
        }
        var persisted=issues.selectByBatchId(batch);
        assertEquals(2,persisted.size());
        assertEquals(2,persisted.stream().map(issue->issue.getFactoryCode()).distinct().count());
        assertEquals(2,persisted.stream().map(issue->issue.getIssueKey()).distinct().count());
    }

    @Test void publishedReviewSnapshotPreservesOperatorsFingerprintsAndHistoricalUnknown() {
        long user=insert("INSERT INTO user(username,password_hash) VALUES('shortage-summary-review','unused')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')",user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(user,"shortage-summary-review"),null,java.util.List.of()));
        try {
            long source=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('永惠','SUPERSEDED',1,'v5-shifted-business-weeks')");
            long sourceRun=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",source);
            jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",sourceRun,source);
            long published=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,revision_of_batch_id) VALUES('永惠','PUBLISHED',2,'v5-shifted-business-weeks',?)",source);
            long publishedRun=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'PUBLISHED')",published);
            jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",publishedRun,published);
            jdbc.update("UPDATE mrp_calc_batch SET superseded_by_batch_id=? WHERE id=?",published,source);
            assertEquals("HISTORICAL_UNVERIFIED",reviews.status(published).schedule());
            reviewRows.insert(source,"SCHEDULE",sourceRun,"schedule-fingerprint",null,user);
            reviewRows.insert(source,"MATERIAL",sourceRun,"schedule-fingerprint","material-fingerprint",user);
            assertEquals(2,reviewRows.copyPublished(source,published,publishedRun));
            var status=reviews.status(published);
            assertEquals("CONFIRMED",status.schedule());
            assertEquals("CONFIRMED",status.material());
            assertEquals("schedule-fingerprint",status.scheduleFingerprint());
            assertEquals("material-fingerprint",status.materialFingerprint());
            assertEquals(user,reviewRows.latest(published,"SCHEDULE").operatorId());
            assertEquals(reviewRows.latest(source,"SCHEDULE").createdAt(),reviewRows.latest(published,"SCHEDULE").createdAt());
        } finally {SecurityContextHolder.clearContext();}
    }

    private long insert(String sql,Object... values) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
