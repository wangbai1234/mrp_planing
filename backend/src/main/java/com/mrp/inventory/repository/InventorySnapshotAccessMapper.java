package com.mrp.inventory.repository;

import com.mrp.inventory.domain.InventorySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InventorySnapshotAccessMapper {
    int canRead(@Param("userId") Long userId, @Param("snapshotId") Long snapshotId);
    List<InventorySnapshot> visible(@Param("userId") Long userId, @Param("keyword") String keyword,
            @Param("warehouseCode") String warehouseCode, @Param("offset") Integer offset, @Param("limit") Integer limit);
    long countVisible(@Param("userId") Long userId, @Param("keyword") String keyword);
}
