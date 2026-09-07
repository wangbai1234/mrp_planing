package com.mrp.inventory.domain;

public record InventoryDetail(
        Long id,
        Long snapshotId,
        String factoryCode,
        String materialId,
        String warehouseCode,
        String warehouseName,
        String warehouseType,
        Long quantity,
        Boolean isInCalculation
) {}
