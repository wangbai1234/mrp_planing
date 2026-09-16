package com.mrp.shipment.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.shipment.domain.ShipmentBatch;
import com.mrp.shipment.domain.ShipmentDetail;
import com.mrp.shipment.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping("/shipment-imports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importShipment(
            @RequestBody List<ShipmentDetail> details) {
        Long userId = CurrentUser.getUserId();
        ShipmentBatch batch = shipmentService.importShipment(details, userId);
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
            "batchId", batch.id(),
            "status", batch.status(),
            "recordCount", details.size()
        )));
    }

    @GetMapping("/shipments/latest")
    public ResponseEntity<ApiResponse<ShipmentBatch>> getLatestBatch() {
        ShipmentBatch batch = shipmentService.getLatestBatch();
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    @GetMapping("/shipments/{batchId}/details")
    public ResponseEntity<ApiResponse<List<ShipmentDetail>>> getDetails(
            @PathVariable Long batchId) {
        List<ShipmentDetail> details = shipmentService.getDetailsByBatchId(batchId);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/shipments/shipped-qty")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getShippedQty(
            @RequestParam String materialId,
            @RequestParam LocalDate planMonth) {
        Long qty = shipmentService.getShippedQty(materialId, planMonth);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "materialId", materialId,
            "planMonth", planMonth,
            "shippedQty", qty
        )));
    }
}
