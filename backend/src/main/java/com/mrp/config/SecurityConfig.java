package com.mrp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(this::handleUnauthorized)
                .accessDeniedHandler(this::handleForbidden)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/feishu/authorize").permitAll()
                .requestMatchers("/api/v1/auth/feishu/callback").permitAll()
                .requestMatchers("/api/v1/auth/change-password").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/icons.svg", "/*.js", "/*.css", "/*.ico").permitAll()

                // Schedule permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/plans/*/exports").hasAuthority("schedule:export")
                .requestMatchers(HttpMethod.POST, "/api/v1/recalculations/**", "/api/v1/plans/**").denyAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/plans/**").denyAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/plans/**").denyAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/plans/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/recalculations/**").hasAuthority("schedule:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/*/history").hasAuthority("schedule:history")
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/*/export").hasAuthority("schedule:export")
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/**").hasAuthority("schedule:view")

                // Forecast permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-workspace/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-workspace/draft").hasAnyAuthority("forecast:create", "forecast:edit", "forecast:delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-workspace/draft/*/lines").hasAuthority("forecast:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/forecast-workspace/draft/*/lines/*").hasAuthority("forecast:edit")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-workspace/draft/*/lines/*/delete").hasAuthority("forecast:delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-workspace/draft/*/publish").hasAuthority("forecast:publish")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-workspace/draft/*/discard").hasAuthority("forecast:edit")
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-versions", "/api/v1/forecast-versions/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-imports/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-imports/*/confirm").hasAuthority("forecast:import_confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-imports/**").hasAuthority("forecast:import")

                // Inventory permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-imports/**").hasAuthority("inventory:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory-imports/items/*/confirm",
                    "/api/v1/inventory-imports/*/confirm").hasAuthority("inventory:import_confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory-imports/**").hasAuthority("inventory:import")
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-snapshots/**").hasAuthority("inventory:view")

                // Capacity permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/capacity-lines/**").hasAnyAuthority("capacity:view", "mrp_batch:list")
                .requestMatchers(HttpMethod.POST, "/api/v1/capacity-lines/**").hasAuthority("capacity:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/capacity-lines/**").hasAuthority("capacity:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/capacity-lines/**").hasAuthority("capacity:delete")

                // User management permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/permissions").hasAuthority("user:view_permissions")
                .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/**").hasAuthority("user:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/users").hasAuthority("user:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/roles").hasAuthority("user:assign_role")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/permissions").hasAuthority("user:assign_permission")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/reset-password").hasAuthority("user:reset_password")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/status").hasAnyAuthority("user:enable", "user:disable")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasAuthority("user:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasAuthority("user:delete")

                // Role management permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/roles/factories", "/api/v1/roles/*",
                    "/api/v1/roles/*/permissions", "/api/v1/roles/*/data-scope")
                    .hasAnyAuthority("role:view", "role:assign_permission")
                .requestMatchers(HttpMethod.GET, "/api/v1/roles/**").hasAuthority("role:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/roles").hasAuthority("role:create")
                .requestMatchers(HttpMethod.POST, "/api/v1/roles/*/copy")
                    .access(org.springframework.security.authorization.AuthorizationManagers.allOf(
                        org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("role:create"),
                        org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("role:assign_permission")))
                .requestMatchers(HttpMethod.PUT, "/api/v1/roles/*/permissions").hasAuthority("role:assign_permission")
                .requestMatchers(HttpMethod.PUT, "/api/v1/roles/*/configuration").hasAuthority("role:assign_permission")
                .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**").hasAuthority("role:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**").hasAuthority("role:delete")

                // Permission management
                .requestMatchers(HttpMethod.GET, "/api/v1/permissions/tree")
                    .hasAnyAuthority("permission:view", "role:assign_permission")
                .requestMatchers(HttpMethod.GET, "/api/v1/permissions/**").hasAuthority("permission:view")

                // Material management
                .requestMatchers(HttpMethod.GET, "/api/v1/materials/**").hasAuthority("material:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/materials/**").hasAuthority("material:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/materials/**").hasAuthority("material:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/materials/**").hasAuthority("material:manage")

                // BOM management
                .requestMatchers(HttpMethod.GET, "/api/v1/bom/**").hasAuthority("bom:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/bom/**").hasAuthority("bom:manage")

                // Material Category management
                .requestMatchers(HttpMethod.GET, "/api/v1/material-categories/**").hasAuthority("material_category:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/material-categories/**").hasAuthority("material_category:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/material-categories/**").hasAuthority("material_category:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/material-categories/**").hasAuthority("material_category:manage")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/material-categories/**").hasAuthority("material_category:manage")

                // Shipment permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/shipment-imports").hasAuthority("inventory:import")
                .requestMatchers(HttpMethod.GET, "/api/v1/shipments/**").hasAuthority("inventory:view")

                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/factories").hasAnyAuthority("mrp_factory:view", "mrp_calendar:view", "mrp_warehouse_mapping:view", "mrp_batch:list", "mrp_batch:create")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/factory-master/**").hasAuthority("mrp_factory:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/factory-master/**").hasAuthority("mrp_factory:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/factory-master/**").hasAuthority("mrp_factory:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/factory-master/**").hasAuthority("mrp_factory:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/current-bom-status").hasAnyAuthority("mrp_batch:create", "mrp_batch:list", "mrp_workspace:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/reviews", "/api/v1/mrp/batches/*/material-feedback/page")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/schedule-confirmation",
                    "/api/v1/mrp/batches/*/material-feedback/*/respond").hasAuthority("mrp_schedule:confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/material-confirmation").hasAuthority("mrp_shortage:confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/material-analysis").hasAuthority("mrp_shortage:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/material-feedback").hasAuthority("mrp_shortage:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/shortage/**",
                    "/api/v1/mrp/batches/*/requirements/**", "/api/v1/mrp/batches/*/terminal-demands/**",
                    "/api/v1/mrp/batches/*/issues/**", "/api/v1/mrp/batches/*/material-policy-notices/**",
                    "/api/v1/mrp/batches/*/inventory-pools/**").hasAuthority("mrp_shortage:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/pending-unallocated/**")
                    .hasAuthority("mrp_shortage:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/inventory/stock-risks/**",
                    "/api/v1/mrp/inventory/pools/**", "/api/v1/mrp/inventory/allocations/**")
                    .hasAuthority("mrp_shortage:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/rolling-grid",
                    "/api/v1/mrp/batches/*/rolling-details", "/api/v1/mrp/plans/*/daily-details")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/daily-plans")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/daily-transfers").hasAuthority("mrp_plan:adjust")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/linked-transfers", "/api/v1/mrp/batches/*/linked-transfers/**").hasAuthority("mrp_plan:adjust")
                // MRP batch lifecycle permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches").hasAuthority("mrp_batch:create")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/precheck").hasAuthority("mrp_batch:precheck")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/cancel").hasAuthority("mrp_batch:create")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/calculate").hasAuthority("mrp_batch:calculate")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/distribute").hasAuthority("mrp_batch:calculate")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/bom-snapshots").hasAuthority("bom:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/publish").hasAuthority("mrp_batch:publish")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/revise").hasAuthority("mrp_batch:publish")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/transition").hasAuthority("mrp_batch:publish")

                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/material-policies/**").hasAuthority("mrp_config:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/material-policies/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/material-policies/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/material-policies/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/production-calendars/**").hasAuthority("mrp_calendar:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/production-calendars/**").hasAuthority("mrp_calendar:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/production-calendars/**").hasAuthority("mrp_calendar:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/production-calendars/**").hasAuthority("mrp_calendar:manage")

                // MRP config permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/category-role-versions/**").hasAuthority("mrp_config:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/material-policy-versions/**").hasAuthority("mrp_config:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/production-calendar-versions/**").hasAuthority("mrp_calendar:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/category-role-versions/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/material-policy-versions/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/material-policy-versions/**").hasAuthority("mrp_config:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/production-calendar-versions/**").hasAuthority("mrp_calendar:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/production-calendar-versions/**").hasAuthority("mrp_calendar:manage")

                // MRP versioned scope/config permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/factory-warehouse-mappings/**").hasAuthority("mrp_warehouse_mapping:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/factory-warehouse-mappings/**").hasAuthority("mrp_warehouse_mapping:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/factory-warehouse-mappings/**").hasAuthority("mrp_warehouse_mapping:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/factory-warehouse-mappings/**").hasAuthority("mrp_warehouse_mapping:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/factory-warehouse-mapping-versions/**").hasAuthority("mrp_warehouse_mapping:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/factory-warehouse-mapping-versions/**").hasAuthority("mrp_warehouse_mapping:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/factory-warehouse-mapping-versions/**").hasAuthority("mrp_warehouse_mapping:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/stock-policies/**").hasAuthority("mrp_stock_policy:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/stock-policies/**").hasAuthority("mrp_stock_policy:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/stock-policies/**").hasAuthority("mrp_stock_policy:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/stock-policies/**").hasAuthority("mrp_stock_policy:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/stock-policy-versions/**").hasAuthority("mrp_stock_policy:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/stock-policy-versions/**").hasAuthority("mrp_stock_policy:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/stock-policy-versions/**").hasAuthority("mrp_stock_policy:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/business-weeks/**").hasAuthority("mrp_business_week:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/business-weeks/**").hasAuthority("mrp_business_week:manage")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/business-weeks/**").hasAuthority("mrp_business_week:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/business-weeks/**").hasAuthority("mrp_business_week:manage")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/business-week-versions/**").hasAuthority("mrp_business_week:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/business-week-versions/**").hasAuthority("mrp_business_week:manage")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/mrp/business-week-versions/**").hasAuthority("mrp_business_week:manage")

                // MRP decision permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/issues").hasAuthority("mrp_decision:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/issues/*/resolve").hasAuthority("mrp_decision:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/issues/inventory-missing/resolve").hasAuthority("mrp_decision:manage")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/material-policy-notices/confirm-default-explosion").hasAuthority("mrp_decision:manage")

                // MRP execution permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/execution/**").hasAuthority("mrp_execution:confirm")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/*/execution/**")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_batch:list")

                // MRP inventory allocation permissions
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/inventory/stock-risks/**").hasAuthority("mrp_inventory:allocate")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/inventory/**").hasAuthority("mrp_inventory:allocate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/inventory/**").hasAuthority("mrp_inventory:allocate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/mrp/batches/*/inventory-pools/**").hasAuthority("mrp_inventory:allocate")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/inventory-pools/**").hasAuthority("mrp_inventory:allocate")

                // MRP plan permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/plans/*/export").hasAnyAuthority("mrp_plan:export", "mrp_shortage:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/plans/**").hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view", "mrp_batch:list")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/batches/*/overrides").hasAuthority("mrp_plan:adjust")
                .requestMatchers(HttpMethod.POST, "/api/v1/mrp/overrides/*/restore-auto").hasAuthority("mrp_plan:adjust")

                // MRP read-only permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/batches/**")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view", "mrp_batch:list")
                .requestMatchers(HttpMethod.GET, "/api/v1/mrp/**")
                    .hasAnyAuthority("mrp_workspace:view", "mrp_shortage:view", "mrp_batch:list")

                // Import/Export task status
                .requestMatchers(HttpMethod.GET, "/api/v1/import-tasks/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-import-tasks/**").hasAuthority("inventory:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/export-tasks/**").hasAuthority("schedule:export")

                // Audit log
                .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs").hasAnyAuthority("audit:view", "forecast:log", "mrp_factory:view", "mrp_warehouse_mapping:view", "mrp_stock_policy:view", "mrp_config:view", "mrp_business_week:view", "mrp_calendar:view")

                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            );
        return http.build();
    }

    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                     AuthenticationException authException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("UNAUTHORIZED", "Authentication required")));
    }

    private void handleForbidden(HttpServletRequest request, HttpServletResponse response,
                                  AccessDeniedException accessDeniedException) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("FORBIDDEN", "Insufficient permissions")));
    }

}
