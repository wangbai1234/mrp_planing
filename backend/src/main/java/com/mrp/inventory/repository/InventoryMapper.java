package com.mrp.inventory.repository;

import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventorySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMapper {

    int insertSnapshot(InventorySnapshot snapshot);

    InventorySnapshot selectSnapshotById(@Param("id") Long id);

    InventorySnapshot selectLatestSnapshot();

    List<InventorySnapshot> selectAllSnapshots();

    int insertDetails(@Param("list") List<InventoryDetail> details);

    List<InventoryDetail> selectDetailsBySnapshotId(@Param("snapshotId") Long snapshotId);

    List<InventoryDetail> selectEffectiveDetailsBySnapshotId(@Param("snapshotId") Long snapshotId);

    int countDetailsBySnapshotId(@Param("snapshotId") Long snapshotId);

    int deleteDetailsBySnapshotId(@Param("snapshotId") Long snapshotId);
}
