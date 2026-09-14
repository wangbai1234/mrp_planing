package com.mrp.inventory.domain;

import java.time.Instant;

public record InventoryItem(
        Long id,
        Long snapshotId,
        String productLine,
        String inventoryCategory,
        String materialId,
        String projectModel,
        String materialName,
        String supplierName,
        String productMode,
        Long odmSupplierQty,
        Long xa400Qty,
        Long xa378Qty,
        Long xa226Qty,
        Long shippingAvailableQty,
        String classification,
        String barcode,
        String remark,
        Long orderPendingQty,
        String salesStatus,
        String parentRecord,
        Instant createdAt
) {
    public InventoryItem withSnapshotId(Long snapshotId) {
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, shippingAvailableQty,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    public InventoryItem withMaterialInfo(String projectModel, String materialName) {
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, shippingAvailableQty,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }

    public InventoryItem withCalculatedQty() {
        long available = (odmSupplierQty != null ? odmSupplierQty : 0)
                + (xa400Qty != null ? xa400Qty : 0)
                + (xa378Qty != null ? xa378Qty : 0)
                + (xa226Qty != null ? xa226Qty : 0);
        return new InventoryItem(id, snapshotId, productLine, inventoryCategory, materialId,
                projectModel, materialName, supplierName, productMode,
                odmSupplierQty, xa400Qty, xa378Qty, xa226Qty, available,
                classification, barcode, remark, orderPendingQty, salesStatus, parentRecord, createdAt);
    }
}
