package com.mrp.mrp.service;

import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Rebuilds the complete linked set under a batch lock, then writes it in one transaction. */
@Service
public class LinkedDailyTransferService {
    private final MrpCalcBatchMapper batches;
    private final DailyPlanningService daily;
    private final MrpDailyPlanOverrideMapper overrides;
    private final MrpExecutionService executions;
    private final ProductionCalendarDayMapper calendars;
    private final DailyCapacityService capacity;
    private final PlanningFactoryAccessService access;
    public LinkedDailyTransferService(MrpCalcBatchMapper batches, DailyPlanningService daily,
            MrpDailyPlanOverrideMapper overrides, MrpExecutionService executions,
            ProductionCalendarDayMapper calendars, DailyCapacityService capacity, PlanningFactoryAccessService access) {
        this.batches=batches; this.daily=daily; this.overrides=overrides; this.executions=executions;
        this.calendars=calendars; this.capacity=capacity;
        this.access=access;
    }
    public record Input(String rowKey, LocalDate sourceDate, LocalDate targetDate, BigDecimal quantity, Long expectedRunId) {}
    public record Amount(String rowKey, BigDecimal quantity) {}
    public record Commit(String rowKey, LocalDate sourceDate, LocalDate targetDate, BigDecimal quantity,
            Long expectedRunId, String fingerprint, String reason, @com.fasterxml.jackson.annotation.JsonAlias("transfers") List<Amount> lines) {}
    public record Line(String rowKey, String terminalMaterialCode, String terminalType, String supplyMode,
            LocalDate sourceMonth, String pathGroupKey, String materialUnit, String sourceQuantity,
            String targetQuantity, String suggestedQuantity, int sourceRevision, int targetRevision) {}
    public record Preview(String fingerprint, List<Line> lines, DailyCapacityService.DayLoad sourceLoad,
            DailyCapacityService.DayLoad targetLoad) {}

    @Transactional(readOnly=true)
    public Preview preview(Long batchId, Input input) { return build(batchId, input, true); }

    @Transactional
    public void commit(Long batchId, Commit input) {
        if (input==null) throw new IllegalArgumentException("调整请求不能为空");
        batches.selectForUpdate(batchId);
        Input request=new Input(input.rowKey(),input.sourceDate(),input.targetDate(),input.quantity(),input.expectedRunId());
        Preview current=build(batchId,request,false);
        if (!Objects.equals(input.fingerprint(),current.fingerprint()))
            throw new ConcurrentChangeException("预览数据已变化，请重新预览");
        if (input.reason()==null || input.reason().isBlank() || input.reason().length()>512)
            throw new IllegalArgumentException("请填写调整原因（最多512字）");
        if (input.lines()==null || input.lines().size()!=current.lines().size())
            throw new IllegalArgumentException("必须一并确认整机与全部关联备料");
        Map<String,BigDecimal> amounts=new HashMap<>();
        for (Amount amount:input.lines()) {
            if (amount==null || amount.rowKey()==null || amounts.containsKey(amount.rowKey()))
                throw new IllegalArgumentException("调整明细重复或不完整");
            validateInteger(amount.quantity(),true);
            amounts.put(amount.rowKey(),amount.quantity());
        }
        for (Line line:current.lines()) {
            BigDecimal amount=amounts.get(line.rowKey());
            if (amount==null) throw new IllegalArgumentException("缺少关联备料调整明细");
            if (!ProductionCapacityRules.MACHINE_OUTPUT.equals(line.terminalType())
                    && amount.compareTo(new BigDecimal(line.suggestedQuantity())) != 0)
                throw new IllegalArgumentException("排产员只能确认系统联动备料建议；物料人工偏差请在物料缺料页处理");
            if (amount.compareTo(new BigDecimal(line.sourceQuantity()).setScale(0,RoundingMode.FLOOR))>0)
                throw new IllegalArgumentException("转移量超过转出日可转整数数量");
            if (ProductionCapacityRules.MACHINE_OUTPUT.equals(line.terminalType()) && amount.compareTo(input.quantity())!=0)
                throw new IllegalArgumentException("整机转移量与预览不一致");
        }
        for (Line line:current.lines()) {
            BigDecimal amount=amounts.get(line.rowKey());
            if (amount.signum()==0) continue;
            daily.transferLinkedLine(batchId,new DailyPlanningService.TransferRequest(line.rowKey(),input.sourceDate(),input.targetDate(),
                    amount,input.reason(),input.expectedRunId(),new BigDecimal(line.sourceQuantity()),new BigDecimal(line.targetQuantity()),
                    line.sourceRevision(),line.targetRevision()));
        }
    }

