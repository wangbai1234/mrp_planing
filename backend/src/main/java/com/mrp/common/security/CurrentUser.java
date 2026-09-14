package com.mrp.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUser {

    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    public static String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.username();
        }
        return null;
    }

    public static boolean hasPermission(String permissionCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(permissionCode));
        }
        return false;
    }

    public record UserPrincipal(Long userId, String username) {}
}
