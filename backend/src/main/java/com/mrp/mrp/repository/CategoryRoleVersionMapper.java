package com.mrp.mrp.repository;

import com.mrp.mrp.domain.CategoryRoleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryRoleVersionMapper {
    int insert(CategoryRoleVersion version);
    int update(CategoryRoleVersion version);
    CategoryRoleVersion selectById(@Param("id") Long id);
    List<CategoryRoleVersion> selectAll();
}
