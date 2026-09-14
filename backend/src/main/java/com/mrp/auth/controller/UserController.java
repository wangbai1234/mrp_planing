package com.mrp.auth.controller;

import com.mrp.auth.domain.User;
import com.mrp.auth.service.UserService;
import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Long roleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(userService.list(keyword, isActive, roleId, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error("USER_NOT_FOUND", "用户不存在");
        }
        return ApiResponse.ok(user);
    }

    @PostMapping
    public ApiResponse<User> create(@RequestBody CreateUpdateUserRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setDepartment(request.department());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        User created = userService.create(user, request.roleIds(), CurrentUser.getUserId());
        return ApiResponse.ok(created);
    }

    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @RequestBody CreateUpdateUserRequest request) {
        User user = new User();
        user.setDisplayName(request.displayName());
        user.setDepartment(request.department());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        User updated = userService.update(id, user, CurrentUser.getUserId());
        return ApiResponse.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id, CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        userService.updateStatus(id, request.isActive(), CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody RolesRequest request) {
        userService.assignRoles(id, request.roleIds(), CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<Map<String, Object>> getPermissions(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUserPermissionDetail(id));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> updatePermissions(@PathVariable Long id, @RequestBody PermissionsRequest request) {
        userService.updateUserPermissions(id, request.permissions(), CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id, CurrentUser.getUserId());
        return ApiResponse.ok();
    }

    public record CreateUpdateUserRequest(
            String username,
            String displayName,
            String department,
            String email,
            String phone,
            List<Long> roleIds
    ) {}

    public record StatusRequest(boolean isActive) {}

    public record RolesRequest(List<Long> roleIds) {}

    public record PermissionsRequest(
            List<com.mrp.auth.domain.UserPermission> permissions
    ) {}
}
