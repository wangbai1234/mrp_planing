package com.mrp.bom.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BomExplosionResult {

    private List<SchedulingNode> schedulingNodes = new ArrayList<>();
    private List<UnmatchedLeaf> unmatchedLeaves = new ArrayList<>();
    private List<ErrorNode> errorNodes = new ArrayList<>();

    public List<SchedulingNode> getSchedulingNodes() { return schedulingNodes; }
    public void setSchedulingNodes(List<SchedulingNode> schedulingNodes) { this.schedulingNodes = schedulingNodes; }

    public List<UnmatchedLeaf> getUnmatchedLeaves() { return unmatchedLeaves; }
    public void setUnmatchedLeaves(List<UnmatchedLeaf> unmatchedLeaves) { this.unmatchedLeaves = unmatchedLeaves; }

    public List<ErrorNode> getErrorNodes() { return errorNodes; }
    public void setErrorNodes(List<ErrorNode> errorNodes) { this.errorNodes = errorNodes; }

    public int getSchedulingNodeCount() { return schedulingNodes.size(); }
    public int getUnmatchedLeafCount() { return unmatchedLeaves.size(); }
    public int getErrorNodeCount() { return errorNodes.size(); }

    public static class SchedulingNode {
        private String rootMaterialCode;
        private String rootMaterialName;
        private String materialCode;
        private String materialName;
        private String categoryCode;
        private String categoryName;
        private BigDecimal demandQuantity;
        private String bomPath;
        private String quantityChain;
        private BigDecimal quantityMultiplier;

        public String getRootMaterialCode() { return rootMaterialCode; }
        public void setRootMaterialCode(String rootMaterialCode) { this.rootMaterialCode = rootMaterialCode; }
        public String getRootMaterialName() { return rootMaterialName; }
        public void setRootMaterialName(String rootMaterialName) { this.rootMaterialName = rootMaterialName; }
        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public BigDecimal getDemandQuantity() { return demandQuantity; }
        public void setDemandQuantity(BigDecimal demandQuantity) { this.demandQuantity = demandQuantity; }
        public String getBomPath() { return bomPath; }
        public void setBomPath(String bomPath) { this.bomPath = bomPath; }
        public String getQuantityChain() { return quantityChain; }
        public void setQuantityChain(String quantityChain) { this.quantityChain = quantityChain; }
        public BigDecimal getQuantityMultiplier() { return quantityMultiplier; }
        public void setQuantityMultiplier(BigDecimal quantityMultiplier) { this.quantityMultiplier = quantityMultiplier; }
    }

    public static class UnmatchedLeaf {
        private String rootMaterialCode;
        private String rootMaterialName;
        private String materialCode;
        private String materialName;
        private String categoryCode;
        private String categoryName;
        private BigDecimal demandQuantity;
        private String bomPath;
        private String quantityChain;
        private String reason;

        public String getRootMaterialCode() { return rootMaterialCode; }
        public void setRootMaterialCode(String rootMaterialCode) { this.rootMaterialCode = rootMaterialCode; }
        public String getRootMaterialName() { return rootMaterialName; }
        public void setRootMaterialName(String rootMaterialName) { this.rootMaterialName = rootMaterialName; }
        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public BigDecimal getDemandQuantity() { return demandQuantity; }
        public void setDemandQuantity(BigDecimal demandQuantity) { this.demandQuantity = demandQuantity; }
        public String getBomPath() { return bomPath; }
        public void setBomPath(String bomPath) { this.bomPath = bomPath; }
        public String getQuantityChain() { return quantityChain; }
        public void setQuantityChain(String quantityChain) { this.quantityChain = quantityChain; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ErrorNode {
        private String materialCode;
        private String materialName;
        private String errorCode;
        private String errorMessage;
        private String bomPath;

        public ErrorNode() {}

        public ErrorNode(String materialCode, String materialName, String errorCode, String errorMessage, String bomPath) {
            this.materialCode = materialCode;
            this.materialName = materialName;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.bomPath = bomPath;
        }

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getBomPath() { return bomPath; }
        public void setBomPath(String bomPath) { this.bomPath = bomPath; }
    }
}
