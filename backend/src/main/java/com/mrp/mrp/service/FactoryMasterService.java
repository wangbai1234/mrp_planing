package com.mrp.mrp.service;

import com.mrp.audit.service.AuditService;
import com.mrp.common.security.CurrentUser;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.repository.FactoryMasterMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class FactoryMasterService {
    private final FactoryMasterMapper mapper;
    private final AuditService audit;

    public FactoryMasterService(FactoryMasterMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public record FactoryInput(String factoryCode, String factoryName, Boolean enabled) {}

    public List<Map<String, Object>> list() {
        return mapper.selectAll();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResult<Map<String, Object>> page(String keyword, String status, int page, int pageSize, List<String> visible) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        if (visible.isEmpty()) return new PageResult<>(List.of(), 0, safePage, safeSize);
        String query = keyword == null ? "" : keyword.trim();
        int total = mapper.countPage(visible, query, status);
        var rows = mapper.selectPage(visible, query, status, safeSize, (long) (safePage - 1) * safeSize);
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    public List<String> enabledCodes() {
        return mapper.selectEnabledCodes();
    }

    public boolean enabled(String code) {
        return code != null && mapper.countEnabled(code) > 0;
    }

    @Transactional
    public Map<String, Object> create(FactoryInput input) {
        String code = required(input.factoryCode(), "工厂编码", 32);
        String name = required(input.factoryName(), "工厂名称", 128);
        try {
            mapper.insert(code, name, input.enabled() == null || input.enabled(), CurrentUser.getUserId());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工厂编码已存在", ex);
        }
        Map<String, Object> after = byCode(code);
        audit.log(CurrentUser.getUserId(), null, code, "CREATE", "mrp_factory", code, null, after, null, null);
        return after;
    }

    @Transactional
    public Map<String, Object> update(String code, FactoryInput input) {
        Map<String, Object> before = byCode(code);
        if (input.factoryCode() != null && !code.equals(input.factoryCode().trim()))
            throw new IllegalArgumentException("工厂编码不可修改");
        String name = required(input.factoryName(), "工厂名称", 128);
        boolean enabled = input.enabled() == null || input.enabled();
        mapper.update(code, name, enabled, CurrentUser.getUserId());
        Map<String, Object> after = byCode(code);
        audit.log(CurrentUser.getUserId(), null, code, "UPDATE", "mrp_factory", code, before, after, null, null);
        return after;
    }

    @Transactional
    public void delete(String code) {
        Map<String, Object> before = byCode(code);
        if (mapper.countReferences(code) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "工厂已有业务引用，请停用而非删除");
        mapper.delete(code);
        audit.log(CurrentUser.getUserId(), null, code, "DELETE", "mrp_factory", code, before, null, null, null);
    }

    private Map<String, Object> byCode(String code) {
        Map<String, Object> result = mapper.selectByCode(code);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工厂不存在");
        return result;
    }

    private static String required(String value, String label, int limit) {
        if (value == null || value.isBlank() || value.trim().length() > limit)
            throw new IllegalArgumentException(label + "必填且不超过" + limit + "字符");
        return value.trim();
    }
}
