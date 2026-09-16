package com.mrp.shipment.service;

import com.mrp.shipment.domain.ShipmentBatch;
import com.mrp.shipment.domain.ShipmentDetail;
import com.mrp.shipment.repository.ShipmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentMapper shipmentMapper;

    public ShipmentService(ShipmentMapper shipmentMapper) {
        this.shipmentMapper = shipmentMapper;
    }

    @Transactional
    public ShipmentBatch importShipment(List<ShipmentDetail> details, Long userId) {
        ShipmentBatch batch = new ShipmentBatch(
            null, ShipmentBatch.SOURCE_IMPORT, ShipmentBatch.STATUS_IMPORTED,
            userId, java.time.Instant.now()
        );
        shipmentMapper.insertBatch(batch);
        Long generatedId = shipmentMapper.selectLastInsertId();
        batch = new ShipmentBatch(
            generatedId, batch.source(), batch.status(),
            batch.createdBy(), batch.createdAt()
        );

        List<ShipmentDetail> batchDetails = new ArrayList<>();
        for (ShipmentDetail detail : details) {
            batchDetails.add(new ShipmentDetail(
                null, batch.id(), detail.materialId(),
                detail.planMonth(), detail.shippedQty()
            ));
        }
        shipmentMapper.insertDetails(batchDetails);

        return batch;
    }

    public Long getShippedQty(String materialId, LocalDate planMonth) {
        ShipmentBatch latest = shipmentMapper.selectLatestBatch();
        if (latest == null) return 0L;

        return shipmentMapper.selectDetailsByBatchId(latest.id()).stream()
            .filter(d -> d.materialId().equals(materialId))
            .filter(d -> d.planMonth().equals(planMonth))
            .mapToLong(ShipmentDetail::shippedQty)
            .sum();
    }

    public List<ShipmentDetail> getDetailsByBatchId(Long batchId) {
        return shipmentMapper.selectDetailsByBatchId(batchId);
    }

    public ShipmentBatch getLatestBatch() {
        return shipmentMapper.selectLatestBatch();
    }
}
