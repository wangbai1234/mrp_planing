package com.mrp.shipment.domain;

import java.time.LocalDate;

public record ShipmentDetail(
        Long id,
        Long batchId,
        String materialId,
        LocalDate planMonth,
        Long shippedQty
) {}
