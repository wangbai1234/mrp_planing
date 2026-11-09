package com.mrp.mrp.service;

import java.util.Set;

/** 可配置值与历史缺失值的计算语义分别处理。 */
public final class MaterialPolicyRules {
    private MaterialPolicyRules() {}
    public static boolean validSupply(String value) {
        return value != null && Set.of("SELF_MADE", "PURCHASED", "OUTSOURCED").contains(value);
    }
    public static boolean validExplosion(String value) {
        return "EXPLODE".equals(value) || "STOP".equals(value);
    }
    public static void validate(String supply, String explosion) {
        if (!validSupply(supply)) throw new IllegalStateException("供应方式必须选择自制、外购或委外");
        if (!validExplosion(explosion)) throw new IllegalStateException("下钻策略必须选择继续下钻或停止下钻");
    }
    public static String effectiveSupply(String value) { return validSupply(value) ? value : "UNMAINTAINED"; }
    public static String effectiveExplosion(String value) { return "STOP".equals(value) ? "STOP" : "EXPLODE"; }
}
