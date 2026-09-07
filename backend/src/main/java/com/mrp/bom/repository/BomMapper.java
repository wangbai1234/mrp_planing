package com.mrp.bom.repository;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomMaterial;
import com.mrp.bom.domain.BomTreeNodeV2;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BomMapper {

    List<BomMaterial> selectMaterialPage(
        @Param("keyword") String keyword,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    int countMaterials(@Param("keyword") String keyword);

    BomMaterial selectMaterialByCode(@Param("invCode") String invCode);

    List<BomDetail> selectDetailByParentCode(@Param("parentCode") String parentCode);

    List<BomTreeNodeV2> selectTreeChildren(@Param("parentCode") String parentCode);

    List<BomDetail> selectAllLatestBom();
}
