package com.mrp.capacity.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.NotFoundException;
import com.mrp.common.exception.ValidationException;
import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.domain.CapacityVersion;
import com.mrp.capacity.repository.CapacityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CapacityService {

    private final CapacityMapper capacityMapper;

    public CapacityService(CapacityMapper capacityMapper) {
        this.capacityMapper = capacityMapper;
    }

    public List<CapacityLine> listLines() {
        CapacityVersion version = capacityMapper.selectActiveVersion();
        if (version == null) return List.of();
        return capacityMapper.selectLinesByVersionId(version.id());
    }

    public List<CapacityLine> listLinesByFactory(String factoryCode) {
        CapacityVersion version = capacityMapper.selectActiveVersion();
        if (version == null) return List.of();
        return capacityMapper.selectActiveLinesByFactory(version.id(), factoryCode);
    }

    @Transactional
    public CapacityLine createLine(CapacityLine line) {
        validateLine(line);

        CapacityVersion version = capacityMapper.selectActiveVersion();
        if (version == null) {
            version = new CapacityVersion(null, 1, CapacityVersion.STATUS_ACTIVE, null, null);
            capacityMapper.insertVersion(version);
        }

        // Check duplicate line_code in same factory
        List<CapacityLine> existing = capacityMapper.selectActiveLinesByFactory(version.id(), line.factoryCode());
        boolean duplicate = existing.stream().anyMatch(l -> l.lineCode().equals(line.lineCode()));
        if (duplicate) {
            throw new ValidationException("产线编码 " + line.lineCode() + " 在工厂 " + line.factoryCode() + " 已存在");
        }

        CapacityLine newLine = new CapacityLine(
                null, version.id(), line.factoryCode(), line.lineCode(),
                line.lineName(), line.weeklyCapacity(), line.effectiveDate(),
                line.isActive(), line.remark()
        );
        capacityMapper.insertLine(newLine);
        return newLine;
    }

    @Transactional
    public CapacityLine updateLine(Long id, CapacityLine line) {
        validateLine(line);

        CapacityLine existing = capacityMapper.selectLineById(id);
        if (existing == null) {
            throw new NotFoundException("CapacityLine", id);
        }

        CapacityLine updated = new CapacityLine(
                id, existing.versionId(), existing.factoryCode(), existing.lineCode(),
                line.lineName(), line.weeklyCapacity(), line.effectiveDate(),
                line.isActive(), line.remark()
        );
        capacityMapper.updateLine(updated);
        return updated;
    }

    private void validateLine(CapacityLine line) {
        if (line.factoryCode() == null || line.factoryCode().isBlank()) {
            throw new ValidationException("工厂不能为空");
        }
        if (line.lineCode() == null || line.lineCode().isBlank()) {
            throw new ValidationException("产线编码不能为空");
        }
        if (line.weeklyCapacity() == null || line.weeklyCapacity() <= 0) {
            throw new ValidationException("周产能必须大于0");
        }
    }
}
