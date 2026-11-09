package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.domain.MrpMaterialFeedback;
import com.mrp.mrp.service.MrpWorkbenchReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mrp/batches/{batchId}")
public class MrpWorkbenchReviewController {
    private final MrpWorkbenchReviewService reviews;
    public MrpWorkbenchReviewController(MrpWorkbenchReviewService reviews){this.reviews=reviews;}
    public record Confirmation(String fingerprint) {}
    public record Response(String response) {}

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<MrpWorkbenchReviewService.Status>> status(@PathVariable Long batchId){
        return ResponseEntity.ok(ApiResponse.ok(reviews.status(batchId)));
    }
    @PostMapping("/schedule-confirmation")
    public ResponseEntity<ApiResponse<MrpWorkbenchReviewService.Status>> schedule(@PathVariable Long batchId,
            @RequestBody Confirmation input){
        return ResponseEntity.ok(ApiResponse.ok(reviews.confirm(batchId,"SCHEDULE",input.fingerprint())));
    }
    @PostMapping("/material-confirmation")
    public ResponseEntity<ApiResponse<MrpWorkbenchReviewService.Status>> material(@PathVariable Long batchId,
            @RequestBody Confirmation input){
        return ResponseEntity.ok(ApiResponse.ok(reviews.confirm(batchId,"MATERIAL",input.fingerprint())));
    }
    @GetMapping("/material-feedback/page")
    public ResponseEntity<ApiResponse<PageResult<MrpMaterialFeedback>>> feedback(@PathVariable Long batchId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
        return ResponseEntity.ok(ApiResponse.ok(reviews.feedbackPage(batchId,page,pageSize)));
    }
    @PostMapping("/material-feedback")
    public ResponseEntity<ApiResponse<MrpMaterialFeedback>> create(@PathVariable Long batchId,
            @RequestBody MrpMaterialFeedback input){
        return ResponseEntity.ok(ApiResponse.ok(reviews.createFeedback(batchId,input)));
    }
    @PostMapping("/material-feedback/{id}/respond")
    public ResponseEntity<ApiResponse<MrpMaterialFeedback>> respond(@PathVariable Long batchId,
            @PathVariable Long id,@RequestBody Response input){
        return ResponseEntity.ok(ApiResponse.ok(reviews.respond(batchId,id,input.response())));
    }
}