    private Preview build(Long batchId, Input input, boolean enforceAvailable) {
        if (input==null) throw new IllegalArgumentException("调整请求不能为空");
        MrpCalcBatch batch=batches.selectById(batchId);
        if (batch==null || !RollingPlanningQueryService.rolling(batch)) throw new IllegalArgumentException("请选择滚动排产版本");
        access.require(batch);
        if (!Set.of("READY","WAITING_DECISION","SCHEDULE_READY").contains(batch.getStatus()))
            throw new IllegalStateException("仅未发布计划可调整；已发布计划请生成修订版");
        if (batch.getActiveRunId()==null || !Objects.equals(batch.getActiveRunId(),input.expectedRunId()))
            throw new ConcurrentChangeException("计划已重算，请刷新后重试");
        validateInteger(input.quantity(),false);
        if (input.sourceDate()==null || input.targetDate()==null || input.sourceDate().equals(input.targetDate()))
            throw new IllegalArgumentException("请选择不同的转出和转入日期");
        for (LocalDate date:List.of(input.sourceDate(),input.targetDate()))
            if (executions.isDateLockedOrExecuted(batchId,date)) throw new IllegalStateException("锁定/已执行日期不可调整");
        List<MrpDailyPlanDetail> facts=daily.getDailyPlans(batchId);
        MrpDailyPlanDetail machine=facts.stream().filter(p->ProductionCapacityRules.MACHINE_OUTPUT.equals(p.getTerminalType())
                && input.sourceDate().equals(p.getScheduleDate()) && Objects.equals(input.rowKey(),DailyPlanningService.gridRowKey(p)))
                .findFirst().orElseThrow(()->new IllegalArgumentException("请选择真实整机排产明细"));
        if (enforceAvailable && machine.getEffectiveQuantity().compareTo(input.quantity())<0)
            throw new IllegalArgumentException("整机转移量超过剩余量");
        boolean production=calendars.selectByVersionId(batch.getProductionCalendarVersionId()).stream()
                .anyMatch(d->Objects.equals(machine.getFactoryCode(),d.getFactoryCode())
                        && input.targetDate().equals(d.getCalendarDate()) && Boolean.TRUE.equals(d.getIsProductionDay()));
        if (!production) throw new IllegalArgumentException("目标日期须为本工厂冻结日历中的生产日");
        List<MrpDailyPlanDetail> sources=facts.stream().filter(p->Objects.equals(machine.getFactoryCode(),p.getFactoryCode())
                && Objects.equals(machine.getRootMaterialCode(),p.getRootMaterialCode()) && Objects.equals(machine.getSourceMonth(),p.getSourceMonth())
                && input.sourceDate().equals(p.getScheduleDate()) && p.getEffectiveQuantity().signum()>0)
                .sorted(Comparator.comparing((MrpDailyPlanDetail p)->!ProductionCapacityRules.MACHINE_OUTPUT.equals(p.getTerminalType()))
                        .thenComparing(DailyPlanningService::gridRowKey)).toList();
        Map<String,MrpDailyPlanOverride> revisions=new HashMap<>();
        for (MrpDailyPlanOverride o:overrides.selectByBatchId(batchId)) revisions.put(overrideKey(o),o);
        Map<String,String> units=capacity.units(batch.getProductionCalendarVersionId());
        List<Line> lines=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for (MrpDailyPlanDetail source:sources) {
            String key=DailyPlanningService.gridRowKey(source);
            if (!seen.add(key)) throw new IllegalStateException("同来源排产明细重复，请重新计算");
            BigDecimal target=facts.stream().filter(p->key.equals(DailyPlanningService.gridRowKey(p)) && input.targetDate().equals(p.getScheduleDate()))
                    .map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal suggested=ProductionCapacityRules.MACHINE_OUTPUT.equals(source.getTerminalType())? input.quantity()
                    :suggest(source.getEffectiveQuantity(),input.quantity(),machine.getEffectiveQuantity());
            lines.add(new Line(key,source.getTerminalMaterialCode(),source.getTerminalType(),source.getSupplyMode(),source.getSourceMonth(),
                    source.getPathGroupKey(),ProductionCapacityRules.MACHINE_OUTPUT.equals(source.getTerminalType())?"台":units.get(source.getTerminalMaterialCode()),
                    text(source.getEffectiveQuantity()),text(target),text(suggested),revision(revisions,key,input.sourceDate()),revision(revisions,key,input.targetDate())));
        }
        String canonical=batchId+"|"+input.expectedRunId()+"|"+input.rowKey()+"|"+input.sourceDate()+"|"+input.targetDate()+"|"+text(input.quantity())+"|"+lines;
        List<DailyCapacityService.DayLoad> loads=capacity.evaluate(batch,facts,input.sourceDate().isBefore(input.targetDate())?input.sourceDate():input.targetDate(),
                input.sourceDate().isAfter(input.targetDate())?input.sourceDate():input.targetDate());
        return new Preview(hash(canonical),List.copyOf(lines),load(loads,machine.getFactoryCode(),input.sourceDate()),load(loads,machine.getFactoryCode(),input.targetDate()));
    }
    /** Suggestions move whole units; fractional BOM residuals remain at source and are conserved. */
    public static BigDecimal suggest(BigDecimal terminal,BigDecimal move,BigDecimal machine) {
        return terminal.multiply(move).divide(machine,0,RoundingMode.HALF_UP)
                .min(terminal.setScale(0,RoundingMode.FLOOR)).max(BigDecimal.ZERO);
    }
    static void validateInteger(BigDecimal amount,boolean allowZero) {
        if (amount==null || amount.signum()<0 || (!allowZero&&amount.signum()==0)
                || amount.stripTrailingZeros().scale()>0 || amount.precision()-amount.scale()>12)
            throw new IllegalArgumentException("转移数量必须为整数，最多12位");
    }
    private static String overrideKey(MrpDailyPlanOverride o) {return String.join("|",o.getFactoryCode(),o.getRootMaterialCode(),o.getTerminalMaterialCode(),o.getTerminalType(),o.getSupplyMode(),o.getPathGroupKey())+"|"+o.getScheduleDate();}
    private static int revision(Map<String,MrpDailyPlanOverride> map,String key,LocalDate date) {var o=map.get(key+"|"+date);return o==null?-1:o.getRevision();}
    private static DailyCapacityService.DayLoad load(List<DailyCapacityService.DayLoad> loads,String factory,LocalDate date) {return loads.stream().filter(l->factory.equals(l.factoryCode())&&date.equals(l.date())).findFirst().orElse(null);}
    private static String text(BigDecimal value) {return value.stripTrailingZeros().toPlainString();}
    private static String hash(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
