package com.mrp.capacity.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.service.CapacityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/capacity-lines")
public class CapacityController {

    private final CapacityService capacityService;

    public CapacityController(CapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CapacityLine>>> list(
            @RequestParam(required = false) String factoryCode) {
        List<CapacityLine> lines = (factoryCode != null)
                ? capacityService.listLinesByFactory(factoryCode)
                : capacityService.listLines();
        return ResponseEntity.ok(ApiResponse.ok(lines));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CapacityLine>> create(@RequestBody CapacityLine line) {
        return ResponseEntity.ok(ApiResponse.ok(capacityService.createLine(line)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CapacityLine>> update(@PathVariable Long id, @RequestBody CapacityLine line) {
        return ResponseEntity.ok(ApiResponse.ok(capacityService.updateLine(id, line)));
    }
}
