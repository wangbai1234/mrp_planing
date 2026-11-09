package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.inventory.service.InventoryImportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and the full HTTP security chain, without changing material/BOM data. */
@AutoConfigureMockMvc
class ImportTaskOwnershipIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired ForecastImportService forecast;
    @Autowired InventoryImportService inventory;
    Long owner, other;
    byte[] file;
    final List<Long> taskIds = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        owner = user(); other = user();
        // A real workbook with an invalid row: owners can preview/download, nobody can confirm.
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("ownership");
            sheet.createRow(0).createCell(0).setCellValue("invalid-" + UUID.randomUUID());
            sheet.createRow(1).createCell(0).setCellValue("test only");
            var row = sheet.createRow(2);
            for (int col = 0; col < 7; col++) row.createCell(col).setCellValue("invalid-" + col);
            workbook.write(out); file = out.toByteArray();
        }
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        for (Long id : taskIds) jdbc.update("DELETE FROM import_task WHERE id=?", id);
        jdbc.update("DELETE FROM user WHERE id IN (?,?)", owner, other);
    }

    @Test void identicalFilesDeduplicateOnlyWithinUploaderAndFlow() throws Exception {
        for (String flow : List.of("forecast", "items", "legacy")) {
            long first = upload(flow, owner);
            assertEquals(first, upload(flow, owner));
            long second = upload(flow, other);
            assertNotEquals(first, second);
            assertEquals(owner, jdbc.queryForObject("SELECT created_by FROM import_task WHERE id=?", Long.class, first));
        }
        assertEquals(6, new HashSet<>(taskIds).size());
    }

    @Test void previewErrorDownloadAndConfirmRequireOwnerBeforeCacheOrWrites() throws Exception {
        int versions = count("forecast_version"), snapshots = count("inventory_snapshot");
        for (String flow : List.of("forecast", "items", "legacy")) {
            long id = upload(flow, owner);
            if (!flow.equals("legacy")) {
                String preview = flow.equals("forecast") ? "/import-tasks/" : "/inventory-import-tasks/";
                String errors = flow.equals("forecast") ? "/forecast-imports/" : "/inventory-imports/";
                call(owner, get("/api/v1" + preview + id)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.errorRows").value(org.hamcrest.Matchers.greaterThan(0)));
                call(owner, get("/api/v1" + errors + id + "/errors")).andExpect(status().isOk())
                        .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                call(other, get("/api/v1" + preview + id)).andExpect(status().isForbidden());
                call(other, get("/api/v1" + errors + id + "/errors")).andExpect(status().isForbidden());
            } else {
                login(owner); assertFalse(inventory.getStagingResult(id).errors().isEmpty());
                login(other); forbidden(() -> inventory.getStagingResult(id));
            }
            call(other, confirm(flow, id)).andExpect(status().isForbidden());
            call(owner, confirm(flow, id)).andExpect(status().isBadRequest());
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM import_task WHERE id=?", String.class, id));
        }
        assertEquals(versions, count("forecast_version"));
        assertEquals(snapshots, count("inventory_snapshot"));
    }

    @Test void taskCannotBeReadOrConfirmedThroughAnotherImportFlow() throws Exception {
        long forecastId = upload("forecast", owner), itemId = upload("items", owner), legacyId = upload("legacy", owner);
        for (long id : List.of(itemId, legacyId)) {
            call(owner, get("/api/v1/import-tasks/" + id)).andExpect(status().isForbidden());
            call(owner, get("/api/v1/forecast-imports/" + id + "/errors")).andExpect(status().isForbidden());
            call(owner, confirm("forecast", id)).andExpect(status().isForbidden());
        }
        for (long id : List.of(forecastId, legacyId)) {
            call(owner, get("/api/v1/inventory-import-tasks/" + id)).andExpect(status().isForbidden());
            call(owner, get("/api/v1/inventory-imports/" + id + "/errors")).andExpect(status().isForbidden());
            call(owner, confirm("items", id)).andExpect(status().isForbidden());
        }
        for (long id : List.of(forecastId, itemId))
            call(owner, confirm("legacy", id)).andExpect(status().isForbidden());
    }

    @Test void missingActorSpoofedActorAndUnownedTasksAreDenied() throws Exception {
        long id = upload("forecast", owner);
        SecurityContextHolder.clearContext();
        forbidden(() -> forecast.getStagingResult(id));
        forbidden(() -> forecast.upload(new ByteArrayInputStream(file), "owner.xlsx", owner));
        login(other);
        forbidden(() -> forecast.confirm(id, owner));
        forbidden(() -> inventory.uploadItem(new ByteArrayInputStream(file), "owner.xlsx", owner));
        jdbc.update("UPDATE import_task SET created_by=NULL WHERE id=?", id);
        call(owner, get("/api/v1/import-tasks/" + id)).andExpect(status().isForbidden());
        call(owner, get("/api/v1/import-tasks/9223372036854775807")).andExpect(status().isNotFound());
    }

    @Test void historicalRequestKeysRemainOwnerAccessibleAndCannotBypassTypeCheck() throws Exception {
        long id = upload("forecast", owner);
        jdbc.update("UPDATE import_task SET request_key=CONCAT('forecast:',input_checksum) WHERE id=?", id);
        call(owner, get("/api/v1/import-tasks/" + id)).andExpect(status().isOk());
        call(other, get("/api/v1/import-tasks/" + id)).andExpect(status().isForbidden());
        jdbc.update("UPDATE import_task SET task_type='INVENTORY' WHERE id=?", id);
        call(owner, get("/api/v1/import-tasks/" + id)).andExpect(status().isForbidden());
    }

    private long upload(String flow, Long user) throws Exception {
        login(user);
        var task = switch (flow) {
            case "forecast" -> forecast.upload(new ByteArrayInputStream(file), "owner.xlsx", user);
            case "items" -> inventory.uploadItem(new ByteArrayInputStream(file), "owner.xlsx", user);
            default -> inventory.upload(new ByteArrayInputStream(file), "owner.xlsx", LocalDate.of(2026, 10, 16), user);
        };
        taskIds.add(task.id()); return task.id();
    }
    private MockHttpServletRequestBuilder confirm(String flow, long id) {
        return switch (flow) {
            case "forecast" -> post("/api/v1/forecast-imports/" + id + "/confirm");
            case "items" -> post("/api/v1/inventory-imports/items/" + id + "/confirm");
            default -> post("/api/v1/inventory-imports/" + id + "/confirm").param("snapshotDate", "2026-10-16");
        };
    }
    private org.springframework.test.web.servlet.ResultActions call(Long user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(authentication(auth(user))));
    }
    private UsernamePasswordAuthenticationToken auth(Long user) {
        return new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(user, "owner-test"), null,
                List.of("forecast:view", "forecast:import", "forecast:import_confirm", "inventory:view", "inventory:import",
                        "inventory:import_confirm")
                        .stream().map(SimpleGrantedAuthority::new).toList());
    }
    private void login(Long user) { SecurityContextHolder.getContext().setAuthentication(auth(user)); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private Long user() {
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> { var s = c.prepareStatement("INSERT INTO user(username,password_hash) VALUES(?,'test-only')", Statement.RETURN_GENERATED_KEYS);
            s.setString(1, "owner-" + UUID.randomUUID()); return s; }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void forbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(403, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}
