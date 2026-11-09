package com.mrp.mrp.service;

import com.mrp.mrp.domain.MaterialPolicyDetail;
import com.mrp.mrp.repository.MaterialPolicyDetailMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MaterialPolicyVersionService {

    private final MaterialPolicyDetailMapper detailMapper;

    public MaterialPolicyVersionService(MaterialPolicyDetailMapper detailMapper) {
        this.detailMapper = detailMapper;
    }

    public Map<String, MaterialPolicyDetail> getDetailMap(Long versionId) {
        if (versionId == null) {
            return Map.of();
        }
        List<MaterialPolicyDetail> details = detailMapper.selectByVersionId(versionId);
        return details.stream()
                .collect(Collectors.toMap(MaterialPolicyDetail::getMaterialCode, d -> d, (a, b) -> a));
    }
}
