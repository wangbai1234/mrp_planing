package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.mrp.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.mrp.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/mrp")
public class RollingPlanningController {
    private final RollingPlanningQueryService query;
    private final LinkedDailyTransferService transfer;
    public RollingPlanningController(RollingPlanningQueryService query,LinkedDailyTransferService transfer){this.query=query;this.transfer=transfer;}
    @GetMapping("/batches/{batchId}/rolling-grid")
    public ApiResponse<MrpDailyPlanningController.DailyGridResponse> grid(@PathVariable Long batchId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String factoryCode,@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String rootMaterialCode,
            @RequestParam(defaultValue="MACHINE_OUTPUT") String view) {
        requireView(view);
        return ApiResponse.ok(query.grid(batchId,page,pageSize,factoryCode,keyword,rootMaterialCode,view,true));
    }
    @GetMapping({"/batches/{batchId}/rolling-details","/plans/{batchId}/daily-details"})
    public ApiResponse<RollingPlanningQueryService.DetailPage> details(@PathVariable Long batchId,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String factoryCode,@RequestParam(required=false) String rootMaterialCode,
            @RequestParam(required=false) String terminalMaterialCode,@RequestParam(required=false) String terminalType,
            @RequestParam(required=false) String view,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate sourceMonth,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate scheduleDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        requireView(view);
        return ApiResponse.ok(query.details(batchId,page,pageSize,factoryCode,rootMaterialCode,terminalMaterialCode,
                "MACHINE_OUTPUT".equals(view)?"MACHINE_OUTPUT":terminalType,sourceMonth,startDate==null?scheduleDate:startDate,endDate==null?scheduleDate:endDate));
    }
    @PostMapping("/batches/{batchId}/linked-transfers/preview")
    public ApiResponse<LinkedDailyTransferService.Preview> preview(@PathVariable Long batchId,@RequestBody LinkedDailyTransferService.Input input){return ApiResponse.ok(transfer.preview(batchId,input));}
    @PostMapping("/batches/{batchId}/linked-transfers")
    public ApiResponse<Void> commit(@PathVariable Long batchId,@RequestBody LinkedDailyTransferService.Commit input){transfer.commit(batchId,input);return ApiResponse.ok(null);}
    private void requireView(String view) {
        if ("TERMINAL".equals(view) || view == null) {
            if (!CurrentUser.hasPermission("mrp_shortage:view"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权查看物料明细");
        } else if ("MACHINE_OUTPUT".equals(view)) {
            if (!CurrentUser.hasPermission("mrp_workspace:view"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权查看整机排产");
        } else throw new IllegalArgumentException("排产视图不合法");
    }
}
