package com.mrp.task;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.inventory.service.InventoryImportService;
import com.mrp.task.domain.ImportTask;
import com.mrp.task.repository.ImportTaskMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL uniqueness plus a connection-local LAST_INSERT_ID fault injection. */
class ImportUploadIdentityIntegrationTest extends BaseIntegrationTest {
    enum Flow { FORECAST, ITEMS, LEGACY }
    @Autowired ForecastImportService forecast;
    @Autowired InventoryImportService inventory;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired @Qualifier("primarySqlSessionFactory") SqlSessionFactory sessions;
    @SpyBean ImportTaskMapper tasks;
    Long owner, other;
    byte[] file;

    @BeforeEach void setup() throws Exception {
        owner = user(); other = user();
        login();
        // Parsing errors remain previews; these tests must never create formal input versions.
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("upload");
            sheet.createRow(0).createCell(0).setCellValue(UUID.randomUUID().toString());
            sheet.createRow(1).createCell(0).setCellValue("test-only");
            var row = sheet.createRow(2);
            for (int column = 0; column < 7; column++) row.createCell(column).setCellValue("invalid-" + column);
            workbook.write(out); file = out.toByteArray();
        }
    }

    @AfterEach void cleanup() {
        reset(tasks);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM import_task WHERE created_by IN (?,?)", owner, other);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void uploadDoesNotAdoptAnotherConnectionsLastInsertId(Flow flow) throws Exception {
        jdbc.update("INSERT INTO import_task(task_type,request_key,status,created_by) VALUES('FORECAST',?,'PENDING',?)",
                "foreign:" + UUID.randomUUID(), other);
        Long foreignId = jdbc.queryForObject("SELECT id FROM import_task WHERE created_by=?", Long.class, other);
        // Each nontransactional mapper call may borrow a different pooled connection. Emulate
        // that connection having this unrelated id, using the real MySQL session function.
        doAnswer(invocation -> jdbc.queryForObject("SELECT LAST_INSERT_ID(?)", Long.class, foreignId))
                .when(tasks).selectLastInsertId();
        ImportTask uploaded = upload(flow);
        Long expected = jdbc.queryForObject("SELECT id FROM import_task WHERE created_by=?", Long.class, owner);
        assertEquals(expected, uploaded.id(), "Response and staging must use the actual request-key task");
        assertEquals(owner, jdbc.queryForObject("SELECT created_by FROM import_task WHERE id=?", Long.class, uploaded.id()));
        preview(flow, uploaded.id());
    }

    @ParameterizedTest @EnumSource(Flow.class)
    void simultaneousFirstUploadsReturnOneTaskInsteadOfDuplicateKeyFailure(Flow flow) throws Exception {
        var firstReads = new CyclicBarrier(2);
        doAnswer(invocation -> {
            ImportTask found;
            try (var session = sessions.openSession(true)) {
                found = session.getMapper(ImportTaskMapper.class).selectByRequestKey(invocation.getArgument(0));
            }
            if (found == null) firstReads.await(10, TimeUnit.SECONDS);
            return found;
        }).when(tasks).selectByRequestKey(anyString());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { login(); try { return upload(flow); }
                finally { SecurityContextHolder.clearContext(); } });
            var second = workers.submit(() -> { login(); try { return upload(flow); }
                finally { SecurityContextHolder.clearContext(); } });
            ImportTask a = first.get(20, TimeUnit.SECONDS), b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.id(), b.id());
            assertEquals(a.requestKey(), b.requestKey());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM import_task WHERE created_by=?", Integer.class, owner));
            preview(flow, a.id());
        }
    }

    private ImportTask upload(Flow flow) throws Exception {
        // Different filenames isolate filesystem writes while keeping the same file checksum.
        String name = "upload-identity-" + UUID.randomUUID() + ".xlsx";
        return switch (flow) {
            case FORECAST -> forecast.upload(new ByteArrayInputStream(file), name, owner);
            case ITEMS -> inventory.uploadItem(new ByteArrayInputStream(file), name, owner);
            case LEGACY -> inventory.upload(new ByteArrayInputStream(file), name, LocalDate.of(2026, 10, 17), owner);
        };
    }
    private void preview(Flow flow, Long task) {
        login();
        var result = switch (flow) {
            case FORECAST -> forecast.getStagingResult(task);
            case ITEMS -> inventory.getItemStagingResult(task);
            case LEGACY -> inventory.getStagingResult(task);
        };
        assertNotNull(result);
        assertTrue(result.hasErrors());
    }
    private Long user() {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var statement = c.prepareStatement("INSERT INTO user(username,password_hash) VALUES(?,'test-only')", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "upload-" + UUID.randomUUID()); return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(owner, "upload-test"), null, List.of()));
    }
}
