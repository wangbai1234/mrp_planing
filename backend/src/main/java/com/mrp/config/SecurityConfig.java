package com.mrp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/icons.svg", "/*.js", "/*.css", "/*.ico").permitAll()

                // Schedule permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/**").hasAuthority("schedule:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/plans/**").hasAuthority("schedule:edit")
                .requestMatchers(HttpMethod.PUT, "/api/v1/plans/**").hasAuthority("schedule:edit")
                .requestMatchers(HttpMethod.POST, "/api/v1/recalculations/**").hasAuthority("schedule:recalculate")
                .requestMatchers(HttpMethod.POST, "/api/v1/plans/*/publish").hasAuthority("schedule:publish")
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/*/history").hasAuthority("schedule:history")
                .requestMatchers(HttpMethod.GET, "/api/v1/plans/*/export").hasAuthority("schedule:export")

                // Forecast permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-versions").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-imports/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-imports/**").hasAuthority("forecast:import")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-imports/*/confirm").hasAuthority("forecast:import_confirm")

                // Inventory permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-imports/**").hasAuthority("inventory:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory-imports/**").hasAuthority("inventory:import")
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-snapshots/**").hasAuthority("inventory:view")

                // Capacity permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/capacity-lines/**").hasAuthority("capacity:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/capacity-lines/**").hasAuthority("capacity:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/capacity-lines/**").hasAuthority("capacity:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/capacity-lines/**").hasAuthority("capacity:delete")

                // User management permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/users").hasAuthority("user:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/users").hasAuthority("user:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasAuthority("user:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasAuthority("user:delete")

                // Role management permissions
                .requestMatchers(HttpMethod.GET, "/api/v1/roles").hasAuthority("role:view")
                .requestMatchers(HttpMethod.POST, "/api/v1/roles").hasAuthority("role:create")
                .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**").hasAuthority("role:update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**").hasAuthority("role:delete")

                // Permission management
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

                // Import/Export task status
                .requestMatchers(HttpMethod.GET, "/api/v1/import-tasks/**").hasAuthority("forecast:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/inventory-import-tasks/**").hasAuthority("inventory:view")
                .requestMatchers(HttpMethod.GET, "/api/v1/export-tasks/**").hasAuthority("schedule:export")

                // Audit log
                .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs").hasAuthority("audit:view")

                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
