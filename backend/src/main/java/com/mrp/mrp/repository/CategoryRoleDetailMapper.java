package com.mrp.mrp.repository;

import com.mrp.mrp.domain.CategoryRoleDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryRoleDetailMapper {
    int insert(CategoryRoleDetail detail);
    int insertBatch(@Param("list") List<CategoryRoleDetail> list);
    int update(CategoryRoleDetail detail);
    List<CategoryRoleDetail> selectByVersionId(@Param("versionId") Long versionId);
    int deleteByVersionId(@Param("versionId") Long versionId);
}
