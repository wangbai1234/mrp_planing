package com.mrp.bom.domain;

import java.util.List;

public class BomTreeNode {
    private String code;
    private String name;
    private String version;
    private Long qty;
    private String orgName;
    private String bodyCode;
    private List<BomTreeNode> children;

    public BomTreeNode() {}

    public BomTreeNode(String code, String name, String version, Long qty, String orgName, String bodyCode) {
        this.code = code;
        this.name = name;
        this.version = version;
        this.qty = qty;
        this.orgName = orgName;
        this.bodyCode = bodyCode;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Long getQty() { return qty; }
    public void setQty(Long qty) { this.qty = qty; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getBodyCode() { return bodyCode; }
    public void setBodyCode(String bodyCode) { this.bodyCode = bodyCode; }

    public List<BomTreeNode> getChildren() { return children; }
    public void setChildren(List<BomTreeNode> children) { this.children = children; }
}
