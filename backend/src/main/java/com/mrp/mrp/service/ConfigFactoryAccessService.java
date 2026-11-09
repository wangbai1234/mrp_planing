package com.mrp.mrp.service;

import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/** HTTP data scope only; snapshot creation retains its existing worker identity contract. */
@Service
public class ConfigFactoryAccessService {
    private final ConfigMasterMapper configs;
    private final MrpMaterialPolicyNoticeMapper scopes;
    private final FactoryMasterService factories;

    public ConfigFactoryAccessService(ConfigMasterMapper configs, MrpMaterialPolicyNoticeMapper scopes, FactoryMasterService factories) {
        this.configs = configs;
        this.scopes = scopes;
        this.factories = factories;
    }

    public List<String> visibleFactories() {
        Long userId = CurrentUser.getUserId();
        if (userId == null) return List.of();
        return factories.enabledCodes().stream()
                .filter(factory -> scopes.canReadFactory(userId, factory) != 0).toList();
    }

    public List<String> visibleEnabledCodes() { return visibleFactories(); }

    public List<String> visibleMasterCodes() {
        Long userId = CurrentUser.getUserId();
        if (userId == null) return List.of();
        return factories.list().stream().map(row -> (String) row.get("factoryCode"))
                .filter(factory -> scopes.canReadFactory(userId, factory) != 0).toList();
    }

    public void requireFactory(String factory) {
        Long userId = CurrentUser.getUserId();
        if (factory == null || factory.isBlank() || userId == null
                || !factories.enabled(factory.trim())
                || scopes.canReadFactory(userId, factory.trim()) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该工厂配置");
    }

    public void requireScope(String factory) {
        Long userId = CurrentUser.getUserId();
        if (factory == null || factory.isBlank() || userId == null
                || scopes.canReadFactory(userId, factory.trim()) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该工厂配置");
    }

    public List<FactoryWarehouseMapping> visibleMappings() {
        List<String> factories = visibleFactories();
        if (factories.isEmpty()) return List.of();
        return configs.selectFactoryWarehouseMappings().stream()
                .filter(row -> factories.contains(row.getFactoryCode())).toList();
    }

    public List<ProductionCalendarEntry> visibleCalendar(String factory) {
        if (factory != null && !factory.isBlank()) {
            requireFactory(factory);
            return configs.selectProductionCalendar(factory.trim());
        }
        // Never issue the unrestricted legacy query for a single-factory user.
        return visibleFactories().stream().flatMap(code -> configs.selectProductionCalendar(code).stream())
                .sorted(Comparator.comparing(ProductionCalendarEntry::getFactoryCode)
                        .thenComparing(ProductionCalendarEntry::getCalendarDate)).toList();
    }

    public void requireMapping(Long id) {
        FactoryWarehouseMapping row = configs.selectFactoryWarehouseMappingById(id);
        if (row == null) throw new IllegalStateException("工厂仓库映射不存在");
        requireFactory(row.getFactoryCode());
    }

    public void requireCalendar(Long id) {
        ProductionCalendarEntry row = configs.selectProductionCalendarById(id);
        if (row == null) throw new IllegalStateException("生产日历不存在");
        requireFactory(row.getFactoryCode());
    }

    public void requireBatch(ConfigMasterService.CalendarBatchRequest request) {
        requireFactory(request.factoryCode());
        if (request.copyFromFactory() != null && !request.copyFromFactory().isBlank())
            requireFactory(request.copyFromFactory());
    }

    public void requireMappings(List<FactoryWarehouseMapping> rows) {
        rows.stream().map(FactoryWarehouseMapping::getFactoryCode).distinct().forEach(this::requireFactory);
    }

    public void requireCalendars(List<ProductionCalendarEntry> rows) {
        rows.stream().map(ProductionCalendarEntry::getFactoryCode).distinct().forEach(this::requireFactory);
    }
}
