package com.mrp.auth.controller;

import com.mrp.auth.domain.Permission;
import com.mrp.auth.service.PermissionService;
import com.mrp.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<List<Permission>> list() {
        return ApiResponse.ok(permissionService.getAll());
    }

    @GetMapping("/tree")
    public ApiResponse<Map<Long, List<Permission>>> tree() {
        return ApiResponse.ok(permissionService.getPermissionTreeMap());
    }
}
