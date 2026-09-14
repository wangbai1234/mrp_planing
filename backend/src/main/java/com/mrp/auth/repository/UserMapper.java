package com.mrp.auth.repository;

import com.mrp.auth.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    List<User> selectAll();

    List<User> selectWithRoles(@Param("keyword") String keyword,
                                @Param("isActive") Boolean isActive,
                                @Param("roleId") Long roleId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countWithRoles(@Param("keyword") String keyword,
                       @Param("isActive") Boolean isActive,
                       @Param("roleId") Long roleId);

    int insert(User user);

    int update(User user);

    int updateStatus(@Param("id") Long id, @Param("isActive") Boolean isActive);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int updateLastLoginAt(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    boolean hasActiveAdminExcluding(@Param("excludeUserId") Long excludeUserId);
}
