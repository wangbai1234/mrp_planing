package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDecisionIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpDecisionIssueMapper {
    int insert(MrpDecisionIssue issue);
    int insertBatch(@Param("list") List<MrpDecisionIssue> list);
    int update(MrpDecisionIssue issue);
    int deleteByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") String status);
    int deleteOpenMaterialByBatchId(@Param("batchId") Long batchId);
    int countResolvedMaterialByBatchId(@Param("batchId") Long batchId);
    MrpDecisionIssue selectById(@Param("id") Long id);
    MrpDecisionIssue selectByBatchIdAndKey(@Param("batchId") Long batchId, @Param("issueKey") String issueKey);
    List<MrpDecisionIssue> selectByBatchId(@Param("batchId") Long batchId);
    List<MrpDecisionIssue> selectByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") String status);
    List<MrpDecisionIssue> selectPageByBatchId(@Param("batchId") Long batchId, @Param("status") String status,
                                                @Param("keyword") String keyword,
                                                @Param("offset") long offset, @Param("pageSize") int pageSize);
    int countByBatchId(@Param("batchId") Long batchId, @Param("status") String status,
                       @Param("keyword") String keyword);
}
