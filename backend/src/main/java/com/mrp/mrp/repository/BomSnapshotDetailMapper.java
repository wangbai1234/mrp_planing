package com.mrp.mrp.repository;

import com.mrp.mrp.domain.BomSnapshotDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BomSnapshotDetailMapper {
    int insert(BomSnapshotDetail detail);
    int insertBatch(@Param("list") List<BomSnapshotDetail> list);
    List<BomSnapshotDetail> selectBySnapshotId(@Param("snapshotId") Long snapshotId);
    List<BomSnapshotDetail> selectByParentCode(@Param("snapshotId") Long snapshotId, @Param("parentCode") String parentCode);
}
