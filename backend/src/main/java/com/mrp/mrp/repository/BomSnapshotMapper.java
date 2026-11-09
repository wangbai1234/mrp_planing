package com.mrp.mrp.repository;

import com.mrp.mrp.domain.BomSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BomSnapshotMapper {
    int insert(BomSnapshot snapshot);
    int update(BomSnapshot snapshot);
    BomSnapshot selectById(@Param("id") Long id);
    BomSnapshot selectLatestReady();
    List<BomSnapshot> selectAll();
}
