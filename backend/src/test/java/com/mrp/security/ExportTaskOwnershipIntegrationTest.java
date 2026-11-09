package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.importexport.service.ExportService;
import com.mrp.task.domain.ExportTask;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL, files and HTTP security. Never modifies material, classification or BOM data. */
@AutoConfigureMockMvc
class ExportTaskOwnershipIntegrationTest extends BaseIntegrationTest {
    @Autowired ExportService exports;
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    Long owner, other, forecast, ownPlan, foreignPlan, combined;
    List<Long> plans;
    final List<String> fields = List.of("整机料号", "合计", "2026-10-05");

    @BeforeEach void setup() {
        owner = user(); other = user();
        for (Long user : List.of(owner, other))
            jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')", user);
        forecast = insert("INSERT INTO forecast_version(version_no,file_name,file_checksum,status,created_by) VALUES(1,'export-test','" + UUID.randomUUID() + "','IMPORTED'," + owner + ")");
        ownPlan = plan("爱培科"); foreignPlan = plan("永惠"); combined = plan("ALL");
        plans = List.of(ownPlan, foreignPlan, combined);
        detail(ownPlan, "爱培科", "OWN", 17);
        detail(ownPlan, "永惠", "FOREIGN-CONTAMINATION", 900);
        detail(foreignPlan, "永惠", "OTHER", 31);
        detail(combined, "爱培科", "OWN", 17);
        detail(combined, "永惠", "OTHER", 31);
        login(owner);
    }

