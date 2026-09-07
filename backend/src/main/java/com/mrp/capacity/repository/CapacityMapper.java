package com.mrp.capacity.repository;

import com.mrp.capacity.domain.CapacityLine;
import com.mrp.capacity.domain.CapacityVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CapacityMapper {

    int insertVersion(CapacityVersion version);

    CapacityVersion selectActiveVersion();

    List<CapacityVersion> selectAllVersions();

    int insertLine(CapacityLine line);

    int updateLine(CapacityLine line);

    int insertLines(@Param("list") List<CapacityLine> lines);

    List<CapacityLine> selectLinesByVersionId(@Param("versionId") Long versionId);

    List<CapacityLine> selectActiveLinesByFactory(@Param("versionId") Long versionId, @Param("factoryCode") String factoryCode);

    CapacityLine selectLineById(@Param("id") Long id);

    int countLinesByVersionId(@Param("versionId") Long versionId);
}
