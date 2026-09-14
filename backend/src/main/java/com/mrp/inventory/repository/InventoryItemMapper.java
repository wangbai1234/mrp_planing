package com.mrp.inventory.repository;

import com.mrp.inventory.domain.InventoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryItemMapper {

    int insertItems(@Param("list") List<InventoryItem> items);

    List<InventoryItem> selectBySnapshotId(@Param("snapshotId") Long snapshotId);

    List<InventoryItem> selectPageBySnapshotId(@Param("snapshotId") Long snapshotId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    int countBySnapshotId(@Param("snapshotId") Long snapshotId);

    int deleteBySnapshotId(@Param("snapshotId") Long snapshotId);
}
