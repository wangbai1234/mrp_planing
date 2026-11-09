package com.mrp.forecast.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.forecast.service.ForecastWorkspaceService;
import com.mrp.forecast.service.ForecastWorkspaceService.LineInput;
import com.mrp.forecast.service.ForecastWorkspaceService.Mutation;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forecast-workspace")
public class ForecastWorkspaceController {
    private final ForecastWorkspaceService service;
    public ForecastWorkspaceController(ForecastWorkspaceService service) { this.service=service; }
    @GetMapping("/current")
    public ApiResponse<Map<String,Object>> current() { return ApiResponse.ok(service.current()); }

    @GetMapping("/materials/page")
    public ApiResponse<PageResult<Map<String,String>>> materials(
            @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int pageSize) {
        return ApiResponse.ok(service.searchMaterials(keyword, Math.max(1,page), Math.max(1,Math.min(pageSize,100))));
    }

    @GetMapping("/versions/{versionId}/metadata")
    public ApiResponse<Map<String,Object>> versionInfo(@PathVariable Long versionId) { return ApiResponse.ok(service.versionInfo(versionId)); }

    @GetMapping("/draft")
    public ApiResponse<Map<String,Object>> draft() { return ApiResponse.ok(service.activeDraft()); }

    @PostMapping("/draft")
    public ApiResponse<Map<String,Object>> create() { return ApiResponse.ok(service.createDraft()); }

    @GetMapping("/draft/{draftId}/lines")
    public ApiResponse<PageResult<Map<String,Object>>> lines(@PathVariable Long draftId,
            @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.lines(draftId,keyword,Math.max(1,page),Math.max(1,Math.min(pageSize,100))));
    }

    @GetMapping("/draft/{draftId}/line")
    public ApiResponse<Map<String,Object>> line(@PathVariable Long draftId, @RequestParam String factoryCode, @RequestParam String materialId) {
        return ApiResponse.ok(service.lineByKey(draftId,factoryCode,materialId));
    }

    @GetMapping("/draft/{draftId}/deleted-line")
    public ApiResponse<Map<String,Object>> deletedLine(@PathVariable Long draftId, @RequestParam String factoryCode, @RequestParam String materialId) {
        return ApiResponse.ok(service.deletedLineByKey(draftId,factoryCode,materialId));
    }

    @PostMapping("/draft/{draftId}/lines")
    public ApiResponse<Map<String,Object>> add(@PathVariable Long draftId,@RequestBody LineInput input) {
        return ApiResponse.ok(service.add(draftId,input));
    }

    @PutMapping("/draft/{draftId}/lines/{lineId}")
    public ApiResponse<Map<String,Object>> edit(@PathVariable Long draftId,@PathVariable Long lineId,@RequestBody LineInput input) {
        return ApiResponse.ok(service.edit(draftId,lineId,input));
    }

    @PostMapping("/draft/{draftId}/lines/{lineId}/delete")
    public ApiResponse<Map<String,Object>> delete(@PathVariable Long draftId,@PathVariable Long lineId,@RequestBody Mutation request) {
        return ApiResponse.ok(service.delete(draftId,lineId,request));
    }

    @PostMapping("/draft/{draftId}/publish")
    public ApiResponse<Map<String,Object>> publish(@PathVariable Long draftId,@RequestBody Mutation request) {
        return ApiResponse.ok(service.publish(draftId,request));
    }

    @PostMapping("/draft/{draftId}/discard")
    public ApiResponse<Boolean> discard(@PathVariable Long draftId,@RequestBody Mutation request) {
        service.discard(draftId,request);
        return ApiResponse.ok(true);
    }

    @GetMapping("/compare")
    public ApiResponse<PageResult<Map<String,Object>>> compare(@RequestParam Long oldId,@RequestParam Long newId,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String factoryCode,@RequestParam(defaultValue="true") boolean changesOnly,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.compare(oldId,newId,keyword,factoryCode,changesOnly,Math.max(1,page),Math.max(1,Math.min(pageSize,100))));
    }

    @GetMapping("/draft/{draftId}/compare")
    public ApiResponse<PageResult<Map<String,Object>>> compareDraft(@PathVariable Long draftId,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String factoryCode,@RequestParam(defaultValue="true") boolean changesOnly,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.compareDraft(draftId,keyword,factoryCode,changesOnly,Math.max(1,page),Math.max(1,Math.min(pageSize,100))));
    }

    @GetMapping("/draft/{draftId}/summary")
    public ApiResponse<Map<String,Object>> draftSummary(@PathVariable Long draftId) { return ApiResponse.ok(service.draftSummary(draftId)); }

    @GetMapping("/history")
    public ApiResponse<PageResult<Map<String,Object>>> history(@RequestParam String factoryCode,@RequestParam String materialId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.history(factoryCode,materialId,Math.max(1,page),Math.max(1,Math.min(pageSize,100))));
    }
}
