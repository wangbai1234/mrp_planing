package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Service
public class MrpShortageExportService {
    private static final int PAGE_SIZE = 100;
    private final MrpCalculationService calculation;
    private final MrpCalcBatchMapper batches;

    public MrpShortageExportService(MrpCalculationService calculation, MrpCalcBatchMapper batches) {
        this.calculation = calculation;
        this.batches = batches;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] export(Long batchId, String factory, LocalDate sourceMonth, String keyword, String root) throws IOException {
        MrpCalcBatch batch = batches.selectById(batchId);
        if (batch == null || batch.getActiveRunId() == null) return new byte[0];
        Long runId = batch.getActiveRunId();
        boolean detail = root != null && !root.isBlank();
        boolean scoped = sourceMonth != null || detail || keyword != null && !keyword.isBlank();
        boolean historical = !"v5-shifted-business-weeks".equals(batch.getRuleVersion());
        var first = calculation.getShortagePage(batchId, factory, root, null, sourceMonth, keyword, detail, 1, PAGE_SIZE);
        if (first.total() == 0) return new byte[0];
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("末级物料汇总");
            String[] headers = detail
                    ? new String[] {"工厂", "来源整机", "末级需求物料", "来源月", "终点类型", "供应方式", "净需求"}
                    : new String[] {"工厂", "末级需求物料", "终点类型", "供应方式",
                    scoped ? "筛选范围净需求" : "末级净需求", "整批良品库存池", "整批库存风险", "受影响整机数", "处理状态"};
            Row heading = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                heading.createCell(column).setCellValue(headers[column]);
                sheet.setColumnWidth(column, Math.min(26, Math.max(15, headers[column].length() * 2 + 4)) * 256);
            }
            CellStyle quantityStyle = workbook.createCellStyle();
            DataFormat formats = workbook.createDataFormat();
            quantityStyle.setDataFormat(formats.getFormat("#,##0.######"));
            int rowIndex = 1;
            int pages = (first.total() + PAGE_SIZE - 1) / PAGE_SIZE;
            for (int page = 1; page <= pages; page++) {
                var result = page == 1 ? first : calculation.getShortagePage(batchId, factory, root, null, sourceMonth, keyword, detail, page, PAGE_SIZE);
                for (MrpTerminalDemand demand : result.items()) {
                    Row row = sheet.createRow(rowIndex++);
                    int column = 0;
                    row.createCell(column++).setCellValue(value(demand.getFactoryCode()));
                    if (detail) row.createCell(column++).setCellValue(value(demand.getRootMaterialCode()));
                    row.createCell(column++).setCellValue(value(demand.getTerminalMaterialCode()));
                    if (detail) row.createCell(column++).setCellValue(demand.getSourceMonth() == null ? "" : demand.getSourceMonth().toString());
                    row.createCell(column++).setCellValue(value(demand.getTerminalType()));
                    row.createCell(column++).setCellValue(value(demand.getSupplyMode()));
                    BigDecimal quantity = demand.getQuantity().stripTrailingZeros();
                    if (quantity.precision() > 15) row.createCell(column).setCellValue(quantity.toPlainString());
                    else row.createCell(column).setCellValue(quantity.doubleValue());
                    row.getCell(column++).setCellStyle(quantityStyle);
                    if (!detail) {
                        row.createCell(column++).setCellValue(historical ? "历史未评估" : demand.getInventoryPoolCount() == null || demand.getInventoryPoolCount() == 0 ? "无冻结库存池" : demand.getInventoryPoolCount() + " 仓");
                        row.createCell(column++).setCellValue(historical ? "未评估" : positive(demand.getWarehouseRiskCount()) ? demand.getWarehouseRiskCount() + " 项库存风险" : !positive(demand.getInventoryPoolCount()) ? "未评估" : "无待处理风险");
                        row.createCell(column++).setCellValue(demand.getAffectedMachineCount() == null ? 0 : demand.getAffectedMachineCount());
                        row.createCell(column).setCellValue(historical ? "历史记录" : pending(demand) ? "待处理" : "无待办");
                    }
                }
            }
            if (!Objects.equals(runId, batches.selectById(batchId).getActiveRunId()))
                throw new IllegalStateException("计算结果已变化，请刷新页面后重新导出");
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            workbook.dispose();
            return output.toByteArray();
        }
    }

    private static boolean pending(MrpTerminalDemand demand) {
        return positive(demand.getPendingIssueCount()) || positive(demand.getPendingPolicyCount())
                || positive(demand.getWarehouseRiskCount());
    }

    private static boolean positive(Integer count) { return count != null && count > 0; }
    private static String value(String text) { return text == null ? "" : text; }
}
