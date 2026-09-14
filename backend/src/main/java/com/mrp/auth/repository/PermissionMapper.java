package com.mrp.auth.repository;

import com.mrp.auth.domain.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    List<Permission> selectAll();

    List<Permission> selectActive();

    Permission selectById(@Param("id") Long id);

    Permission selectByCode(@Param("code") String code);

    List<Permission> selectByParentId(@Param("parentId") Long parentId);

    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    List<Permission> selectByUserId(@Param("userId") Long userId);

    List<String> selectCodesByUserId(@Param("userId") Long userId);

    int insert(Permission permission);

    int update(Permission permission);

    int deleteById(@Param("id") Long id);
}
