package com.mrp.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryItem(
        Long id,
        Long snapshotId,
        String productLine,
        String inventoryCategory,
        String materialId,
        String inventoryQuality,
        String warehouseCode,
        String warehouseName,
        String projectModel,
        String materialName,
        String supplierName,
        String productMode,
        BigDecimal odmSupplierQty,
        BigDecimal xa400Qty,
        BigDecimal xa378Qty,
        BigDecimal xa226Qty,
        BigDecimal shippingAvailableQty,
        String classification,
        String barcode,
        String remark,
        BigDecimal orderPendingQty,
        String salesStatus,
        String parentRecord,
        Instant createdAt
) {
    public InventoryItem withSnapshotId(Long snapshotId) {
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                inventoryQuality, warehouseCode, warehouseName, projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, shippingAvailableQty,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    public InventoryItem withMaterialInfo(String projectModel, String materialName) {
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                inventoryQuality, warehouseCode, warehouseName, projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, shippingAvailableQty,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    public InventoryItem withWarehouseQuality(String quality, String name) {
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                quality, warehouseCode, name, projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, shippingAvailableQty,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    public InventoryItem withCalculatedQty() {
        BigDecimal available = safeQty(odmSupplierQty)
                .add(safeQty(xa400Qty))
                .add(safeQty(xa378Qty))
                .add(safeQty(xa226Qty));
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                inventoryQuality, warehouseCode, warehouseName, projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, available,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    private static BigDecimal safeQty(BigDecimal qty) {
        return qty != null ? qty : BigDecimal.ZERO;
    }
}
