package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpInventoryBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpInventoryBalanceMapper {
    int insert(MrpInventoryBalance balance);
    int insertBatch(@Param("list") List<MrpInventoryBalance> list);
    int update(MrpInventoryBalance balance);
    int deleteBySnapshotId(@Param("snapshotId") Long snapshotId);
    List<MrpInventoryBalance> selectBySnapshotId(@Param("snapshotId") Long snapshotId);
    List<MrpInventoryBalance> selectAllBySnapshotId(@Param("snapshotId") Long snapshotId);
    List<MrpInventoryBalance> selectGoodBySnapshotId(@Param("snapshotId") Long snapshotId);
    List<MrpInventoryBalance> selectByMaterialCode(@Param("snapshotId") Long snapshotId, @Param("materialCode") String materialCode);
    List<MrpInventoryBalance> selectAggregatedBySnapshotId(@Param("snapshotId") Long snapshotId);
}
