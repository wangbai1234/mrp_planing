package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpDailyExecution;
import com.mrp.mrp.service.MrpExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mrp/batches/{batchId}/execution")
public class MrpExecutionController {

    private final MrpExecutionService executionService;

    public MrpExecutionController(MrpExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<?>> confirmExecution(
            @PathVariable Long batchId,
            @RequestParam String rowKey,
            @RequestParam BigDecimal actualQty,
            @RequestParam(required = false) String remark) {
        executionService.confirmExecution(
                batchId, rowKey, actualQty,
                CurrentUser.getUserId(), remark);
        return ResponseEntity.ok(ApiResponse.ok("Execution confirmed"));
    }

    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<?>> lockDay(
            @PathVariable Long batchId,
            @RequestParam LocalDate scheduleDate) {
        executionService.lockDay(batchId, scheduleDate);
        return ResponseEntity.ok(ApiResponse.ok("Day locked"));
    }

    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<?>> unlockDay(
            @PathVariable Long batchId,
            @RequestParam LocalDate scheduleDate) {
        executionService.unlockDay(batchId, scheduleDate);
        return ResponseEntity.ok(ApiResponse.ok("Day unlocked"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionStatus(
            @PathVariable Long batchId) {
        Map<String, Object> status = executionService.getExecutionStatus(batchId);
        return ResponseEntity.ok(ApiResponse.ok(status));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResult<MrpDailyExecution>>> getExecutionPage(
            @PathVariable Long batchId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate scheduleDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(
                executionService.getExecutionPage(batchId, keyword, scheduleDate, page, pageSize)));
    }
}
