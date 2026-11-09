package com.mrp.mrp.service;

import com.mrp.mrp.domain.BomSnapshot;
import com.mrp.mrp.domain.BomSnapshotDetail;
import com.mrp.mrp.repository.BomSnapshotDetailMapper;
import com.mrp.mrp.repository.BomSnapshotMapper;
import com.mrp.bom.service.BomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BomSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BomSnapshotService.class);
    private static final int ERROR_SUMMARY_LIMIT = 50;
    private static final String PLANNING_ORGANIZATION = BomService.PLANNING_ORGANIZATION;

    private final BomSnapshotMapper snapshotMapper;
    private final BomSnapshotDetailMapper detailMapper;
    private final JdbcTemplate jdbcTemplate;

    public BomSnapshotService(BomSnapshotMapper snapshotMapper, BomSnapshotDetailMapper detailMapper,
                               JdbcTemplate jdbcTemplate) {
        this.snapshotMapper = snapshotMapper;
        this.detailMapper = detailMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BomSnapshot createSnapshot(LocalDate effectiveDate) {
        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setSourceVersion("bom_parent+bom_detail");
        snapshot.setStatus("CREATING");
        snapshot.setEffectiveAt(effectiveDate);
        snapshotMapper.insert(snapshot);

        BomLoadResult loadResult = loadEdgesFromSource(effectiveDate);

        if (!loadResult.errors.isEmpty()) {
            snapshot.setStatus("FAILED");
            snapshot.setErrorMessage(summarizeErrors(loadResult.errors));
            snapshotMapper.update(snapshot);
            log.warn("BOM snapshot {} failed with {} errors; persisted a bounded summary",
                    snapshot.getId(), loadResult.errors.size());
            return snapshot;
        }

        List<BomSnapshotDetail> edges = loadResult.edges;
        if (edges.isEmpty()) {
            snapshot.setStatus("EMPTY");
            snapshotMapper.update(snapshot);
            log.warn("No BOM edges found for effective date {}", effectiveDate);
            return snapshot;
        }

        for (BomSnapshotDetail edge : edges) {
            edge.setSnapshotId(snapshot.getId());
        }

        int batchSize = 2000;
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<BomSnapshotDetail> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
            detailMapper.insertBatch(batch);
        }

        String checksum = computeChecksum(edges);
        snapshot.setChecksum(checksum);
        snapshot.setStatus("READY");
        snapshot.setErrorMessage(null);
        snapshotMapper.update(snapshot);

        log.info("Created BOM snapshot {} with {} edges, checksum={}", snapshot.getId(), edges.size(), checksum);
        return snapshot;
    }

    private BomLoadResult loadEdgesFromSource(LocalDate effectiveDate) {
        String sql = "SELECT d.parent_code, d.child_code, d.parent_qty, d.child_qty, d.version as bom_version, " +
                "d.effective_date, d.org_name as organization_code, d.body_code as body_scope " +
                "FROM bom_detail d " +
                "WHERE d.org_name = ? " +
                (effectiveDate != null ? "AND (d.effective_date IS NULL OR d.effective_date <= ?) " : "");
        List<Object> parameters = new ArrayList<>();
        parameters.add(PLANNING_ORGANIZATION);
        if (effectiveDate != null) {
            parameters.add(effectiveDate);
        }
        List<BomSnapshotDetail> allRows = jdbcTemplate.query(
                sql,
                parameters.toArray(),
                (rs, rowNum) -> {
                    BomSnapshotDetail detail = new BomSnapshotDetail();
                    detail.setParentCode(rs.getString("parent_code"));
                    detail.setChildCode(rs.getString("child_code"));
                    detail.setParentQty(rs.getBigDecimal("parent_qty"));
                    detail.setChildQty(rs.getBigDecimal("child_qty"));
                    detail.setBomVersion(rs.getString("bom_version"));
                    java.sql.Date ed = rs.getDate("effective_date");
                    detail.setEffectiveDate(ed != null ? ed.toLocalDate() : null);
                    detail.setOrganizationCode(rs.getString("organization_code"));
                    detail.setBodyScope(rs.getString("body_scope"));
                    return detail;
                });

        List<String> errors = new ArrayList<>();
        List<BomSnapshotDetail> result = new ArrayList<>();

        // Group by organization_code (inventory_org) + parent_code + child_code
        Map<String, List<BomSnapshotDetail>> grouped = allRows.stream()
                .collect(Collectors.groupingBy(
                        row -> safeStr(row.getOrganizationCode()) + "|" + row.getParentCode() + "|" + row.getChildCode()));

        for (Map.Entry<String, List<BomSnapshotDetail>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<BomSnapshotDetail> rows = entry.getValue();
            String[] parts = key.split("\\|", 3);
            String orgCode = parts[0];
            String parentCode = parts[1];
            String childCode = parts[2];

            // Check: organization_code must not be empty
            if (orgCode.isEmpty()) {
                errors.add("BOM_ORG_AMBIGUOUS: parent=" + parentCode + ", child=" + childCode + " 库存组织为空");
                continue;
            }

            // 负数或空用量是源数据错误；0 用量表示该整机不使用此物料，跳过且不阻断快照。
            boolean hasInvalidQty = rows.stream().anyMatch(r ->
                    r.getParentQty() == null || r.getParentQty().compareTo(BigDecimal.ZERO) <= 0 ||
                    r.getChildQty() == null || r.getChildQty().compareTo(BigDecimal.ZERO) < 0);
            if (hasInvalidQty) {
                errors.add("BOM_INVALID_QTY: org=" + orgCode + ", parent=" + parentCode +
                        ", child=" + childCode + " 父项或子项数量无效");
                continue;
            }
            rows.removeIf(r -> r.getChildQty().compareTo(BigDecimal.ZERO) == 0);
            if (rows.isEmpty()) {
                continue;
            }

            // Sort by version value: try numeric parse first, fall back to lexicographic
            // null versions treated as lowest
            rows.sort(Comparator.comparing(
                    BomSnapshotDetail::getBomVersion,
                    BomSnapshotService::compareVersions));

            // Find the highest version value
            String highestVersion = rows.get(rows.size() - 1).getBomVersion();

            // Collect all rows that share this highest version
            List<BomSnapshotDetail> highestVersionRows = rows.stream()
                    .filter(r -> compareVersions(r.getBomVersion(), highestVersion) == 0)
                    .collect(Collectors.toList());

            // Check: multiple distinct highest versions (should not happen after sort, but defensive)
            long distinctHighestVersions = highestVersionRows.stream()
                    .map(BomSnapshotDetail::getBomVersion)
                    .distinct()
                    .count();
            if (distinctHighestVersions > 1) {
                errors.add("MULTIPLE_ACTIVE_BOM: org=" + orgCode + ", parent=" + parentCode +
                        ", child=" + childCode + " 存在多个不同版本的最高有效BOM (versions=" +
                        highestVersionRows.stream().map(BomSnapshotDetail::getBomVersion)
                                .distinct().collect(Collectors.joining(", ")) + ")");
                continue;
            }

            // Check: effective date overlap among highest-version rows
            // If multiple rows with the same highest version have different non-null effective dates,
            // that is an overlap condition
            List<LocalDate> nonNullDates = highestVersionRows.stream()
                    .map(BomSnapshotDetail::getEffectiveDate)
                    .filter(d -> d != null)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            if (nonNullDates.size() > 1) {
                errors.add("BOM_EFFECTIVE_OVERLAP: org=" + orgCode + ", parent=" + parentCode +
                        ", child=" + childCode + " 有效期重叠 (version=" + highestVersion +
                        ", dates=" + nonNullDates + ")");
                continue;
            }

            // Take one representative row as the edge
            BomSnapshotDetail edge = highestVersionRows.get(0);
            result.add(edge);
        }

        return new BomLoadResult(result, errors);
    }

    static String summarizeErrors(List<String> errors) {
        int total = errors.size();
        List<String> visible = errors.stream().limit(ERROR_SUMMARY_LIMIT).toList();
        StringBuilder summary = new StringBuilder("共发现 ")
                .append(total)
                .append(" 个BOM问题");
        if (!visible.isEmpty()) {
            summary.append("；前 ").append(visible.size()).append(" 条：")
                    .append(String.join("；", visible));
        }
        if (total > visible.size()) {
            summary.append("；其余 ").append(total - visible.size())
                    .append(" 条未在摘要中展示，请修复源BOM后重新生成快照");
        }
        return summary.toString();
    }

    /**
     * Compare two version strings. Tries numeric comparison first (treating version as a
     * dot-separated numeric sequence like "2.1"), falls back to lexicographic comparison.
     * null is treated as the lowest value.
     */
    static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        // Try numeric: split by '.' and compare each segment as integers
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int maxLen = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLen; i++) {
                long n1 = i < parts1.length ? Long.parseLong(parts1[i].trim()) : 0;
                long n2 = i < parts2.length ? Long.parseLong(parts2[i].trim()) : 0;
                if (n1 != n2) return Long.compare(n1, n2);
            }
            return 0;
        } catch (NumberFormatException e) {
            // Fall back to lexicographic
            return v1.compareTo(v2);
        }
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }

    public BomSnapshot getSnapshot(Long id) {
        return snapshotMapper.selectById(id);
    }

    /** The mutable BOM shown in BOM management is the source for new planning batches. */
    public int countCurrentBomEdges() {
        BomLoadResult loaded = loadEdgesFromSource(null);
        if (!loaded.errors.isEmpty()) throw new IllegalStateException(summarizeErrors(loaded.errors));
        return loaded.edges.size();
    }

    /** The mutable BOM shown in BOM management is the source for new planning batches. */
    public CurrentBom loadCurrentBom() {
        BomLoadResult loaded = loadEdgesFromSource(null);
        if (!loaded.errors.isEmpty()) throw new IllegalStateException(summarizeErrors(loaded.errors));
        if (loaded.edges.isEmpty()) throw new IllegalStateException("尚无上海组织的已同步BOM，请先从NC同步");
        return new CurrentBom(List.copyOf(loaded.edges), computeChecksum(loaded.edges));
    }

    public List<BomSnapshotDetail> edgesForBatch(com.mrp.mrp.domain.MrpCalcBatch batch) {
        if (batch.getBomSnapshotId() != null) return getSnapshotDetails(batch.getBomSnapshotId());
        if (batch.getBomSourceChecksum() == null) throw new IllegalStateException("批次缺少当前BOM校验码");
        CurrentBom current = loadCurrentBom();
        if (!batch.getBomSourceChecksum().equals(current.checksum())) {
            throw new IllegalStateException("BOM管理数据已重新同步，当前批次需重新创建并预检，避免沿旧BOM决策计算新BOM");
        }
        return current.edges();
    }

    public record CurrentBom(List<BomSnapshotDetail> edges, String checksum) {}

    /** Historical snapshots remain readable, but mixed organizations must not enter a new batch. */
    public void validatePlanningOrganization(Long snapshotId) {
        Integer invalid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bom_snapshot_detail WHERE snapshot_id = ? " +
                        "AND (organization_code IS NULL OR organization_code <> ?)",
                Integer.class, snapshotId, PLANNING_ORGANIZATION);
        if (invalid != null && invalid > 0) {
            throw new IllegalStateException("该BOM快照包含非上海组织数据，请从NC重新同步并生成上海组织快照后创建排产");
        }
    }

    public BomSnapshot getCurrentSnapshot() {
        return snapshotMapper.selectLatestReady();
    }

    public List<BomSnapshot> getAllSnapshots() {
        return snapshotMapper.selectAll();
    }

    public List<BomSnapshotDetail> getSnapshotDetails(Long snapshotId) {
        return detailMapper.selectBySnapshotId(snapshotId);
    }

    public List<BomSnapshotDetail> getChildren(Long snapshotId, String parentCode) {
        return detailMapper.selectByParentCode(snapshotId, parentCode);
    }

    String computeChecksum(List<BomSnapshotDetail> edges) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Sort edges by a stable key to ensure deterministic checksum
            List<BomSnapshotDetail> sorted = edges.stream()
                    .sorted(Comparator.comparing(BomSnapshotDetail::getOrganizationCode, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(BomSnapshotDetail::getParentCode, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(BomSnapshotDetail::getChildCode, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(BomSnapshotDetail::getBomVersion, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            for (BomSnapshotDetail edge : sorted) {
                sb.append(safeStr(edge.getOrganizationCode())).append(":")
                  .append(edge.getParentCode()).append(":")
                  .append(edge.getChildCode()).append(":")
                  .append(edge.getParentQty()).append(":")
                  .append(edge.getChildQty()).append(":")
                  .append(safeStr(edge.getBomVersion())).append(":")
                  .append(edge.getEffectiveDate()).append(":")
                  .append(safeStr(edge.getBodyScope())).append(";");
            }
            byte[] hash = md.digest(sb.toString().getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static class BomLoadResult {
        final List<BomSnapshotDetail> edges;
        final List<String> errors;

        BomLoadResult(List<BomSnapshotDetail> edges, List<String> errors) {
            this.edges = edges;
            this.errors = errors;
        }
    }
}
