package com.mrp.config;

import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.UserService;
import com.mrp.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, @Lazy UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                var claims = jwtService.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.getSubject();

                // Load user permissions
                Set<String> permissions = userService.getEffectivePermissions(userId);

                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                var principal = new CurrentUser.UserPrincipal(userId, username);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
