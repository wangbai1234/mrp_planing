package com.mrp.security;

import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.UserService;
import com.mrp.config.JwtAuthenticationFilter;
import com.mrp.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EndpointPermissionRegressionTest.Probe.class)
@ContextConfiguration(classes = {SecurityConfig.class, JwtAuthenticationFilter.class, EndpointPermissionRegressionTest.Probe.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, EndpointPermissionRegressionTest.Probe.class})
class EndpointPermissionRegressionTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserService userService;

    @ParameterizedTest
    @CsvSource({
        "/api/v1/users/1,user:view",
        "/api/v1/users/1/permissions,user:view_permissions",
        "/api/v1/forecast-versions/1,forecast:view",
        "/api/v1/forecast-versions/1/details,forecast:view",
        "/api/v1/plans/1/export,schedule:export",
        "/api/v1/plans/1/history,schedule:history",
        "/api/v1/mrp/plans/1/export,mrp_plan:export"
    })
    void detailsRequireAnActualGrant(String path, String permission) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("limited").authorities())).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("authorized").authorities(new SimpleGrantedAuthority(permission))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({
        "GET,/api/v1/forecast-versions,forecast:view",
        "POST,/api/v1/forecast-imports/999999/confirm,forecast:import_confirm",
        "POST,/api/v1/forecast-workspace/draft/999999/publish,forecast:publish",
        "GET,/api/v1/inventory-snapshots,inventory:view",
        "POST,/api/v1/inventory-imports/999999/confirm,inventory:import_confirm",
        "POST,/api/v1/inventory-imports/items/999999/confirm,inventory:import_confirm",
        "POST,/api/v1/inventory-imports/items,inventory:import",
        "GET,/api/v1/materials,material:view",
        "POST,/api/v1/materials,material:manage",
        "GET,/api/v1/bom/materials,bom:view",
        "POST,/api/v1/bom/sync,bom:manage",
        "GET,/api/v1/material-categories,material_category:view",
        "POST,/api/v1/material-categories,material_category:manage",
        "GET,/api/v1/users,user:view",
        "PUT,/api/v1/users/999999/roles,user:assign_role",
        "PUT,/api/v1/users/999999/permissions,user:assign_permission",
        "PUT,/api/v1/users/999999/reset-password,user:reset_password",
        "GET,/api/v1/roles,role:view",
        "PUT,/api/v1/roles/999999/configuration,role:assign_permission",
        "GET,/api/v1/plans,schedule:view",
        "GET,/api/v1/plans/999999/export,schedule:export",
        "POST,/api/v1/plans/999999/exports,schedule:export",
        "GET,/api/v1/mrp/factory-master,mrp_factory:view",
        "POST,/api/v1/mrp/factory-master,mrp_factory:manage",
        "GET,/api/v1/mrp/production-calendars,mrp_calendar:view",
        "POST,/api/v1/mrp/production-calendars,mrp_calendar:manage",
        "GET,/api/v1/mrp/stock-policies,mrp_stock_policy:view",
        "POST,/api/v1/mrp/stock-policies,mrp_stock_policy:manage",
        "GET,/api/v1/mrp/batches/999999/shortage/page,mrp_shortage:view",
        "POST,/api/v1/mrp/batches/999999/material-analysis,mrp_shortage:manage",
        "POST,/api/v1/mrp/batches/999999/material-confirmation,mrp_shortage:confirm",
        "POST,/api/v1/mrp/batches/999999/schedule-confirmation,mrp_schedule:confirm",
        "POST,/api/v1/mrp/batches/999999/overrides,mrp_plan:adjust",
        "POST,/api/v1/mrp/batches/999999/precheck,mrp_batch:precheck",
        "POST,/api/v1/mrp/batches/999999/calculate,mrp_batch:calculate",
        "POST,/api/v1/mrp/batches/999999/publish,mrp_batch:publish",
        "GET,/api/v1/mrp/plans/999999/export,mrp_plan:export"
    })
    void permissionMatrixRequiresTheConfiguredGrant(String method, String path, String permission) throws Exception {
        mvc.perform(request(method, path)).andExpect(status().isUnauthorized());
        mvc.perform(request(method, path).with(user("limited").authorities()))
                .andExpect(status().isForbidden());
        mvc.perform(request(method, path).with(user("authorized").authorities(new SimpleGrantedAuthority(permission))))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder request(String method, String path) {
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/plans/1/export,schedule:view",
        "/api/v1/plans/1/history,schedule:view",
        "/api/v1/mrp/plans/1/export,mrp_workspace:view",
        "/api/v1/users/1/permissions,user:view"
    })
    void generalReadGrantCannotShadowSpecificPermission(String path, String permission) throws Exception {
        mvc.perform(get(path).with(user("reader").authorities(new SimpleGrantedAuthority(permission))))
                .andExpect(status().isForbidden());
    }

    @Test void currentBomStatusIsAvailableForBatchCreationWithoutBomView() throws Exception {
        mvc.perform(get("/api/v1/mrp/batches/current-bom-status").with(user("creator").authorities(
                new SimpleGrantedAuthority("mrp_batch:create")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mrp/batches/current-bom-status").with(user("bom-only").authorities(
                new SimpleGrantedAuthority("bom:view")))).andExpect(status().isForbidden());
    }

    @Test void factoryAndWarehousePagesHaveIndependentApiPermissions() throws Exception {
        var factory = user("factory").authorities(new SimpleGrantedAuthority("mrp_factory:view"));
        var warehouse = user("warehouse").authorities(new SimpleGrantedAuthority("mrp_warehouse_mapping:view"));
        mvc.perform(get("/api/v1/mrp/factory-master").with(factory)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mrp/factory-master").with(warehouse)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mrp/factory-warehouse-mappings").with(warehouse)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mrp/factory-warehouse-mappings").with(factory)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mrp/factories").with(warehouse)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/mrp/factory-master").with(factory)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/factory-warehouse-mappings").with(warehouse)).andExpect(status().isForbidden());
    }
    @Test void scheduleAndMaterialWorkbenchesHaveSeparateWriteGrants() throws Exception {
        var planner = user("planner").authorities(new SimpleGrantedAuthority("mrp_schedule:confirm"),
                new SimpleGrantedAuthority("mrp_workspace:view"));
        var material = user("material").authorities(new SimpleGrantedAuthority("mrp_shortage:view"),
                new SimpleGrantedAuthority("mrp_shortage:manage"), new SimpleGrantedAuthority("mrp_shortage:confirm"));
        mvc.perform(post("/api/v1/mrp/batches/1/schedule-confirmation").with(planner)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/mrp/batches/1/material-analysis").with(planner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/batches/1/material-confirmation").with(planner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/batches/1/schedule-confirmation").with(material)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/batches/1/material-analysis").with(material)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/mrp/batches/1/material-confirmation").with(material)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mrp/batches/1/shortage/page").with(planner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mrp/batches/1/shortage/page").with(material)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/mrp/inventory/stock-risks/1/apply-zero-thresholds").with(planner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/inventory/stock-risks/1/apply-zero-thresholds").with(material)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mrp/inventory/stock-risks/1/apply-zero-thresholds").with(user("material-inventory").authorities(
                new SimpleGrantedAuthority("mrp_shortage:view"), new SimpleGrantedAuthority("mrp_inventory:allocate"))))
                .andExpect(status().isOk());
    }

    @Test void importPublishAndRoleAssignmentUseSpecificGrants() throws Exception {
        mvc.perform(post("/api/v1/forecast-imports/1/confirm").with(user("u").authorities(new SimpleGrantedAuthority("forecast:import"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forecast-imports/1/confirm").with(user("u").authorities(new SimpleGrantedAuthority("forecast:import_confirm"))))
                .andExpect(status().isOk());
        for (String path : new String[]{"/api/v1/inventory-imports/1/confirm",
                "/api/v1/inventory-imports/items/1/confirm"}) {
            mvc.perform(post(path).with(user("u").authorities(new SimpleGrantedAuthority("inventory:import"))))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("u").authorities(new SimpleGrantedAuthority("inventory:import_confirm"))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/plans/1/publish").with(user("u").authorities(new SimpleGrantedAuthority("schedule:edit"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/1/roles").with(user("u").authorities(new SimpleGrantedAuthority("user:update"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/1/roles").with(user("u").authorities(new SimpleGrantedAuthority("user:assign_role"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/1/permissions").with(user("u").authorities(new SimpleGrantedAuthority("user:update"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/1/permissions").with(user("u").authorities(new SimpleGrantedAuthority("user:assign_permission"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/1/reset-password").with(user("u").authorities(new SimpleGrantedAuthority("user:update"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/1/reset-password").with(user("u").authorities(new SimpleGrantedAuthority("user:reset_password"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/1/status").with(user("u").authorities(new SimpleGrantedAuthority("user:update"))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/1").with(user("u").authorities(new SimpleGrantedAuthority("user:update"))))
                .andExpect(status().isOk());
    }

    @Test void forecastWorkspaceMutationsRequireTheirOwnGrants() throws Exception {
        var viewer = user("viewer").authorities(new SimpleGrantedAuthority("forecast:view"));
        mvc.perform(get("/api/v1/forecast-workspace/draft/1/compare").with(viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/forecast-workspace/draft").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forecast-workspace/draft/1/lines/2/delete").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forecast-workspace/draft/1/publish").with(viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forecast-workspace/draft/1/publish").with(user("publisher").authorities(
                new SimpleGrantedAuthority("forecast:publish")))).andExpect(status().isOk());
    }

    @Test void copyingGrantsRequiresBothCreateAndAssignPermission() throws Exception {
        for (String permission : new String[]{"role:create", "role:assign_permission", "role:view"}) {
            mvc.perform(post("/api/v1/roles/1/copy").with(user("u").authorities(new SimpleGrantedAuthority(permission))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/roles/1/copy").with(user("u").authorities(
                new SimpleGrantedAuthority("role:create"), new SimpleGrantedAuthority("role:assign_permission"))))
                .andExpect(status().isOk());
    }

    // Probe isolates URL authorization from DB/service behavior. Runtime tests use real controllers/JWTs.
    @RestController
    static class Probe {
        @RequestMapping("/api/v1/**") String respond() { return "allowed"; }
    }
}
