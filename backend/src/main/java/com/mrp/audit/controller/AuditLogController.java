package com.mrp.audit.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.mrp.audit.repository.AuditLogMapper;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogMapper mapper;

    public AuditLogController(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        boolean scopedConfigLog = resourceId != null && !resourceId.isBlank() &&
                (("mrp_factory".equals(resourceType) && CurrentUser.hasPermission("mrp_factory:view")) ||
                 ("factory_warehouse_mapping".equals(resourceType) && CurrentUser.hasPermission("mrp_warehouse_mapping:view")) ||
                 ("stock_policy".equals(resourceType) && CurrentUser.hasPermission("mrp_stock_policy:view")) ||
                 ("material_policy".equals(resourceType) && CurrentUser.hasPermission("mrp_config:view")) ||
                 ("business_week".equals(resourceType) && CurrentUser.hasPermission("mrp_business_week:view")) ||
                 ("mrp_production_calendar".equals(resourceType) && CurrentUser.hasPermission("mrp_calendar:view")));
        boolean forecastLog = CurrentUser.hasPermission("forecast:log") && resourceType != null &&
                (resourceType.equals("forecast") || resourceType.equals("forecast_line") ||
                 resourceType.equals("forecast_draft") || resourceType.equals("forecast_version"));
        if (!CurrentUser.hasPermission("audit:view") && !forecastLog && !scopedConfigLog) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该类日志");
        }

        page = Math.max(1, page);
        pageSize = Math.max(1, Math.min(100, pageSize));
        Map<String, String> filters = new java.util.HashMap<>();
        filters.put("keyword", keyword);
        filters.put("action", action);
        filters.put("resourceType", resourceType);
        filters.put("resourceId", resourceId);
        filters.put("startDate", startDate);
        filters.put("endDate", endDate);
        Long userId = CurrentUser.getUserId();
        long total = mapper.countVisible(userId, filters);
        List<Map<String, Object>> items = mapper.selectVisible(userId, filters, pageSize, (page - 1L) * pageSize);

        return ApiResponse.ok(Map.of(
                "items", items,
                "total", total,
                "page", page,
                "pageSize", pageSize
        ));
    }
}
