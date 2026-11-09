package com.mrp.forecast;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.importexport.excel.ForecastExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Excel and MySQL confirmation; material metadata are local read-only fixtures. */
class ForecastMetadataCapacityIntegrationTest extends BaseIntegrationTest {
    @Autowired ForecastImportService forecast;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @TempDir Path directory;
    Long owner, task;

    @BeforeEach void setup() {
        owner = insert("INSERT INTO user(username,password_hash) VALUES(?,'test-only')", "capacity-" + UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(owner, "capacity-test"), null, List.of()));
        task = insert("INSERT INTO import_task(task_type,request_key,status,created_by) VALUES('FORECAST',?,'PENDING',?)",
                "forecast:" + UUID.randomUUID(), owner);
    }

    @AfterEach void cleanup() {
        cache().remove(task);
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM forecast_detail WHERE version_id IN (SELECT id FROM forecast_version WHERE created_by=?)", owner);
        jdbc.update("DELETE FROM forecast_version WHERE created_by=?", owner);
        jdbc.update("DELETE FROM import_task WHERE created_by=?", owner);
        jdbc.update("DELETE FROM user WHERE id=?", owner);
    }

    @ParameterizedTest @ValueSource(ints = {129, 256})
    void validSourceSpecificationSurvivesPreviewAndConfirmationWithoutTruncation(int characters) throws Exception {
        String specification = "规".repeat(characters - 1) + "\uD83D\uDE80";
        assertEquals(characters, specification.codePointCount(0, specification.length()));
        var material = new Material(1L, "CAPACITY-TEST", "规格容量夹具", "project", specification, "台",
                null, null, null, null, null, true, null, null, "API_SYNC", null, false, null, null, null);
        MaterialMapper source = mock(MaterialMapper.class);
        when(source.selectByCode("CAPACITY-TEST")).thenReturn(material);
        Path path = directory.resolve("metadata.xlsx");
        try (var book = new XSSFWorkbook(); var stream = Files.newOutputStream(path)) {
            var sheet = book.createSheet("forecast");
            var header = sheet.createRow(0);
            String[] labels = {"工厂", "形态", "料号", "模具", "状态", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03"};
            for (int i = 0; i < labels.length; i++) header.createCell(i).setCellValue(labels[i]);
            sheet.createRow(1).createCell(0).setCellValue("test-only");
            var row = sheet.createRow(2);
            String[] values = {"爱培科", "整机", "CAPACITY-TEST", "mold", "test", "123", "123", "123", "123", "123", "123"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            book.write(stream);
        }
        ParseResult<ForecastDetail> parsed = new ForecastExcelParser(source).parse(path, null);
        assertFalse(parsed.hasErrors(), () -> parsed.errors().toString());
        assertEquals(1, parsed.successRows());
        assertEquals(6, parsed.rows().size());
        assertTrue(parsed.rows().stream().allMatch(row -> specification.equals(row.platform())));
        cache().put(task, parsed);
        long version = forecast.confirm(task, owner).id();
        var rows = forecast.getVersionDetails(version);
        assertEquals(6, rows.size());
        assertEquals(6, rows.stream().map(ForecastDetail::planMonth).distinct().count());
        for (var row : rows) {
            assertEquals(specification, row.platform());
            assertEquals(0, new BigDecimal("123").compareTo(row.forecastQty()));
        }
        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, task));
        assertFalse(cache().containsKey(task));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ParseResult<ForecastDetail>> cache() {
        return (Map<Long, ParseResult<ForecastDetail>>) Objects.requireNonNull(ReflectionTestUtils.getField(forecast, "stagingCache"));
    }

    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
