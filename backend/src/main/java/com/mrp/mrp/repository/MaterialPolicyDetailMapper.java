package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MaterialPolicyDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialPolicyDetailMapper {
    int insert(MaterialPolicyDetail detail);
    int insertBatch(@Param("list") List<MaterialPolicyDetail> list);
    int update(MaterialPolicyDetail detail);
    List<MaterialPolicyDetail> selectByVersionId(@Param("versionId") Long versionId);
    int deleteByVersionId(@Param("versionId") Long versionId);
}
