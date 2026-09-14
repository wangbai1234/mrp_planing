package com.mrp.auth.controller;

import com.mrp.auth.domain.Role;
import com.mrp.auth.service.RoleService;
import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<List<Role>> list() {
        return ApiResponse.ok(roleService.getAll());
    }

    @GetMapping("/active")
    public ApiResponse<List<Role>> listActive() {
        return ApiResponse.ok(roleService.getActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<Role> getById(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ApiResponse.error("ROLE_NOT_FOUND", "角色不存在");
        }
        return ApiResponse.ok(role);
    }

    @PostMapping
    public ApiResponse<Role> create(@RequestBody CreateRoleRequest request) {
        Role role = new Role();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        Role created = roleService.create(role, CurrentUser.getUserId());
        return ApiResponse.ok(created);
    }

    @PutMapping("/{id}")
    public ApiResponse<Role> update(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        Role updated = roleService.update(id, role, CurrentUser.getUserId());
        return ApiResponse.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id, CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<Long>> getPermissions(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getPermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> updatePermissions(@PathVariable Long id, @RequestBody PermissionsRequest request) {
        roleService.updatePermissions(id, request.permissionIds(), CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<Role> copy(@PathVariable Long id, @RequestBody CopyRoleRequest request) {
        Role copied = roleService.copy(id, request.name(), request.code(), CurrentUser.getUserId());
        return ApiResponse.ok(copied);
    }

    public record CreateRoleRequest(String code, String name, String description) {}

    public record UpdateRoleRequest(String name, String description, String status) {}

    public record PermissionsRequest(List<Long> permissionIds) {}

    public record CopyRoleRequest(String name, String code) {}
}
