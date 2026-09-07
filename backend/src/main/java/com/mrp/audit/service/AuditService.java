package com.mrp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void log(Long operatorId, String operatorRole, String factoryScope,
                    String action, String resourceType, String resourceId,
                    Object beforeValue, Object afterValue, String traceId, String ipAddress) {
        try {
            String beforeJson = beforeValue != null ? objectMapper.writeValueAsString(beforeValue) : null;
            String afterJson = afterValue != null ? objectMapper.writeValueAsString(afterValue) : null;

            jdbcTemplate.update("""
                INSERT INTO audit_log (operator_id, operator_role, factory_scope, action,
                    resource_type, resource_id, before_value, after_value, trace_id, ip_address)
                VALUES (?, ?, ?, ?, ?, ?, ?::json, ?::json, ?, ?)
                """, operatorId, operatorRole, factoryScope, action,
                resourceType, resourceId, beforeJson, afterJson, traceId, ipAddress);

        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit values", e);
        }
    }
}
