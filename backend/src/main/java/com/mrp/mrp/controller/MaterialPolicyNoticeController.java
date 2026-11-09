package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.domain.MrpMaterialPolicyNotice;
import com.mrp.mrp.service.MaterialPolicyNoticeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mrp/batches")
public class MaterialPolicyNoticeController {
    private final MaterialPolicyNoticeService service;
    public MaterialPolicyNoticeController(MaterialPolicyNoticeService service) { this.service = service; }
    @GetMapping("/{batchId}/material-policy-notices")
    public ApiResponse<PageResult<MrpMaterialPolicyNotice>> page(@PathVariable Long batchId,
            @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="") String missingType,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.page(batchId, keyword, missingType, page, pageSize));
    }
    @GetMapping("/{batchId}/material-policy-notices/pending-default-count")
    public ApiResponse<Integer> pendingDefaultCount(@PathVariable Long batchId) {
        return ApiResponse.ok(service.pendingDefaultExplosionCount(batchId));
    }
    @PostMapping("/{batchId}/material-policy-notices/confirm-default-explosion")
    public ApiResponse<Integer> confirmDefaultExplosion(@PathVariable Long batchId) {
        return ApiResponse.ok(service.confirmDefaultExplosion(batchId));
    }
}
