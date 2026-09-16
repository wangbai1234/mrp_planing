package com.mrp.masterdata.repository;

import com.mrp.masterdata.domain.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialMapper {

    int insert(Material material);

    int insertBatch(@Param("list") List<Material> materials);

    Material selectById(@Param("id") Long id);

    Material selectByCode(@Param("materialCode") String materialCode);

    List<Material> selectAll(@Param("category") String category, @Param("region") String region,
                             @Param("isActive") Boolean isActive, @Param("keyword") String keyword,
                             @Param("materialCategoryId") Long materialCategoryId);

    List<Material> selectPage(@Param("category") String category, @Param("region") String region,
                              @Param("isActive") Boolean isActive, @Param("keyword") String keyword,
                              @Param("materialCategoryId") Long materialCategoryId,
                              @Param("offset") int offset, @Param("limit") int limit);

    int countTotal(@Param("category") String category, @Param("region") String region,
                   @Param("isActive") Boolean isActive, @Param("keyword") String keyword,
                   @Param("materialCategoryId") Long materialCategoryId);

    int update(Material material);

    int logicalDelete(@Param("id") Long id);

    int countByCode(@Param("materialCode") String materialCode);

    int countByExternalId(@Param("externalId") String externalId);
}
