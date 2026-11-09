package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MrpMaterialFeedback {
    private Long id, batchId, runId, createdBy, respondedBy;
    private String factoryCode, rootMaterialCode, terminalMaterialCode, reason, status, response;
    private LocalDate sourceMonth, suggestedDate;
    private BigDecimal affectedQuantity;
    private LocalDateTime createdAt, respondedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getBatchId(){return batchId;} public void setBatchId(Long v){batchId=v;}
    public Long getRunId(){return runId;} public void setRunId(Long v){runId=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Long getRespondedBy(){return respondedBy;} public void setRespondedBy(Long v){respondedBy=v;}
    public String getFactoryCode(){return factoryCode;} public void setFactoryCode(String v){factoryCode=v;}
    public String getRootMaterialCode(){return rootMaterialCode;} public void setRootMaterialCode(String v){rootMaterialCode=v;}
    public String getTerminalMaterialCode(){return terminalMaterialCode;} public void setTerminalMaterialCode(String v){terminalMaterialCode=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getResponse(){return response;} public void setResponse(String v){response=v;}
    public LocalDate getSourceMonth(){return sourceMonth;} public void setSourceMonth(LocalDate v){sourceMonth=v;}
    public LocalDate getSuggestedDate(){return suggestedDate;} public void setSuggestedDate(LocalDate v){suggestedDate=v;}
    public BigDecimal getAffectedQuantity(){return affectedQuantity;} public void setAffectedQuantity(BigDecimal v){affectedQuantity=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getRespondedAt(){return respondedAt;} public void setRespondedAt(LocalDateTime v){respondedAt=v;}
}
