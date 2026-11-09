package com.mrp.mrp.controller;

import com.mrp.mrp.service.MrpDailyPlanExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/mrp/plans")
public class MrpDailyPlanExportController {
    private final MrpDailyPlanExportService exportService;

    public MrpDailyPlanExportController(MrpDailyPlanExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(value = "/{batchId}/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@PathVariable Long batchId, @RequestParam(defaultValue="MACHINE_OUTPUT") String view) throws java.io.IOException {
        byte[] file = exportService.export(batchId, view);
        if (file.length == 0) return ResponseEntity.noContent().build();
        String filename = ("TERMINAL".equals(view) ? "物料缺料_" : "整机排产_") + batchId + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }
}
