package com.mrp.masterdata.repository;

import com.mrp.masterdata.domain.MaterialCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialCategoryMapper {

    int insert(MaterialCategory category);

    MaterialCategory selectById(@Param("id") Long id);

    MaterialCategory selectByCode(@Param("code") String code);

    List<MaterialCategory> selectAll();

    List<MaterialCategory> selectAll(@Param("level") Integer level, @Param("enabled") Boolean enabled);

    List<MaterialCategory> selectByParentId(@Param("parentId") Long parentId);

    List<MaterialCategory> selectByLevel(@Param("level") int level);

    List<MaterialCategory> selectEnabled();

    int update(MaterialCategory category);

    int logicalDelete(@Param("id") Long id);

    int countByCode(@Param("code") String code);

    int countByParentId(@Param("parentId") Long parentId);

    int countMaterialReferences(@Param("categoryId") Long categoryId);

    List<MaterialCategory> selectByCodes(@Param("codes") List<String> codes);
}
