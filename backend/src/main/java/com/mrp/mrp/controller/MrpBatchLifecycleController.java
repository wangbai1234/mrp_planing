package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.service.BatchOrchestratorService;
import com.mrp.mrp.service.DecisionService;
import com.mrp.mrp.service.MrpLifecycleTaskService;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import com.mrp.common.response.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mrp/batches")
public class MrpBatchLifecycleController {

    private final BatchOrchestratorService orchestrator;
    private final DecisionService decisionService;
    private final MrpDecisionIssueMapper issueMapper;
    private final com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess;
    private final CalcTaskMapper calcTaskMapper;
    private final MrpLifecycleTaskService lifecycleTasks;
    private final com.mrp.mrp.service.PlanningCalendarValidationService calendarValidation;

    public MrpBatchLifecycleController(BatchOrchestratorService orchestrator, DecisionService decisionService,
                                        MrpDecisionIssueMapper issueMapper, com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess,
                                        CalcTaskMapper calcTaskMapper, MrpLifecycleTaskService lifecycleTasks, com.mrp.mrp.service.PlanningCalendarValidationService calendarValidation) {
        this.calendarValidation = calendarValidation;
        this.factoryAccess = factoryAccess;
        this.orchestrator = orchestrator;
        this.decisionService = decisionService;
        this.issueMapper = issueMapper;
        this.calcTaskMapper = calcTaskMapper;
        this.lifecycleTasks = lifecycleTasks;
    }

