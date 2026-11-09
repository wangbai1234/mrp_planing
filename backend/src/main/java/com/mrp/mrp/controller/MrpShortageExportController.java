package com.mrp.mrp.controller;

import com.mrp.mrp.service.MrpShortageExportService;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/mrp/batches/{batchId}/shortage")
public class MrpShortageExportController {
    private final MrpShortageExportService exports;
    private final PlanningFactoryAccessService access;

    public MrpShortageExportController(MrpShortageExportService exports, PlanningFactoryAccessService access) {
        this.exports = exports;
        this.access = access;
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@PathVariable Long batchId,
            @RequestParam(required = false) String factoryCode,
            @RequestParam(required = false) LocalDate sourceMonth,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String rootMaterialCode) throws IOException {
        access.requireBatch(batchId);
        byte[] file = exports.export(batchId, factoryCode, sourceMonth, keyword, rootMaterialCode);
        if (file.length == 0) return ResponseEntity.noContent().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("物料汇总_" + batchId + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .body(file);
    }
}
