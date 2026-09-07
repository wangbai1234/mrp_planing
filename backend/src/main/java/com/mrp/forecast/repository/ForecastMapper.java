package com.mrp.forecast.repository;

import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForecastMapper {

    int insertVersion(ForecastVersion version);

    ForecastVersion selectVersionById(@Param("id") Long id);

    ForecastVersion selectLatestVersion();

    List<ForecastVersion> selectAllVersions();

    int insertDetails(@Param("list") List<ForecastDetail> details);

    List<ForecastDetail> selectDetailsByVersionId(@Param("versionId") Long versionId);

    int countDetailsByVersionId(@Param("versionId") Long versionId);

    int deleteDetailsByVersionId(@Param("versionId") Long versionId);
}
