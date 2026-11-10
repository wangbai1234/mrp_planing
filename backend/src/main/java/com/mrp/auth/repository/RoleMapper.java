package com.mrp.auth.repository;

import com.mrp.auth.domain.Role;
import com.mrp.auth.domain.RoleDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    Role selectById(@Param("id") Long id);

    Role selectByIdForUpdate(@Param("id") Long id);

    Role selectByCode(@Param("code") String code);

    List<Role> selectAll();

    List<Role> selectPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    int countPage(@Param("keyword") String keyword);

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

    List<Long> selectActivePermissionIds();

    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    List<RoleDataScope> selectDataScopesByRoleId(@Param("roleId") Long roleId);

    List<String> selectEnabledFactoryCodes();

    int deleteDataScopesByRoleId(@Param("roleId") Long roleId);

    int insertAllDataScope(@Param("roleId") Long roleId);

    int insertFactoryDataScopes(@Param("roleId") Long roleId, @Param("factoryCodes") List<String> factoryCodes);
}