    @GetMapping("/{batchId}/summary")
    public ResponseEntity<ApiResponse<BatchOrchestratorService.BatchSummary>> getSummary(@PathVariable Long batchId) {
        BatchOrchestratorService.BatchSummary summary = orchestrator.getBatchSummary(batchId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @PostMapping("/{batchId}/precheck")
    public ResponseEntity<ApiResponse<MrpCalcBatch>> precheck(@PathVariable Long batchId) {
        MrpCalcBatch batch = orchestrator.precheck(batchId);
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    @PostMapping("/{batchId}/calculate")
    public ResponseEntity<ApiResponse<CalcTask>> calculate(@PathVariable Long batchId) {
        CalcTask task = orchestrator.submitCalculation(batchId, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @PostMapping("/{batchId}/material-analysis")
    public ResponseEntity<ApiResponse<CalcTask>> materialAnalysis(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                orchestrator.submitMaterialAnalysis(batchId, CurrentUser.getUserId())));
    }

    public record CalculationProgress(Long taskId, String status, Integer progressCurrent,
                                      Integer progressTotal, String phase, String errorMessage) {}

    @GetMapping("/{batchId}/calculation-task")
    public ResponseEntity<ApiResponse<CalculationProgress>> calculationProgress(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        CalcTask task = calcTaskMapper.selectLatestCalculationForBatch(batchId);
        if (task == null) return ResponseEntity.ok(ApiResponse.ok(null));
        return ResponseEntity.ok(ApiResponse.ok(new CalculationProgress(task.getId(), task.getStatus(),
                task.getProgressCurrent(), task.getProgressTotal(), task.getPhase(), task.getErrorMessage())));
    }

    public record PublishRequest(String capacityFingerprint) {}

    @PostMapping("/{batchId}/publish")
    public ResponseEntity<ApiResponse<CalculationProgress>> publish(@PathVariable Long batchId, @RequestBody(required = false) PublishRequest request) {
        factoryAccess.requireBatch(batchId);
        CalcTask task = lifecycleTasks.submit(batchId, MrpLifecycleTaskService.PUBLISH,
                request == null ? null : request.capacityFingerprint(), CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(progress(task)));
    }

    @GetMapping("/{batchId}/capacity-review")
    public ResponseEntity<ApiResponse<BatchOrchestratorService.CapacityReview>> capacityReview(@PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(orchestrator.capacityReview(batchId)));
    }

    @GetMapping("/{batchId}/calendar-check")
    public ResponseEntity<ApiResponse<com.mrp.mrp.service.PlanningCalendarValidationService.CalendarCheck>> calendarCheck(
            @PathVariable Long batchId, @RequestParam(defaultValue = "false") boolean latest) {
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(calendarValidation.checkBatch(batchId, latest)));
    }

    @PostMapping("/{batchId}/revise")
    public ResponseEntity<ApiResponse<CalculationProgress>> revise(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        CalcTask task = lifecycleTasks.submit(batchId, MrpLifecycleTaskService.REVISION, null, CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(progress(task)));
    }

    public record LifecycleProgress(Long taskId, String taskType, String status, Integer progressCurrent,
                                    Integer progressTotal, String phase, String errorMessage, Long resultBatchId) {}

    @GetMapping("/{batchId}/lifecycle-task")
    public ResponseEntity<ApiResponse<LifecycleProgress>> lifecycleProgress(@PathVariable Long batchId,
            @RequestParam String kind) {
        factoryAccess.requireBatch(batchId);
        String taskType = switch (kind) {
            case "publish" -> MrpLifecycleTaskService.PUBLISH;
            case "revise" -> MrpLifecycleTaskService.REVISION;
            default -> throw new IllegalArgumentException("未知任务类型");
        };
        CalcTask task = lifecycleTasks.latest(batchId, taskType);
        return ResponseEntity.ok(ApiResponse.ok(task == null ? null : new LifecycleProgress(task.getId(),
                task.getTaskType(), task.getStatus(), task.getProgressCurrent(), task.getProgressTotal(),
                task.getPhase(), task.getErrorMessage(), task.getResultResourceId())));
    }

    private CalculationProgress progress(CalcTask task) {
        return new CalculationProgress(task.getId(), task.getStatus(), task.getProgressCurrent(),
                task.getProgressTotal(), task.getPhase(), task.getErrorMessage());
    }

    @PostMapping("/{batchId}/transition")
    public ResponseEntity<ApiResponse<MrpCalcBatch>> transition(
            @PathVariable Long batchId,
            @RequestParam String from,
            @RequestParam String to) {
        MrpCalcBatch batch = orchestrator.transitionStatus(batchId, from, to);
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    @PostMapping("/{batchId}/issues")
    public ResponseEntity<ApiResponse<MrpDecisionIssue>> createIssue(
            @PathVariable Long batchId,
            @RequestParam String issueKey,
            @RequestParam String issueType,
            @RequestParam String severity,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeKey,
            @RequestParam(required = false) String payloadJson) {
        MrpDecisionIssue issue = decisionService.createIssue(batchId, issueKey, issueType, severity, scopeType, scopeKey, payloadJson);
        return ResponseEntity.ok(ApiResponse.ok(issue));
    }

    @PostMapping("/issues/{issueId}/resolve")
    public ResponseEntity<ApiResponse<MrpDecisionRecord>> resolveIssue(
            @PathVariable Long issueId,
            @RequestParam String decisionCode,
            @RequestParam(required = false) String decisionPayload,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Integer expectedRevision) {
        MrpDecisionRecord record = decisionService.recordDecision(issueId, decisionCode, decisionPayload, reason, CurrentUser.getUserId(), expectedRevision);
        return ResponseEntity.ok(ApiResponse.ok(record));
    }

    public record BatchInventoryConfirmation(List<Long> issueIds, String reason) {}

    @PostMapping("/{batchId}/issues/inventory-missing/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveMissingInventory(
            @PathVariable Long batchId, @RequestBody BatchInventoryConfirmation request) {
        factoryAccess.requireBatch(batchId);
        if (request == null) throw new IllegalArgumentException("请选择缺库存问题");
        decisionService.batchResolveIssues(batchId, request.issueIds(), "MANUAL_CONFIRM",
                request.reason(), CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{batchId}/issues")
    public ResponseEntity<ApiResponse<List<MrpDecisionIssue>>> getIssues(
            @PathVariable Long batchId,
            @RequestParam(required = false) String status) {
        List<MrpDecisionIssue> issues;
        if (status != null) {
            issues = issueMapper.selectByBatchIdAndStatus(batchId, status);
        } else {
            issues = issueMapper.selectByBatchId(batchId);
        }
        return ResponseEntity.ok(ApiResponse.ok(issues));
    }

    @GetMapping("/{batchId}/issues/page")
    public ResponseEntity<ApiResponse<PageResult<MrpDecisionIssue>>> getIssuesPage(@PathVariable Long batchId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        int total = issueMapper.countByBatchId(batchId, status, keyword);
        return ResponseEntity.ok(ApiResponse.ok(new PageResult<>(issueMapper.selectPageByBatchId(batchId, status, keyword,
                (long) (page - 1) * pageSize, pageSize), total, page, pageSize)));
    }

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<MrpCalcBatch>>> getBatches(
            @RequestParam(required = false) String status) {
        List<MrpCalcBatch> batches = factoryAccess.visible(status, null, false, null, null);
        return ResponseEntity.ok(ApiResponse.ok(batches));
    }
}
