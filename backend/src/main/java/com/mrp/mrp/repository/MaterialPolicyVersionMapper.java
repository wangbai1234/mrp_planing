package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MaterialPolicyVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialPolicyVersionMapper {
    int insert(MaterialPolicyVersion version);
    int update(MaterialPolicyVersion version);
    MaterialPolicyVersion selectById(@Param("id") Long id);
    List<MaterialPolicyVersion> selectAll();
    int countReferences(@Param("id") Long id);
    int markDeleted(@Param("id") Long id);
}
