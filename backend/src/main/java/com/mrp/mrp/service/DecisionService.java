package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpDecisionIssue;
import com.mrp.mrp.domain.MrpDecisionRecord;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.mrp.repository.MrpDecisionRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DecisionService {
    
    private final MrpDecisionIssueMapper issueMapper;
    private final MrpDecisionRecordMapper recordMapper;
    private final MrpCalcBatchMapper batchMapper;
    
    public DecisionService(MrpDecisionIssueMapper issueMapper, MrpDecisionRecordMapper recordMapper,
                           MrpCalcBatchMapper batchMapper) {
        this.issueMapper = issueMapper;
        this.recordMapper = recordMapper;
        this.batchMapper = batchMapper;
    }
    
    @Transactional
    public MrpDecisionIssue createIssue(Long batchId, String issueKey, String issueType, String severity, 
                                         String scopeType, String scopeKey, String payloadJson) {
        MrpDecisionIssue issue = new MrpDecisionIssue();
        issue.setBatchId(batchId);
        issue.setIssueKey(issueKey);
        issue.setIssueType(issueType);
        issue.setSeverity(severity);
        issue.setScopeType(scopeType);
        issue.setScopeKey(scopeKey);
        issue.setPayloadJson(payloadJson);
        issue.setStatus("OPEN");
        issue.setRevision(0);
        issue.setCreatedAt(LocalDateTime.now());
        issueMapper.insert(issue);
        return issue;
    }
    
    @Transactional
    public MrpDecisionRecord recordDecision(Long issueId, String decisionCode, String decisionPayload, 
                                             String reason, Long operatorId, Integer expectedRevision) {
        MrpDecisionIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new IllegalStateException("Issue not found: " + issueId);
        }
        
        if (expectedRevision != null && !expectedRevision.equals(issue.getRevision())) {
            throw new IllegalStateException("Revision conflict: expected " + expectedRevision + ", actual " + issue.getRevision());
        }
        
        MrpDecisionRecord record = new MrpDecisionRecord();
        record.setIssueId(issueId);
        record.setDecisionCode(decisionCode);
        record.setDecisionPayload(decisionPayload);
        record.setReason(reason);
        record.setOperatorId(operatorId);
        record.setExpectedRevision(expectedRevision);
        record.setDecisionChecksum(computeDecisionChecksum(issue, decisionCode));
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);
        
        issue.setStatus("RESOLVED");
        issue.setResolvedAt(LocalDateTime.now());
        issue.setRevision(issue.getRevision() + 1);
        issueMapper.update(issue);
        
        // Update batch unresolved count
        Long batchId = issue.getBatchId();
        if (batchId != null) {
            long openCount = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN").size();
            MrpCalcBatch batch = batchMapper.selectById(batchId);
            if (batch != null) {
                batch.setUnresolvedCount((int) openCount);
                if (openCount == 0 && "WAITING_DECISION".equals(batch.getStatus())) {
                    batch.setStatus("READY_TO_CALCULATE");
                }
                batchMapper.update(batch);
            }
        }
        
        return record;
    }
    
    @Transactional
    public void batchResolveIssues(Long batchId, List<Long> issueIds, String decisionCode, 
                                    String reason, Long operatorId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !("WAITING_DECISION".equals(batch.getStatus()) || "MATERIAL_REVIEW".equals(batch.getStatus()))) {
            throw new IllegalStateException("当前排产状态不能批量确认缺库存记录");
        }
        if (issueIds == null || issueIds.isEmpty() || issueIds.size() > 100) {
            throw new IllegalArgumentException("请选择 1 至 100 条缺库存问题");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("请填写本批次按零库存计算的确认原因");
        }
        if (new java.util.HashSet<>(issueIds).size() != issueIds.size()) {
            throw new IllegalArgumentException("问题编号不能重复");
        }
        for (Long issueId : issueIds) {
            MrpDecisionIssue issue = issueMapper.selectById(issueId);
            if (issue == null || !batchId.equals(issue.getBatchId())
                    || !"OPEN".equals(issue.getStatus())
                    || !"INVENTORY_MISSING".equals(issue.getIssueType())) {
                throw new IllegalArgumentException("只能批量确认本批次待处理的缺库存记录");
            }
            recordDecision(issueId, decisionCode, null, reason, operatorId, null);
        }
    }
    
    public List<MrpDecisionIssue> getIssuesByBatch(Long batchId) {
        return issueMapper.selectByBatchId(batchId);
    }
    
    public List<MrpDecisionIssue> getOpenIssuesByBatch(Long batchId) {
        return issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
    }
    
    private String computeDecisionChecksum(MrpDecisionIssue issue, String decisionCode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = issue.getIssueKey() + ":" + decisionCode + ":" + issue.getRevision();
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
