package com.mrp.mrp.controller;

import com.mrp.mrp.service.LinkedDailyTransferService;
import com.mrp.mrp.service.RollingPlanningQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PlanningViewPermissionTest {
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void materialPermissionCannotOpenMachineGridOrDetails() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "material", null, List.of(new SimpleGrantedAuthority("mrp_shortage:view"))));
        var query = mock(RollingPlanningQueryService.class);
        var controller = new RollingPlanningController(query, mock(LinkedDailyTransferService.class));
        var grid = assertThrows(ResponseStatusException.class,
                () -> controller.grid(1L, 1, 20, null, null, null, "MACHINE_OUTPUT"));
        var details = assertThrows(ResponseStatusException.class,
                () -> controller.details(1L, 1, 20, null, null, null, null,
                        "MACHINE_OUTPUT", null, null, null, null));
        assertEquals(403, grid.getStatusCode().value());
        assertEquals(403, details.getStatusCode().value());
        verifyNoInteractions(query);
    }
}
