package com.mrp.config;

import com.mrp.mrp.service.PlanningFactoryAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import java.util.Objects;

/** Resolve indirect IDs before a controller can read or mutate planning data. */
@Component
public class PlanningFactoryInterceptor implements HandlerInterceptor {
    private final PlanningFactoryAccessService access;
    public PlanningFactoryInterceptor(PlanningFactoryAccessService access) { this.access = access; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) return true;
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        Long batchId = id(variables.get("batchId"));
        if (uri.startsWith("/api/v1/mrp/batches/") && variables.containsKey("id")) batchId = id(variables.get("id"));
        if (uri.startsWith("/api/v1/mrp/plans/") && variables.containsKey("planId")) batchId = id(variables.get("planId"));
        if (batchId == null && uri.startsWith("/api/v1/mrp/inventory/")) batchId = id(request.getParameter("batchId"));
        if (batchId != null) access.requireBatch(batchId);
        for (String resource : new String[]{"issue", "override", "pool", "allocation"}) {
            Object value = variables.get(resource + "Id");
            if (value == null && uri.startsWith("/api/v1/mrp/inventory/")) value = request.getParameter(resource + "Id");
            if (value != null) {
                Long owner = access.requireResource(resource, id(value));
                if (batchId != null && !Objects.equals(owner, batchId)) forbidden();
                batchId = owner;
            }
        }
        String runId = request.getParameter("runId");
        if (runId != null && batchId != null && !Objects.equals(access.requireResource("run", id(runId)), batchId)) forbidden();
        if (uri.equals("/api/v1/mrp/batches") && "POST".equals(request.getMethod())) {
            String factory = request.getParameter("factoryCode");
            if (factory != null && !factory.isBlank()) access.requireFactory(factory.trim());
        }
        return true;
    }

    private static Long id(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("资源编号须为整数"); }
    }
    private static void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "排产资源不属于当前批次"); }
}
