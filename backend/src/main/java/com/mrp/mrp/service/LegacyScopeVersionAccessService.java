package com.mrp.mrp.service;

import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.FactoryWarehouseMappingDetail;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.LegacyScopeVersionAccessMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** HTTP boundary only. Workers and immutable snapshot readers do not have an HTTP identity. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LegacyScopeVersionAccessService {
    private final LegacyScopeVersionAccessMapper access;
    private final Phase2ScopeConfigService service;

    public LegacyScopeVersionAccessService(LegacyScopeVersionAccessMapper access, Phase2ScopeConfigService service) {
        this.access = access;
        this.service = service;
    }

    public List<Map<String, Object>> mappings() { return access.mappingVersions(CurrentUser.getUserId()); }
    public List<Map<String, Object>> stocks() { return access.stockVersions(CurrentUser.getUserId()); }

    public List<FactoryWarehouseMappingDetail> mappingDetails(Long id) {
        requireMapping(id);
        return service.getFactoryWarehouseDetails(id);
    }

    public List<StockPolicyDetail> stockDetails(Long id) {
        requireStock(id);
        return service.getStockPolicyDetails(id);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> createMapping(LocalDate date) {
        // Empty global drafts do not persist an owning factory.
        requireAll();
        return service.createFactoryWarehouseVersion(date, CurrentUser.getUserId());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void saveMapping(Long id, List<FactoryWarehouseMappingDetail> details) {
        requireMapping(id);
        if (details != null) for (var detail : details) {
            String factory = detail.getFactoryCode();
            if (access.canUseFactory(factory == null ? null : factory.trim(), CurrentUser.getUserId()) == 0) denied();
        }
        service.saveFactoryWarehouseDetails(id, details);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void publishMapping(Long id) { requireMapping(id); service.publishFactoryWarehouseVersion(id); }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteMapping(Long id) { requireMapping(id); service.deleteFactoryWarehouseVersion(id); }

    // Legacy stock drafts have warehouse codes but no frozen factory mapping. Whole-version
    // replacement/creation could change an unprovable domain, so these global writes require ALL.
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> createStock(LocalDate date) {
        requireAll();
        return service.createStockPolicyVersion(date, CurrentUser.getUserId());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> incrementalStock(Long id, LocalDate date, List<StockPolicyDetail> overrides) {
        requireAll(); requireStock(id);
        return service.createIncrementalStockPolicyVersion(id, date, overrides, CurrentUser.getUserId());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void saveStock(Long id, List<StockPolicyDetail> details) {
        requireAll(); requireStock(id); service.saveStockPolicyDetails(id, details);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void publishStock(Long id) { requireAll(); requireStock(id); service.publishStockPolicyVersion(id); }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteStock(Long id) { requireAll(); requireStock(id); service.deleteStockPolicyVersion(id); }

    private void requireMapping(Long id) {
        if (access.canReadMapping(id, CurrentUser.getUserId()) == 0) denied();
    }
    private void requireStock(Long id) {
        if (access.canReadStock(id, CurrentUser.getUserId()) == 0) denied();
    }
    private void requireAll() { if (access.hasAllScope(CurrentUser.getUserId()) == 0) denied(); }
    private void denied() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该历史配置版本"); }
}
