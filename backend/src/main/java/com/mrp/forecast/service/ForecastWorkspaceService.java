package com.mrp.forecast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.audit.service.AuditService;
import com.mrp.common.exception.ValidationException;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.common.response.PageResult;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.forecast.repository.ForecastWorkspaceMapper;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ForecastWorkspaceService {
    public record LineInput(String factoryCode, String materialId, String formType, String mold,
                            String status, Map<String, BigDecimal> months, Integer prioritySeq,
                            Long expectedRevision, String reason) {}
    public record Mutation(Long expectedRevision, String reason) {}

    private final ForecastWorkspaceMapper workspace;
    private final ForecastMapper forecasts;
    private final MaterialMapper materials;
    private final PlanningFactoryAccessService access;
    private final AuditService audit;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final com.mrp.mrp.service.FactoryMasterService factoryMaster;

    public ForecastWorkspaceService(ForecastWorkspaceMapper workspace, ForecastMapper forecasts,
            MaterialMapper materials, PlanningFactoryAccessService access, AuditService audit, ObjectMapper json,
            @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc,
            com.mrp.mrp.service.FactoryMasterService factoryMaster) {
        this.workspace = workspace;
        this.forecasts = forecasts;
        this.materials = materials;
        this.access = access;
        this.audit = audit;
        this.json = json;
        this.jdbc = jdbc;
        this.factoryMaster = factoryMaster;
    }

    public Map<String, Object> current() {
        Map<String,Object> head = workspace.currentHead();
        if (head == null || head.get("currentVersionId") == null) return Map.of();
        Long id = number(head.get("currentVersionId"));
        return versionInfo(id);
    }

    public Map<String, Object> versionInfo(Long id) {
        requireVersionScope(id);
        ForecastVersion version = forecasts.selectVersionById(id);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id",version.id());
        result.put("versionNo",version.versionNo());
        result.put("createdBy",version.createdBy());
        result.put("createdAt",version.createdAt());
        result.putAll(workspace.versionMetadata(id));
        return result;
    }

    public void requireVisibleVersion(Long id) { requireVersionScope(id); }

    public List<String> versionMonths(Long id) {
        requireVersionScope(id);
        return workspace.versionMonths(id);
    }

    public PageResult<Map<String,String>> searchMaterials(String keyword, int page, int size) {
        String query = keyword == null ? "" : keyword.trim();
        var items = materials.selectPage(null, null, true, query, null, (page - 1) * size, size)
                .stream().map(material -> Map.of(
                        "materialCode", material.materialCode(),
                        "materialName", material.materialName() == null ? "" : material.materialName(),
                        "specModel", material.specModel() == null ? "" : material.specModel())).toList();
        return new PageResult<>(items, materials.countTotal(null, null, true, query, null), page, size);
    }

    @Transactional
    public Map<String,Object> createDraft() {
        factoryMaster.enabledCodes().forEach(this::requireFactory);
        Map<String,Object> head = workspace.headForUpdate();
        if (workspace.activeDraft() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "已有编辑草稿");
        Long base = head.get("currentVersionId") == null ? null : number(head.get("currentVersionId"));
        if (base != null) requireVersionScope(base);
        Long user = actor();
        workspace.createDraft(base, user);
        Long draftId = workspace.lastId();
        if (base != null) workspace.copyFromVersion(draftId, base);
        log("CREATE_DRAFT", "forecast_draft", draftId.toString(), null, workspace.draft(draftId), "ALL");
        return workspace.draft(draftId);
    }

    public Map<String,Object> activeDraft() {
        Map<String,Object> draft = workspace.activeDraft();
        if (draft == null) return Map.of();
        requireDraftScope(number(draft.get("id")));
        return draft;
    }

    public PageResult<Map<String,Object>> lines(Long draftId, String keyword, int page, int size) {
        requireDraftScope(draftId);
        int offset = (page - 1) * size;
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Map<String,Object> original : workspace.linesPage(draftId, keyword, offset, size)) {
            Map<String,Object> row = new LinkedHashMap<>(original);
            decodeMonths(row);
            rows.add(row);
        }
        return new PageResult<>(rows, (int) workspace.countLines(draftId, keyword), page, size);
    }

    public Map<String,Object> lineByKey(Long draftId, String factoryCode, String materialId) {
        requireDraftScope(draftId);
        requireFactory(factoryCode);
        Map<String,Object> original = workspace.lineByKeyRead(draftId, factoryCode, materialId);
        Map<String,Object> row = original == null ? null : new LinkedHashMap<>(original);
        if (row == null || isDeleted(row)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "草稿行不存在");
        decodeMonths(row);
        return row;
    }

    public Map<String,Object> deletedLineByKey(Long draftId, String factoryCode, String materialId) {
        requireDraftScope(draftId);
        requireFactory(factoryCode);
        Map<String,Object> original = workspace.deletedLineByKeyRead(draftId, factoryCode, materialId);
        Map<String,Object> row = original == null ? null : new LinkedHashMap<>(original);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待删除料号不存在");
        decodeMonths(row);
        return row;
    }

    @Transactional
    public Map<String,Object> add(Long draftId, LineInput input) {
        if (input == null) throw new ValidationException("新增内容不能为空");
        Map<String,Object> draft = lockedDraft(draftId, input.expectedRevision());
        Map<String,Object> line = validatedLine(input, null);
        requireFactory(input.factoryCode());
        Map<String,Object> existing=workspace.lineByKey(draftId,input.factoryCode().trim(),input.materialId().trim());
        if (existing != null) {
            if (!isDeleted(existing)) throw new ResponseStatusException(HttpStatus.CONFLICT,"该工厂料号已存在于草稿");
            validateLineMonths(existing,input.months().keySet());
            Long lineId=number(existing.get("id"));
            line.put("prioritySeq",existing.get("prioritySeq"));
            workspace.updateLine(draftId,lineId,line,actor());
            bump(draftId,input.expectedRevision());
            log("ADD","forecast_line",key(input.factoryCode(),input.materialId()),existing,
                    Map.of("draftId",draftId,"line",line,"reason",requiredReason(input.reason())),input.factoryCode());
            return Map.of("id",lineId,"revision",number(draft.get("revision"))+1);
        }
        validateVersionMonths(draft, input.months().keySet());
        int priority = workspace.maxPriority(draftId) + 1;
        line.put("prioritySeq", priority);
        workspace.insertLine(draftId, line, actor());
        Long lineId = workspace.lastId();
        bump(draftId, input.expectedRevision());
        log("ADD", "forecast_line", key(input.factoryCode(), input.materialId()), null,
                Map.of("draftId",draftId,"line",line,"reason",requiredReason(input.reason())),input.factoryCode());
        return Map.of("id",lineId,"revision",number(draft.get("revision"))+1);
    }

    @Transactional
    public Map<String,Object> edit(Long draftId, Long lineId, LineInput input) {
        if (input == null) throw new ValidationException("编辑内容不能为空");
        Map<String,Object> draft = lockedDraft(draftId, input.expectedRevision());
        Map<String,Object> before = workspace.lineForUpdate(lineId,draftId);
        if (before == null || isDeleted(before)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"草稿行不存在");
        String factory = String.valueOf(before.get("factoryCode"));
        String material = String.valueOf(before.get("materialId"));
        requireFactory(factory);
        if (!factory.equals(input.factoryCode()) || !material.equals(input.materialId()))
            throw new ValidationException("工厂和料号不可原地修改，请删除后新增");
        Map<String,Object> line = validatedLine(input, before);
        validateLineMonths(before,input.months().keySet());
        // Priority is maintained through an explicit action only; ordinary edits retain order.
        line.put("prioritySeq",before.get("prioritySeq"));
        workspace.updateLine(draftId,lineId,line,actor());
        bump(draftId,input.expectedRevision());
        log("EDIT","forecast_line",key(factory,material),before,
                Map.of("draftId",draftId,"line",line,"reason",requiredReason(input.reason())),factory);
        return Map.of("id",lineId,"revision",number(draft.get("revision"))+1);
    }

    @Transactional
    public Map<String,Object> delete(Long draftId, Long lineId, Mutation request) {
        if (request == null) throw new ValidationException("删除原因和修订号必填");
        Map<String,Object> draft = lockedDraft(draftId,request.expectedRevision());
        Map<String,Object> before = workspace.lineForUpdate(lineId,draftId);
        if (before == null || isDeleted(before)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"草稿行不存在");
        String factory = String.valueOf(before.get("factoryCode"));
        requireFactory(factory);
        workspace.deleteLine(draftId,lineId,actor());
        bump(draftId,request.expectedRevision());
        log("DELETE","forecast_line",key(factory,String.valueOf(before.get("materialId"))),before,
                Map.of("draftId",draftId,"reason",requiredReason(request.reason())),factory);
        return Map.of("revision",number(draft.get("revision"))+1);
    }

    @Transactional
    public Map<String,Object> publish(Long draftId, Mutation request) {
        if (request == null) throw new ValidationException("发布原因和修订号必填");
        Map<String,Object> head = workspace.headForUpdate();
        Map<String,Object> draft = lockedDraft(draftId,request.expectedRevision());
        Long currentId = head.get("currentVersionId") == null ? null : number(head.get("currentVersionId"));
        Long baseId = draft.get("baseVersionId") == null ? null : number(draft.get("baseVersionId"));
        if (!Objects.equals(currentId,baseId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"当前版本已变化，请重新创建草稿");
        if (workspace.countActiveLines(draftId)==0) throw new ValidationException("草稿不能为空");
        if (workspace.countActiveMonths(draftId)==0) throw new ValidationException("草稿没有计划月份");
        requireDraftScope(draftId);
        if (workspace.countDraftComparison(baseId,draftId,null,null,true)==0)
            throw new ValidationException("计划内容没有变化，不能生成新版本");
        requiredReason(request.reason());
        ForecastVersion latest = forecasts.selectLatestVersion();
        int versionNo = latest == null ? 1 : latest.versionNo()+1;
        ForecastVersion created = new ForecastVersion(null,versionNo,null,null,ForecastVersion.STATUS_IMPORTED,actor(),null);
        forecasts.insertVersion(created);
        Long versionId = forecasts.selectLastInsertVersionId();
        workspace.setVersionMetadata(versionId,draft.get("baseVersionId") == null ? null : number(draft.get("baseVersionId")),actor());
        workspace.insertPublishedDetails(versionId,draftId);
        workspace.advanceHead(versionId);
        workspace.publishDraft(draftId,versionId);
        ForecastBatchInvalidation.staleBatches(jdbc, versionId);
        log("PUBLISH","forecast_version",versionId.toString(),draft,
                Map.of("versionNo",versionNo,"reason",request.reason()),"ALL");
        return Map.of("id",versionId,"versionNo",versionNo);
    }

    @Transactional
    public void discard(Long draftId, Mutation request) {
        if (request == null) throw new ValidationException("放弃原因和修订号必填");
        lockedDraft(draftId,request.expectedRevision());
        requireDraftScope(draftId);
        requiredReason(request.reason());
        workspace.discardDraft(draftId);
        log("DISCARD","forecast_draft",draftId.toString(),null,Map.of("reason",request.reason()),"ALL");
    }

    public PageResult<Map<String,Object>> compare(Long oldId, Long newId, String keyword, String factoryCode, boolean changesOnly, int page, int size) {
        requireVersionScope(oldId);
        requireVersionScope(newId);
        if (factoryCode != null && !factoryCode.isBlank()) requireFactory(factoryCode);
        return new PageResult<>(workspace.compareVersions(oldId,newId,keyword,factoryCode,changesOnly,(page-1)*size,size),
                (int)workspace.countComparison(oldId,newId,keyword,factoryCode,changesOnly),page,size);
    }

    public PageResult<Map<String,Object>> history(String factory, String material, int page, int size) {
        requireFactory(factory);
        return new PageResult<>(workspace.materialHistory(factory,material,(page-1)*size,size),
                (int)workspace.countMaterialHistory(factory,material),page,size);
    }

    public PageResult<Map<String,Object>> compareDraft(Long draftId, String keyword, String factoryCode, boolean changesOnly, int page, int size) {
        Map<String,Object> draft = draftForPreview(draftId);
        Long base = draft.get("baseVersionId") == null ? null : number(draft.get("baseVersionId"));
        if (factoryCode != null && !factoryCode.isBlank()) requireFactory(factoryCode);
        return new PageResult<>(workspace.compareDraft(base,draftId,keyword,factoryCode,changesOnly,(page-1)*size,size),
                (int)workspace.countDraftComparison(base,draftId,keyword,factoryCode,changesOnly),page,size);
    }

    public Map<String,Object> draftSummary(Long draftId) {
        Map<String,Object> draft = draftForPreview(draftId);
        Long base = draft.get("baseVersionId") == null ? null : number(draft.get("baseVersionId"));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("draftId",draftId);
        result.put("baseVersionId",base);
        result.put("revision",draft.get("revision"));
        result.put("activeLines",workspace.countActiveLines(draftId));
        result.put("changedLines",workspace.countChangedDraftLines(base,draftId,null,null,true));
        result.put("changes",workspace.summarizeDraftComparison(base,draftId,null,null,true));
        return result;
    }

    private Map<String,Object> draftForPreview(Long id) {
        Map<String,Object> draft = workspace.draft(id);
        if (draft == null || !"DRAFT".equals(draft.get("status")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"草稿不存在或已结束");
        requireDraftScope(id);
        return draft;
    }

    private Map<String,Object> lockedDraft(Long id, Long expectedRevision) {
        if (expectedRevision == null) throw new ValidationException("缺少草稿修订号");
        Map<String,Object> draft = workspace.draftForUpdate(id);
        if (draft == null || !"DRAFT".equals(draft.get("status")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"草稿不存在或已结束");
        if (number(draft.get("revision")).longValue()!=expectedRevision)
            throw new ConcurrentChangeException("草稿已被他人修改，请刷新");
        return draft;
    }

    private Map<String,Object> validatedLine(LineInput input, Map<String,Object> existing) {
        if (input == null || input.factoryCode()==null || input.materialId()==null) throw new ValidationException("工厂和料号必填");
        if (input.factoryCode().isBlank() || input.materialId().isBlank() || input.factoryCode().length()>32 || input.materialId().length()>64)
            throw new ValidationException("工厂或料号无效");
        requiredReason(input.reason());
        Material material = materials.selectByCode(input.materialId().trim());
        if (material == null || Boolean.TRUE.equals(material.isDeleted()) || Boolean.FALSE.equals(material.isActive()))
            throw new ValidationException("请选择有效整机料号");
        if (input.months()==null || input.months().isEmpty()) throw new ValidationException("至少填写一个计划月份");
        Map<String,String> months = new LinkedHashMap<>();
        input.months().forEach((month,qty)->{
            try { if (!YearMonth.parse(month).toString().equals(month)) throw new IllegalArgumentException(); }
            catch (RuntimeException e) { throw new ValidationException("月份须为 YYYY-MM: "+month); }
            if (qty==null || qty.signum()<0 || qty.scale()>6 || qty.precision()-qty.scale()>12)
                throw new ValidationException("计划数量须为非负 DECIMAL(18,6)");
            months.put(month+"-01",qty.toPlainString());
        });
        Map<String,Object> line = new LinkedHashMap<>();
        line.put("factoryCode",input.factoryCode().trim());
        line.put("materialId",input.materialId().trim());
        line.put("materialName",material.materialName());
        line.put("businessLine",existing==null?input.factoryCode():existing.get("businessLine"));
        line.put("formType",input.formType());
        line.put("project",material.projectModel());
        line.put("platform",material.specModel());
        line.put("mold",input.mold());
        line.put("status",input.status());
        try { line.put("monthsJson",json.writeValueAsString(months)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("月份序列化失败",e); }
        return line;
    }

    private void validateVersionMonths(Map<String,Object> draft, Set<String> submittedMonths) {
        if (draft.get("baseVersionId") == null) return;
        Set<String> expected = workspace.versionMonths(number(draft.get("baseVersionId"))).stream()
                .map(month -> month.substring(0,7)).collect(Collectors.toSet());
        if (!expected.equals(submittedMonths))
            throw new ValidationException("新增料号必须使用当前版本的全部月份，不能增加或移除月份");
    }

    private Map<String,String> parseMonths(Map<String,Object> row) {
        try { return json.readValue(String.valueOf(row.get("monthsJson")),new TypeReference<Map<String,String>>(){}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("草稿月份数据损坏",e); }
    }

    private void validateLineMonths(Map<String,Object> original, Set<String> submittedMonths) {
        if (!parseMonths(original).keySet().equals(submittedMonths.stream().map(month -> month + "-01").collect(Collectors.toSet())))
            throw new ValidationException("编辑只能修改现有月份的数量，不能增加或移除月份");
    }

    private void decodeMonths(Map<String,Object> row) {
        Object raw = row.remove("monthsJson");
        if (raw == null) {
            String key = row.keySet().stream().filter(name -> name.equalsIgnoreCase("monthsJson") || name.equalsIgnoreCase("months_json"))
                    .findFirst().orElse(null);
            if (key != null) raw = row.remove(key);
        }
        if (raw == null) throw new IllegalStateException("草稿月份数据缺失");
        try {
            Map<String,String> months = json.readValue(String.valueOf(raw),new TypeReference<Map<String,String>>(){});
            if (months == null) throw new IllegalStateException("草稿月份数据缺失");
            row.put("months",months);
            row.put("total",months.values().stream().map(BigDecimal::new).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString());
        }
        catch (JsonProcessingException e) { throw new IllegalStateException("草稿月份数据损坏",e); }
    }

    private void bump(Long id, Long revision) {
        if (workspace.bumpDraft(id,revision,actor())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"草稿已变化");
    }
    private void requireVersionScope(Long id) {
        if (id == null || forecasts.selectVersionById(id)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"版本不存在");
        forecasts.selectDistinctFactoriesByVersionId(id).forEach(this::requireFactory);
        forecasts.selectDistinctFactoriesByVersionId(id).forEach(this::requireFactory);
    }
    private void requireDraftScope(Long id) {
        if (workspace.draft(id)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"草稿不存在");
        workspace.lines(id).stream().map(row->String.valueOf(row.get("factoryCode"))).distinct().forEach(this::requireFactory);
        workspace.lines(id).stream().map(row->String.valueOf(row.get("factoryCode"))).distinct().forEach(this::requireFactory);
    }
    private void requireFactory(String factory) { access.requireFactory(factory); }
    private Long actor() {
        Long id=CurrentUser.getUserId();
        if(id==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请登录");
        return id;
    }
    private String requiredReason(String reason) {
        if(reason==null || reason.isBlank() || reason.length()>500) throw new ValidationException("请填写不超过500字的操作原因");
        return reason.trim();
    }
    private boolean isDeleted(Map<String,Object> row) {
        Object value=row.get("isDeleted");
        return value instanceof Boolean flag ? flag : value instanceof Number count && count.intValue()!=0;
    }
    private Long number(Object value) { return ((Number)value).longValue(); }
    private String key(String factory,String material) { return factory+"|"+material; }
    private void log(String action,String type,String id,Object before,Object after,String factory) {
        audit.log(actor(),null,factory,action,type,id,before,after,null,null);
    }
}
