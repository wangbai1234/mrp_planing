package com.mrp.mrp.repository;

import com.mrp.mrp.domain.StockPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StockPolicyAccessMapper {
    List<StockPolicy> visible(@Param("userId") Long userId, @Param("keyword") String keyword,
            @Param("warehouse") String warehouse, @Param("offset") Integer offset, @Param("limit") Integer limit);
    int countVisible(@Param("userId") Long userId, @Param("keyword") String keyword, @Param("warehouse") String warehouse);
    int canUseWarehouse(@Param("userId") Long userId, @Param("warehouse") String warehouse);
}
