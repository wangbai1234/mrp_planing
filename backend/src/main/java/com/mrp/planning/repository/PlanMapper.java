package com.mrp.planning.repository;

import com.mrp.planning.domain.PlanDetail;
import com.mrp.planning.domain.PlanVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlanMapper {

    int insertVersion(PlanVersion version);

    int updateVersionStatus(@Param("id") Long id, @Param("status") String status,
                           @Param("resultChecksum") String resultChecksum, @Param("version") Integer version);

    PlanVersion selectVersionById(@Param("id") Long id);

    PlanVersion selectLatestVersion(@Param("factoryCode") String factoryCode);

    List<PlanVersion> selectVersionsByFactory(@Param("factoryCode") String factoryCode);

    List<PlanVersion> selectAllLatestVersions();

    int insertDetails(@Param("list") List<PlanDetail> details);

    List<PlanDetail> selectDetailsByVersionId(@Param("versionId") Long versionId);

    List<PlanDetail> selectDetailsByVersionAndFactory(@Param("versionId") Long versionId, @Param("factoryCode") String factoryCode);

    int countDetailsByVersionId(@Param("versionId") Long versionId);

    int deleteDetailsByVersionId(@Param("versionId") Long versionId);
}
