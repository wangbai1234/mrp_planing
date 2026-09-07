package com.mrp.bom.domain;

public class BomTreeNodeV2 {
    private String code;
    private String name;
    private String spec;
    private Long qty;
    private String version;
    private boolean hasChildren;

    public BomTreeNodeV2() {}

    public BomTreeNodeV2(String code, String name, String spec, Long qty, String version, boolean hasChildren) {
        this.code = code;
        this.name = name;
        this.spec = spec;
        this.qty = qty;
        this.version = version;
        this.hasChildren = hasChildren;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public Long getQty() { return qty; }
    public void setQty(Long qty) { this.qty = qty; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isHasChildren() { return hasChildren; }
    public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }
}
