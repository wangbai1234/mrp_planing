package com.mrp.forecast.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ForecastWorkspaceMapper {
    Map<String, Object> headForUpdate();
    Map<String, Object> currentHead();
    Map<String, Object> versionMetadata(@Param("id") Long id);
    List<String> versionMonths(@Param("id") Long id);
    int markImportedVersionEffective(@Param("versionId") Long versionId, @Param("userId") Long userId);
    int advanceHead(@Param("versionId") Long versionId);
    Map<String, Object> activeDraft();
    Map<String, Object> draftForUpdate(@Param("id") Long id);
    Map<String, Object> draft(@Param("id") Long id);
    int createDraft(@Param("baseVersionId") Long baseVersionId, @Param("userId") Long userId);
    Long lastId();
    int copyFromVersion(@Param("draftId") Long draftId, @Param("baseVersionId") Long baseVersionId);
    List<Map<String, Object>> lines(@Param("draftId") Long draftId);
    List<Map<String, Object>> linesPage(@Param("draftId") Long draftId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    long countLines(@Param("draftId") Long draftId, @Param("keyword") String keyword);
    Map<String, Object> lineForUpdate(@Param("id") Long id, @Param("draftId") Long draftId);
    Map<String, Object> lineByKey(@Param("draftId") Long draftId, @Param("factoryCode") String factoryCode, @Param("materialId") String materialId);
    Map<String, Object> lineByKeyRead(@Param("draftId") Long draftId, @Param("factoryCode") String factoryCode, @Param("materialId") String materialId);
    Map<String, Object> deletedLineByKeyRead(@Param("draftId") Long draftId, @Param("factoryCode") String factoryCode, @Param("materialId") String materialId);
    int insertLine(@Param("draftId") Long draftId, @Param("line") Map<String, Object> line, @Param("userId") Long userId);
    int updateLine(@Param("draftId") Long draftId, @Param("lineId") Long lineId, @Param("line") Map<String, Object> line, @Param("userId") Long userId);
    int deleteLine(@Param("draftId") Long draftId, @Param("lineId") Long lineId, @Param("userId") Long userId);
    int bumpDraft(@Param("draftId") Long draftId, @Param("expectedRevision") long expectedRevision, @Param("userId") Long userId);
    int publishDraft(@Param("draftId") Long draftId, @Param("versionId") Long versionId);
    int discardDraft(@Param("draftId") Long draftId);
    int countActiveLines(@Param("draftId") Long draftId);
    int countActiveMonths(@Param("draftId") Long draftId);
    int maxPriority(@Param("draftId") Long draftId);
    int setVersionMetadata(@Param("versionId") Long versionId, @Param("baseVersionId") Long baseVersionId, @Param("userId") Long userId);
    int insertPublishedDetails(@Param("versionId") Long versionId, @Param("draftId") Long draftId);
    List<Map<String, Object>> compareVersions(@Param("oldId") Long oldId, @Param("newId") Long newId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly, @Param("offset") int offset, @Param("limit") int limit);
    long countComparison(@Param("oldId") Long oldId, @Param("newId") Long newId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly);
    List<Map<String, Object>> compareDraft(@Param("baseVersionId") Long baseVersionId, @Param("draftId") Long draftId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly, @Param("offset") int offset, @Param("limit") int limit);
    long countDraftComparison(@Param("baseVersionId") Long baseVersionId, @Param("draftId") Long draftId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly);
    List<Map<String, Object>> summarizeDraftComparison(@Param("baseVersionId") Long baseVersionId, @Param("draftId") Long draftId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly);
    int countChangedDraftLines(@Param("baseVersionId") Long baseVersionId, @Param("draftId") Long draftId, @Param("keyword") String keyword, @Param("factoryCode") String factoryCode, @Param("changesOnly") boolean changesOnly);
    List<Map<String,Object>> materialHistory(@Param("factoryCode") String factoryCode, @Param("materialId") String materialId, @Param("offset") int offset, @Param("limit") int limit);
    long countMaterialHistory(@Param("factoryCode") String factoryCode, @Param("materialId") String materialId);
}
