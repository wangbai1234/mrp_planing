package com.mrp.bom.controller;

import com.mrp.bom.domain.BomExplosionResult;
import com.mrp.bom.service.BomExplosionService;
import com.mrp.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bom")
public class BomExplosionController {

    private final BomExplosionService bomExplosionService;

    public BomExplosionController(BomExplosionService bomExplosionService) {
        this.bomExplosionService = bomExplosionService;
    }

    @PostMapping("/explode")
    public ResponseEntity<ApiResponse<BomExplosionResult>> explode(@RequestBody Map<String, Object> payload) {
        Long rootMaterialId = Long.valueOf(payload.get("rootMaterialId").toString());

        BigDecimal demandQuantity;
        Object qtyObj = payload.get("demandQuantity");
        if (qtyObj instanceof Number) {
            demandQuantity = BigDecimal.valueOf(((Number) qtyObj).doubleValue());
        } else {
            demandQuantity = new BigDecimal(qtyObj.toString());
        }

        @SuppressWarnings("unchecked")
        List<String> targetCategories = (List<String>) payload.get("targetCategories");

        BomExplosionResult result = bomExplosionService.explode(rootMaterialId, demandQuantity, targetCategories);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
