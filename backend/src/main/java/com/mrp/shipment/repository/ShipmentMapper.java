package com.mrp.shipment.repository;

import com.mrp.shipment.domain.ShipmentBatch;
import com.mrp.shipment.domain.ShipmentDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShipmentMapper {

    int insertBatch(ShipmentBatch batch);

    ShipmentBatch selectBatchById(@Param("id") Long id);

    ShipmentBatch selectLatestBatch();

    int insertDetails(@Param("list") List<ShipmentDetail> details);

    List<ShipmentDetail> selectDetailsByBatchId(@Param("batchId") Long batchId);

    int countDetailsByBatchId(@Param("batchId") Long batchId);

    int deleteDetailsByBatchId(@Param("batchId") Long batchId);
}
