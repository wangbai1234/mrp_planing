package com.mrp.auth.repository;

import com.mrp.auth.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    Role selectById(@Param("id") Long id);

    Role selectByCode(@Param("code") String code);

    List<Role> selectAll();

    List<Role> selectActive();

    List<Role> selectByUserId(@Param("userId") Long userId);

    int insert(Role role);

    int update(Role role);

    int deleteById(@Param("id") Long id);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRoles(@Param("userId") Long userId);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int deleteRolePermissions(@Param("roleId") Long roleId);

    int batchInsertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