    @AfterEach void cleanup() throws Exception {
        TestSecurityContextHolder.clearContext();
        for (String path : jdbc.queryForList("SELECT file_path FROM export_task WHERE created_by IN (?,?) AND file_path IS NOT NULL", String.class, owner, other))
            Files.deleteIfExists(Path.of(path));
        jdbc.update("DELETE FROM export_task WHERE created_by IN (?,?) OR request_key LIKE 'export-test:%'", owner, other);
        for (Long plan : plans) { jdbc.update("DELETE FROM plan_detail WHERE plan_version_id=?", plan); jdbc.update("DELETE FROM plan_version WHERE id=?", plan); }
        jdbc.update("DELETE FROM forecast_version WHERE id=?", forecast);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id IN (?,?)", owner, other);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }

    @Test void ownerCanReadAndDownloadButAnotherExporterCannot() throws Exception {
        ExportTask task = create(ownPlan, null, fields, false, false, owner);
        mvc.perform(get("/api/v1/export-tasks/" + task.getId()).with(authentication(auth(owner, "schedule:export"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(task.getId()))
                .andExpect(jsonPath("$.data.filePath").doesNotExist());
        byte[] file = mvc.perform(get("/api/v1/export-tasks/" + task.getId() + "/download").with(authentication(auth(owner, "schedule:export"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(file))) {
            var sheet = book.getSheetAt(0);
            assertEquals(1, sheet.getLastRowNum());
            assertEquals("OWN", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(17, sheet.getRow(1).getCell(1).getNumericCellValue());
            assertEquals(17, sheet.getRow(1).getCell(2).getNumericCellValue());
        }
        for (String suffix : List.of("", "/download"))
            mvc.perform(get("/api/v1/export-tasks/" + task.getId() + suffix).with(authentication(auth(other, "schedule:export"))))
                    .andExpect(status().isForbidden());
    }

    @Test void downloadRejectsOutsideMissingAndSymlinkFilesWithoutLeakingPaths() throws Exception {
        ExportTask task = create(ownPlan, null, fields, false, false, owner);
        Path original = Path.of(task.getFilePath());
        Path link = original.resolveSibling("export-test-link-" + UUID.randomUUID() + ".xlsx");
        try {
            for (String path : List.of("/etc/hostname", original + ".expired")) {
                jdbc.update("UPDATE export_task SET file_path=? WHERE id=?", path, task.getId());
                mvc.perform(get("/api/v1/export-tasks/" + task.getId() + "/download")
                        .with(authentication(auth(owner, "schedule:export"))))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error.message").value("Export file not found"));
            }
            Files.createSymbolicLink(link, Path.of("/etc/hostname"));
            jdbc.update("UPDATE export_task SET file_path=? WHERE id=?", link.toString(), task.getId());
            mvc.perform(get("/api/v1/export-tasks/" + task.getId() + "/download")
                    .with(authentication(auth(owner, "schedule:export"))))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("UPDATE export_task SET file_path=? WHERE id=?", original.toString(), task.getId());
            Files.deleteIfExists(link);
        }
    }

    @Test void revokedScopeAndUnownedOrWrongTypeTasksAreDenied() throws Exception {
        ExportTask task = create(ownPlan, null, fields, false, false, owner);
        jdbc.update("UPDATE export_task SET request_key=? WHERE id=?", "export-test:" + UUID.randomUUID(), task.getId());
        assertEquals(task.getId(), exports.getExportTask(task.getId()).getId()); // Historical keys remain readable.
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", owner);
        forbidden(() -> exports.getExportTask(task.getId()));
        mvc.perform(get("/api/v1/export-tasks/" + task.getId() + "/download").with(authentication(auth(owner, "schedule:export"))))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')", owner);
        for (String update : List.of("created_by=NULL", "task_type='OTHER'", "business_scope=''")) {
            jdbc.update("UPDATE export_task SET " + update + " WHERE id=?", task.getId());
            forbidden(() -> exports.getExportTask(task.getId()));
            jdbc.update("UPDATE export_task SET created_by=?,task_type='PLAN_EXPORT',business_scope='爱培科' WHERE id=?", owner, task.getId());
        }
    }

    @Test void versionAndRequestedFactoryAreAuthorizedBeforeAnyTaskWrite() {
        long before = count();
        forbidden(() -> create(foreignPlan, null, fields, false, false, owner));
        forbidden(() -> create(ownPlan, "永惠", fields, false, false, owner));
        forbidden(() -> create(ownPlan, "ALL", fields, false, false, owner));
        forbidden(() -> create(combined, "爱培科", fields, false, false, owner));
        forbidden(() -> create(ownPlan, null, fields, false, false, other));
        SecurityContextHolder.clearContext();
        forbidden(() -> create(ownPlan, null, fields, false, false, owner));
        assertEquals(before, count());
    }

    @Test void deduplicationIncludesUserOptionsFieldBoundariesAndOrder() throws Exception {
        ExportTask base = create(ownPlan, null, fields, false, false, owner);
        assertEquals(base.getId(), create(ownPlan, "爱培科", fields, false, false, owner).getId());
        assertEquals(base.getId(), create(ownPlan, " ", fields, false, false, owner).getId());
        var ids = new HashSet<Long>(); ids.add(base.getId());
        for (boolean[] options : List.of(new boolean[]{true, false}, new boolean[]{false, true}, new boolean[]{true, true})) {
            ExportTask task = create(ownPlan, null, fields, options[0], options[1], owner);
            assertTrue(ids.add(task.getId()));
            try (var book = new XSSFWorkbook(Files.newInputStream(Path.of(task.getFilePath())))) {
                assertEquals(fields.size() + (options[0] ? 1 : 0) + (options[1] ? 1 : 0), book.getSheetAt(0).getRow(0).getLastCellNum());
            }
        }
        for (List<String> selected : List.of(List.of("a,b"), List.of("a", "b"), List.of("b", "a"), List.of("很长字段".repeat(100))))
            assertTrue(ids.add(create(ownPlan, null, selected, false, false, owner).getId()));
        login(other);
        ExportTask second = create(ownPlan, null, fields, false, false, other);
        assertTrue(ids.add(second.getId())); assertNotEquals(base.getFilePath(), second.getFilePath());
        assertEquals(other, second.getCreatedBy());
        assertTrue(second.getRequestKey().length() <= 128);
    }

    @Test void combinedTaskRequiresBothFactoriesAtCreateAndRead() {
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')", owner);
        ExportTask all = create(combined, null, fields, false, false, owner);
        ExportTask subset = create(combined, "爱培科", fields, false, false, owner);
        assertEquals("ALL", all.getBusinessScope()); assertEquals("爱培科", subset.getBusinessScope());
        assertNotEquals(all.getId(), subset.getId());
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=? AND factory_code='永惠'", owner);
        forbidden(() -> exports.getExportTask(all.getId()));
        assertEquals(subset.getId(), exports.getExportTask(subset.getId()).getId());
    }

    @Test void createUsesExportPermissionAndMissingTaskKeepsCompatibility() throws Exception {
        String url = "/api/v1/plans/" + ownPlan + "/exports";
        String body = "{\"fields\":[\"整机料号\",\"合计\"]}";
        TestSecurityContextHolder.clearContext();
        mvc.perform(post(url).with(anonymous()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for (String permission : List.of("schedule:edit", "schedule:view"))
            mvc.perform(post(url).with(authentication(auth(owner, permission))).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        mvc.perform(post(url).with(authentication(auth(owner, "schedule:export"))).contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/export-tasks/9223372036854775807").with(authentication(auth(owner, "schedule:export"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).with(authentication(auth(owner, "schedule:export"))).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).with(authentication(auth(owner, "schedule:export"))).contentType("application/json").content("{\"fields\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    private ExportTask create(Long plan, String factory, List<String> selected, boolean priority, boolean actual, Long user) {
        return exports.createExportTask(plan, factory, selected, priority, actual, user);
    }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM export_task", Long.class); }
    private Long user() { return insert("INSERT INTO user(username,password_hash) VALUES('export-" + UUID.randomUUID() + "','test-only')"); }
    private Long plan(String factory) { return insert("INSERT INTO plan_version(version_no,factory_code,forecast_version_id,rule_version,current_week_start,status) VALUES(1,'" + factory + "'," + forecast + ",'v1','2026-10-05','PUBLISHED')"); }
    private void detail(Long plan, String factory, String material, long quantity) {
        jdbc.update("INSERT INTO plan_detail(plan_version_id,factory_code,material_id,week_start_date,physical_month,source_month,slot,system_quantity,effective_quantity) VALUES(?,?,?,'2026-10-05','2026-10-01','2026-10-01',1,?,?)", plan, factory, material, quantity, quantity);
    }
    private Long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private UsernamePasswordAuthenticationToken auth(Long user, String permission) {
        return new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(user, "export-test"), null, List.of(new SimpleGrantedAuthority(permission)));
    }
    private void login(Long user) { SecurityContextHolder.getContext().setAuthentication(auth(user, "schedule:export")); }
    private void forbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(403, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}
