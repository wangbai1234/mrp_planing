package com.mrp.bom.controller;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomMaterial;
import com.mrp.bom.domain.BomTreeNodeV2;
import com.mrp.bom.service.BomService;
import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bom")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    @GetMapping("/materials")
    public ResponseEntity<ApiResponse<PageResult<BomMaterial>>> getMaterialPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<BomMaterial> result = bomService.getMaterialPage(keyword, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/materials/{invCode}")
    public ResponseEntity<ApiResponse<BomMaterial>> getMaterialByCode(@PathVariable String invCode) {
        BomMaterial material = bomService.getMaterialByCode(invCode);
        if (material == null) {
            return ResponseEntity.ok(ApiResponse.error("NOT_FOUND", "物料不存在"));
        }
        return ResponseEntity.ok(ApiResponse.ok(material));
    }

    @GetMapping("/detail/{invCode}")
    public ResponseEntity<ApiResponse<List<BomDetail>>> getDetailByParentCode(@PathVariable String invCode) {
        List<BomDetail> details = bomService.getDetailByParentCode(invCode);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/tree/{invCode}")
    public ResponseEntity<ApiResponse<List<BomTreeNodeV2>>> getTreeChildren(@PathVariable String invCode) {
        List<BomTreeNodeV2> children = bomService.getTreeChildren(invCode);
        return ResponseEntity.ok(ApiResponse.ok(children));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncFromOracle() {
        Map<String, Object> result = bomService.syncFromOracle();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        int count = bomService.getLocalCount();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("localCount", count)));
    }
}
