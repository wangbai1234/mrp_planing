package com.mrp.audit.controller;

import com.mrp.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        int offset = (page - 1) * pageSize;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (al.resource_id LIKE ? OR al.operator_id LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (action != null && !action.isEmpty()) {
            where.append(" AND al.action = ?");
            params.add(action);
        }
        if (resourceType != null && !resourceType.isEmpty()) {
            where.append(" AND al.resource_type = ?");
            params.add(resourceType);
        }
        if (startDate != null && !startDate.isEmpty()) {
            where.append(" AND al.created_at >= ?");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            where.append(" AND al.created_at <= ?");
            params.add(endDate + " 23:59:59");
        }

        String countSql = "SELECT COUNT(*) FROM audit_log al " + where;
        int total = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

        String querySql = """
            SELECT al.id, al.operator_id, al.operator_role, al.factory_scope,
                   al.action, al.resource_type, al.resource_id,
                   al.before_value, al.after_value,
                   al.trace_id, al.ip_address, al.created_at
            FROM audit_log al
            """ + where + " ORDER BY al.created_at DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> items = jdbcTemplate.queryForList(querySql, params.toArray());

        return ApiResponse.ok(Map.of(
                "items", items,
                "total", total,
                "page", page,
                "pageSize", pageSize
        ));
    }
}
