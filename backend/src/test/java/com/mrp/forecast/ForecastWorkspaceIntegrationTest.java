package com.mrp.forecast;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.service.ForecastWorkspaceService;
import com.mrp.forecast.service.ForecastWorkspaceService.LineInput;
import com.mrp.forecast.service.ForecastWorkspaceService.Mutation;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.common.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

class ForecastWorkspaceIntegrationTest extends BaseIntegrationTest {
    @Autowired ForecastWorkspaceService service;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;

    @Test @Transactional
    void forecastMaterialSearchOnlyReturnsActiveMatchingMaterials() {
        jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('FORECAST-SEARCH-ON','查询可用料号',1,0),('FORECAST-SEARCH-OFF','查询停用料号',0,0)");
        var result = service.searchMaterials("FORECAST-SEARCH", 1, 10);
        assertEquals(1, result.total());
        assertEquals("FORECAST-SEARCH-ON", result.items().getFirst().get("materialCode"));
        assertEquals(0, service.searchMaterials("NO-SUCH-FORECAST-MATERIAL", 1, 10).total());
    }

    @Test @Transactional
    void existingVersionMonthsCannotBeAddedOrRemovedThroughApi() {
        Long admin=jdbc.queryForObject("SELECT id FROM user WHERE username='admin'",Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(admin,"admin"),null,List.of()));
        try {
            jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('FORECAST-LOCK-1','原有料号',1,0),('FORECAST-LOCK-2','新增料号',1,0)");
            jdbc.update("INSERT INTO forecast_version(version_no,status,created_by) VALUES(1,'IMPORTED',?)",admin);
            Long versionId=jdbc.queryForObject("SELECT MAX(id) FROM forecast_version",Long.class);
            jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,material_name,plan_month,forecast_qty,priority_seq,source_row_no) VALUES(?,'永惠','FORECAST-LOCK-1','原有料号','2026-11-01',100,1,1),(?,'永惠','FORECAST-LOCK-1','原有料号','2026-12-01',200,1,1)",versionId,versionId);
            jdbc.update("UPDATE forecast_version_head SET current_version_id=? WHERE id=1",versionId);
            Long draftId=((Number)service.createDraft().get("id")).longValue();
            Long lineId=((Number)service.lineByKey(draftId,"永惠","FORECAST-LOCK-1").get("id")).longValue();
            assertThrows(ValidationException.class,()->service.edit(draftId,lineId,new LineInput("永惠","FORECAST-LOCK-1",null,null,null,
                    Map.of("2026-11",new BigDecimal("110")),null,0L,"非法移除月份")));
            assertThrows(ValidationException.class,()->service.edit(draftId,lineId,new LineInput("永惠","FORECAST-LOCK-1",null,null,null,
                    Map.of("2026-11",new BigDecimal("110"),"2026-12",new BigDecimal("200"),"2027-01",BigDecimal.ZERO),null,0L,"非法新增月份")));
            assertThrows(ValidationException.class,()->service.add(draftId,new LineInput("永惠","FORECAST-LOCK-2",null,null,null,
                    Map.of("2026-11",BigDecimal.ONE),null,0L,"新增缺月份")));
            assertThrows(ValidationException.class,()->service.add(draftId,new LineInput("永惠","FORECAST-LOCK-2",null,null,null,
                    Map.of("2026-11",BigDecimal.ONE,"2026-12",BigDecimal.ZERO,"2027-01",BigDecimal.ZERO),null,0L,"新增多月份")));
            assertEquals(0L,((Number)service.activeDraft().get("revision")).longValue());
            var edited=service.edit(draftId,lineId,new LineInput("永惠","FORECAST-LOCK-1",null,null,null,
                    Map.of("2026-11",new BigDecimal("110"),"2026-12",new BigDecimal("200")),null,0L,"合法数量修改"));
            assertEquals(1L,((Number)edited.get("revision")).longValue());
            var added=service.add(draftId,new LineInput("永惠","FORECAST-LOCK-2",null,null,null,
                    Map.of("2026-11",BigDecimal.ONE,"2026-12",BigDecimal.ZERO),null,1L,"合法新增"));
            assertEquals(2L,((Number)added.get("revision")).longValue());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test @Transactional
    void draftEditPublishAndDiffPreserveBaseVersion() {
        Long admin=jdbc.queryForObject("SELECT id FROM user WHERE username='admin'",Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(admin,"admin"),null,List.of()));
        try {
            jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('FORECAST-WS-1','测试整机',1,0)");
            jdbc.update("INSERT INTO forecast_version(version_no,status,created_by) VALUES(1,'IMPORTED',?)",admin);
            Long old=jdbc.queryForObject("SELECT MAX(id) FROM forecast_version",Long.class);
            jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,material_name,plan_month,forecast_qty,priority_seq,source_row_no) VALUES(?,'永惠','FORECAST-WS-1','测试整机','2026-11-01',100,1,1)",old);
            jdbc.update("UPDATE forecast_version_head SET current_version_id=? WHERE id=1",old);
            jdbc.update("INSERT INTO mrp_calc_batch(factory_code,status,forecast_version_id,version_no,active_request_key) VALUES('永惠','MATERIAL_REVIEW',?,1,'forecast-workspace-stale-test')",old);
            var draft=service.createDraft();
            Long draftId=((Number)draft.get("id")).longValue();
            var lines=service.lines(draftId,null,1,20);
            assertEquals(1,lines.total());
            Long lineId=((Number)lines.items().getFirst().get("id")).longValue();
            assertEquals(lineId, ((Number)service.lineByKey(draftId,"永惠","FORECAST-WS-1").get("id")).longValue());
            assertEquals(0, new BigDecimal("100").compareTo(new BigDecimal(String.valueOf(service.lineByKey(draftId,"永惠","FORECAST-WS-1").get("total")))));
            assertThrows(ValidationException.class,()->service.publish(draftId,new Mutation(0L,"未修改直接发布")));
            var changed=service.edit(draftId,lineId,new LineInput("永惠","FORECAST-WS-1",null,null,null,
                    Map.of("2026-11",new BigDecimal("120.000001")),null,0L,"业务调整"));
            assertEquals(1L,((Number)changed.get("revision")).longValue());
            var preview=service.compareDraft(draftId,null,null,true,1,20);
            assertEquals(1,preview.total());
            assertEquals("QUANTITY",preview.items().getFirst().get("changeType"));
            assertEquals(0,new BigDecimal("20.000001").compareTo((BigDecimal)preview.items().getFirst().get("delta")));
            var summary=service.draftSummary(draftId);
            assertEquals(1,summary.get("activeLines"));
            assertEquals("QUANTITY",((List<?>)summary.get("changes")).stream().map(Map.class::cast).findFirst().orElseThrow().get("changeType"));
            var published=service.publish(draftId,new Mutation(1L,"确认需求"));
            Long newest=((Number)published.get("id")).longValue();
            assertEquals("STALE", jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE active_request_key='forecast-workspace-stale-test'", String.class));
            assertEquals(newest,((Number)service.current().get("id")).longValue());
            assertEquals(0,new BigDecimal("100").compareTo(jdbc.queryForObject("SELECT forecast_qty FROM forecast_detail WHERE version_id=?",BigDecimal.class,old)));
            assertEquals(0,new BigDecimal("120.000001").compareTo(jdbc.queryForObject("SELECT forecast_qty FROM forecast_detail WHERE version_id=?",BigDecimal.class,newest)));
            var difference=service.compare(old,newest,null,null,true,1,20);
            assertEquals(1,difference.total());
            assertEquals("QUANTITY",difference.items().getFirst().get("changeType"));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test @Transactional
    void addDeletePreviewAndRevisionConflict() {
        Long admin=jdbc.queryForObject("SELECT id FROM user WHERE username='admin'",Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(admin,"admin"),null,List.of()));
        try {
            jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('FORECAST-WS-2','整机二',1,0)");
            Long draftId=((Number)service.createDraft().get("id")).longValue();
            assertEquals(0,service.draftSummary(draftId).get("activeLines"));
            assertThrows(ValidationException.class,()->service.publish(draftId,new Mutation(0L,"空草稿")));
            var input=new LineInput("永惠","FORECAST-WS-2",null,null,null,Map.of("2026-12",new BigDecimal("5.5")),null,0L,"新增需求");
            Long lineId=((Number)service.add(draftId,input).get("id")).longValue();
            assertEquals("ADDED",service.compareDraft(draftId,null,null,true,1,20).items().getFirst().get("changeType"));
            assertThrows(ConcurrentChangeException.class,()->service.add(draftId,input));
            assertEquals(1L,((Number)service.draftSummary(draftId).get("revision")).longValue());
            service.delete(draftId,lineId,new Mutation(1L,"取消需求"));
            assertEquals(lineId, ((Number)service.deletedLineByKey(draftId,"永惠","FORECAST-WS-2").get("id")).longValue());
            assertEquals(0,service.compareDraft(draftId,null,null,true,1,20).total());
            assertEquals(0,service.draftSummary(draftId).get("activeLines"));
            service.add(draftId,new LineInput("永惠","FORECAST-WS-2",null,null,null,
                    Map.of("2026-12",new BigDecimal("5.5")),null,2L,"恢复需求"));
            assertEquals(1,service.lines(draftId,null,1,20).total());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test @Transactional
    void copiedDraftDoesNotReportUnchangedMonthPriorityAsPendingChange() {
        Long admin=jdbc.queryForObject("SELECT id FROM user WHERE username='admin'",Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(admin,"admin"),null,List.of()));
        try {
            jdbc.update("INSERT INTO material(material_code,material_name,is_active,is_deleted) VALUES('FORECAST-WS-PRIORITY','测试整机',1,0)");
            jdbc.update("INSERT INTO forecast_version(version_no,status,created_by) VALUES(1,'IMPORTED',?)",admin);
            Long old=jdbc.queryForObject("SELECT MAX(id) FROM forecast_version",Long.class);
            jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,material_name,plan_month,forecast_qty,priority_seq,source_row_no) VALUES(?,'永惠','FORECAST-WS-PRIORITY','测试整机','2026-11-01',100,1,1)",old);
            jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,material_name,plan_month,forecast_qty,priority_seq,source_row_no) VALUES(?,'永惠','FORECAST-WS-PRIORITY','测试整机','2026-12-01',200,2,2)",old);
            jdbc.update("UPDATE forecast_version_head SET current_version_id=? WHERE id=1",old);
            Long draftId=((Number)service.createDraft().get("id")).longValue();
            assertEquals(0,service.compareDraft(draftId,null,null,true,1,20).total());
            assertEquals(0,service.draftSummary(draftId).get("changedLines"));
            assertThrows(ValidationException.class,()->service.publish(draftId,new Mutation(0L,"无实际变化")));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
