package com.mrp.bom.local.repository;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BomLocalMapper {

    List<BomMaterial> selectMaterialPage(
        @Param("keyword") String keyword,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    int countMaterials(@Param("keyword") String keyword);

    BomMaterial selectMaterialByCode(@Param("invCode") String invCode);

    List<BomDetail> selectDetailByParentCode(@Param("parentCode") String parentCode);

    List<BomDetail> selectAllDetails();

    List<BomDetail> selectDetailsByParentCodes(@Param("parentCodes") List<String> parentCodes);

    void insertParent(BomMaterial material);

    void insertDetail(BomDetail detail);

    void deleteAllParents();

    void deleteAllDetails();

    int insertParentBatch(@Param("list") List<BomMaterial> list);

    int insertDetailBatch(@Param("list") List<BomDetail> list);

    boolean isParent(@Param("invCode") String invCode);
}
