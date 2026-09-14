package com.mrp.auth.repository;

import com.mrp.auth.domain.UserPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPermissionMapper {

    List<UserPermission> selectByUserId(@Param("userId") Long userId);

    List<UserPermission> selectByUserIdAndEffect(@Param("userId") Long userId, @Param("effect") String effect);

    int insert(UserPermission userPermission);

    int delete(@Param("userId") Long userId, @Param("permissionId") Long permissionId);

    int deleteByUserId(@Param("userId") Long userId);

    int batchInsert(@Param("list") List<UserPermission> list);
}
