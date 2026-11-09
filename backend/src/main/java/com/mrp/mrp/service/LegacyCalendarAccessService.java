package com.mrp.mrp.service;

import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.ProductionCalendarDay;
import com.mrp.mrp.domain.ProductionCalendarVersion;
import com.mrp.mrp.repository.ProductionCalendarVersionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** HTTP authorization only; immutable snapshot and worker reads retain their existing contract. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class LegacyCalendarAccessService {
    private final ProductionCalendarVersionMapper versions;
    private final MrpConfigurationService service;

    public LegacyCalendarAccessService(ProductionCalendarVersionMapper versions, MrpConfigurationService service) {
        this.versions = versions;
        this.service = service;
    }

    public List<ProductionCalendarVersion> list() { return versions.selectVisible(CurrentUser.getUserId()); }

    public ProductionCalendarVersion get(Long id) {
        requireAccess(id);
        return versions.selectById(id);
    }

    public List<ProductionCalendarDay> days(Long id) {
        requireAccess(id);
        return service.getProductionCalendarDays(id);
    }

    public MrpConfigurationService.BatchCreationVersions batchCreationVersions() {
        return new MrpConfigurationService.BatchCreationVersions(
                service.getCategoryRoleVersions(), service.getMaterialPolicyVersions(), list());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ProductionCalendarVersion create(String factory) {
        if (versions.canUseFactory(factory, CurrentUser.getUserId()) == 0) denied();
        return service.createProductionCalendarVersion(factory, CurrentUser.getUserId());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void save(Long id, List<ProductionCalendarDay> days) {
        requireWrite(id);
        service.saveProductionCalendarDays(id, days);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void publish(Long id) {
        requireWrite(id);
        service.publishProductionCalendarVersion(id);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void delete(Long id) {
        requireWrite(id);
        service.deleteProductionCalendarVersion(id);
    }

    private void requireAccess(Long id) {
        if (versions.canAccess(id, CurrentUser.getUserId()) == 0) denied();
    }

    private void requireWrite(Long id) {
        requireAccess(id);
        // Legacy saves force every day to the header factory, including ALL.
        // All-factory reads do not confer permission to perform that replacement.
        if ("ALL".equals(versions.selectById(id).getFactoryCode())
                && versions.hasAllScope(CurrentUser.getUserId()) == 0) denied();
    }

    private void denied() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该生产日历版本");
    }
}
