package com.mrp.forecast.repository;

import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ForecastMapper {

    int insertVersion(ForecastVersion version);

    Long selectLastInsertVersionId();

    ForecastVersion selectVersionById(@Param("id") Long id);

    ForecastVersion selectLatestVersion();

    List<ForecastVersion> selectAllVersions();

    List<ForecastVersion> selectVersionsPage(@Param("keyword") String keyword,
                                              @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countVersionsPage(@Param("keyword") String keyword);

    int insertDetails(@Param("list") List<ForecastDetail> details);

    List<ForecastDetail> selectDetailsByVersionId(@Param("versionId") Long versionId);

    List<ForecastDetail> selectDetailsByVersionIdAndMaterial(@Param("versionId") Long versionId,
                                                              @Param("materialId") String materialId);

    List<ForecastDetail> selectDetailsPage(@Param("versionId") Long versionId,
                                            @Param("keyword") String keyword,
                                            @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countDetailsPage(@Param("versionId") Long versionId, @Param("keyword") String keyword);

    List<Map<String, Object>> selectDistinctMaterials(@Param("versionId") Long versionId,
                                                       @Param("keyword") String keyword,
                                                       @Param("offset") int offset, @Param("pageSize") int pageSize);

    long countDistinctMaterials(@Param("versionId") Long versionId, @Param("keyword") String keyword);

    int countDetailsByVersionId(@Param("versionId") Long versionId);

    int deleteDetailsByVersionId(@Param("versionId") Long versionId);

    List<String> selectDistinctFactoriesByVersionId(@Param("versionId") Long versionId);
}
