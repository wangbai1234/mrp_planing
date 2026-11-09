package com.mrp.mrp.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface InventorySnapshotGroupMapper {
    int insert(Map<String, Object> group);
    int insertMember(@Param("groupId") Long groupId, @Param("warehouseCode") String warehouseCode,
                     @Param("inventorySnapshotId") Long inventorySnapshotId);
    Long selectSnapshotId(@Param("groupId") Long groupId, @Param("warehouseCode") String warehouseCode);
}
