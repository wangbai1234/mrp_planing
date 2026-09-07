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
                .requestMatchers(HttpMethod.GET, "/api/v1/forecast-versions").hasAnyRole("ADMIN", "PLANNER", "BUSINESS", "READONLY")
                .requestMatchers(HttpMethod.POST, "/api/v1/forecast-imports/**").hasAnyRole("ADMIN", "BUSINESS")
                .requestMatchers(HttpMethod.POST, "/api/v1/inventory-imports/**").hasAnyRole("ADMIN", "WAREHOUSE")
                .requestMatchers(HttpMethod.POST, "/api/v1/shipment-sync-tasks/**").hasAnyRole("ADMIN")
                .requestMatchers("/api/v1/capacity-lines/**").hasAnyRole("ADMIN", "PLANNER")
                .requestMatchers("/api/v1/plans/**", "/api/v1/recalculations/**").hasAnyRole("ADMIN", "PLANNER")
                .requestMatchers(HttpMethod.GET, "/api/v1/materials/template").hasAnyRole("ADMIN", "PLANNER", "BUSINESS", "READONLY")
                .requestMatchers("/api/v1/materials/**").hasAnyRole("ADMIN", "PLANNER")
                .requestMatchers("/api/v1/bom/**").hasAnyRole("ADMIN", "PLANNER", "BUSINESS", "READONLY")
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
