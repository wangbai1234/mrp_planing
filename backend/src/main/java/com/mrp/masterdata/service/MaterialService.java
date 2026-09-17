package com.mrp.masterdata.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.NotFoundException;
import com.mrp.common.exception.ValidationException;
import com.mrp.common.response.PageResult;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.repository.MaterialCategoryMapper;
import com.mrp.masterdata.repository.MaterialMapper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;

    public MaterialService(MaterialMapper materialMapper, MaterialCategoryMapper materialCategoryMapper) {
        this.materialMapper = materialMapper;
        this.materialCategoryMapper = materialCategoryMapper;
    }

    public Material getById(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new NotFoundException("MRP_MATERIAL_NOT_FOUND", "Material not found: " + id);
        }
        return material;
    }

    public Material getByCode(String materialCode) {
        Material material = materialMapper.selectByCode(materialCode);
        if (material == null) {
            throw new NotFoundException("MRP_MATERIAL_NOT_FOUND", "Material not found: " + materialCode);
        }
        return material;
    }

    public List<Material> list(String category, String region, Boolean isActive, String keyword,
                               Long materialCategoryId) {
        return materialMapper.selectAll(category, region, isActive, keyword, materialCategoryId);
    }

    public PageResult<Material> listPage(String category, String region, Boolean isActive, String keyword,
                                         Long materialCategoryId, int page, int pageSize) {
        // Ensure valid page/pageSize
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        int offset = (page - 1) * pageSize;
        List<Material> items = materialMapper.selectPage(category, region, isActive, keyword,
                materialCategoryId, offset, pageSize);
        int total = materialMapper.countTotal(category, region, isActive, keyword, materialCategoryId);

        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional
    public Material create(Material material) {
        // Check duplicate code
        if (materialMapper.countByCode(material.materialCode()) > 0) {
            throw new ValidationException("Material code already exists: " + material.materialCode());
        }

        // Set default dataSource if not provided
        if (material.dataSource() == null || material.dataSource().isBlank()) {
            material = new Material(
                    material.id(), material.materialCode(), material.materialName(),
                    material.projectModel(), material.specModel(), material.unit(),
                    material.moq(), material.mpq(), material.region(), material.attribute(),
                    material.category(), material.isActive(), material.leadTimeDays(),
                    material.originPlace(), Material.SOURCE_EXCEL_IMPORT, material.externalId(),
                    material.isDeleted(), material.createdAt(), material.updatedAt(),
                    material.materialCategoryId()
            );
        }

        materialMapper.insert(material);
        log.info("Material created: code={}, id={}", material.materialCode(), material.id());
        return material;
    }

    @Transactional
    public Material update(Material material) {
        // Check exists
        Material existing = getById(material.id());

        // Check code conflict (if code changed)
        if (!existing.materialCode().equals(material.materialCode())) {
            if (materialMapper.countByCode(material.materialCode()) > 0) {
                throw new ValidationException("Material code already exists: " + material.materialCode());
            }
        }

        materialMapper.update(material);
        log.info("Material updated: id={}", material.id());
        return getById(material.id());
    }

    @Transactional
    public void delete(Long id) {
        getById(id); // Check exists
        materialMapper.logicalDelete(id);
        log.info("Material logically deleted: id={}", id);
    }

    @Transactional
    public List<Material> batchCreate(List<Material> materials) {
        if (materials.isEmpty()) {
            return materials;
        }

        // Check duplicates within batch
        long uniqueCodes = materials.stream().map(Material::materialCode).distinct().count();
        if (uniqueCodes < materials.size()) {
            throw new ValidationException("Duplicate material codes in batch");
        }

        // Check existing codes
        for (Material m : materials) {
            if (materialMapper.countByCode(m.materialCode()) > 0) {
                throw new ValidationException("Material code already exists: " + m.materialCode());
            }
        }

        // Batch insert (500 per batch)
        for (int i = 0; i < materials.size(); i += 500) {
            List<Material> batch = materials.subList(i, Math.min(i + 500, materials.size()));
            materialMapper.insertBatch(batch);
        }

        log.info("Batch created {} materials", materials.size());
        return materials;
    }

    @Transactional
    public Map<String, Object> updateCategoryFromExcel(InputStream excelStream) {
        Map<String, Long> categoryCodeMap = new HashMap<>();
        List<MaterialCategory> allCategories = materialCategoryMapper.selectAll(null, null);
        for (MaterialCategory cat : allCategories) {
            if (cat.getCode() != null) {
                categoryCodeMap.put(cat.getCode(), cat.getId());
            }
        }

        Map<String, String> materialCategoryMapping = new HashMap<>();
        try {
            parseExcelMapping(excelStream, materialCategoryMapping);
        } catch (Exception e) {
            log.error("Failed to parse Excel for category mapping", e);
            throw new BusinessException("PARSE_ERROR", "Failed to parse Excel file: " + e.getMessage());
        }

        int updated = 0;
        int notFound = 0;
        int categoryNotFound = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> entry : materialCategoryMapping.entrySet()) {
            String materialCode = entry.getKey();
            String categoryCode = entry.getValue();

            if (materialCode == null || materialCode.isBlank() || categoryCode == null || categoryCode.isBlank()) {
                continue;
            }

            Long categoryId = categoryCodeMap.get(categoryCode);
            if (categoryId == null) {
                categoryNotFound++;
                errors.add("Category code not found: " + categoryCode + " for material: " + materialCode);
                continue;
            }

            Material existing = materialMapper.selectByCode(materialCode);
            if (existing == null) {
                notFound++;
                errors.add("Material not found: " + materialCode);
                continue;
            }

            try {
                materialMapper.updateCategoryIdByCode(materialCode, categoryId);
                updated++;
            } catch (Exception e) {
                errors.add("Failed to update material " + materialCode + ": " + e.getMessage());
            }
        }

        log.info("Category update completed: updated={}, notFound={}, categoryNotFound={}, errors={}",
                updated, notFound, categoryNotFound, errors.size());

        Map<String, Object> result = new HashMap<>();
        result.put("totalProcessed", materialCategoryMapping.size());
        result.put("updated", updated);
        result.put("materialNotFound", notFound);
        result.put("categoryNotFound", categoryNotFound);
        result.put("errors", errors);
        return result;
    }

    private void parseExcelMapping(InputStream excelStream, Map<String, String> mapping) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(excelStream)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new BusinessException("NO_SHEET", "No sheet found in Excel");
            }

            InputStream sheetStream = sheets.next();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XMLReader xmlReader = factory.newSAXParser().getXMLReader();

            CategoryMappingHandler handler = new CategoryMappingHandler(sst, mapping);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(sheetStream));
            sheetStream.close();
        }
    }

    private static class CategoryMappingHandler extends DefaultHandler {
        private final SharedStrings sst;
        private final Map<String, String> mapping;

        private int currentRow = -1;
        private int currentCol = 0;
        private String cellType;
        private String cellValue;
        private boolean inValue;
        private StringBuilder valueBuilder = new StringBuilder();
        private List<String> currentRowData = new ArrayList<>();

        private int materialCodeCol = -1;
        private int categoryCodeCol = -1;

        CategoryMappingHandler(SharedStrings sst, Map<String, String> mapping) {
            this.sst = sst;
            this.mapping = mapping;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if ("row".equals(localName) || "row".equals(qName)) {
                currentRow++;
                currentCol = 0;
                currentRowData.clear();
                String rowNum = getAttr(attrs, "r");
                if (rowNum != null) {
                    currentRow = Integer.parseInt(rowNum) - 1;
                }
            } else if ("c".equals(localName) || "c".equals(qName)) {
                cellType = getAttr(attrs, "t");
                cellValue = null;
                inValue = false;
                valueBuilder.setLength(0);
                String ref = getAttr(attrs, "r");
                if (ref != null) {
                    int col = colFromRef(ref);
                    while (currentCol < col) {
                        currentRowData.add(null);
                        currentCol++;
                    }
                }
            } else if ("v".equals(localName) || "v".equals(qName) || "t".equals(localName) || "t".equals(qName)) {
                inValue = true;
                valueBuilder.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) {
                valueBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("v".equals(localName) || "v".equals(qName) || "t".equals(localName) || "t".equals(qName)) {
                inValue = false;
                cellValue = valueBuilder.toString().trim();
            } else if ("c".equals(localName) || "c".equals(qName)) {
                String resolved = resolveCellValue(cellType, cellValue);
                currentRowData.add(resolved);
                currentCol++;
            } else if ("row".equals(localName) || "row".equals(qName)) {
                processRow();
            }
        }

        private void processRow() {
            if (currentRow == 0) {
                for (int i = 0; i < currentRowData.size(); i++) {
                    String val = currentRowData.get(i);
                    if (val != null) {
                        if (val.contains("料号") || val.contains("material_code") || val.contains("materialCode")) {
                            materialCodeCol = i;
                        }
                        if (val.contains("CODE") || val.contains("code") || val.contains("分类编码")) {
                            categoryCodeCol = i;
                        }
                    }
                }
                return;
            }

            if (materialCodeCol < 0 || categoryCodeCol < 0) {
                return;
            }

            if (currentRowData.size() <= Math.max(materialCodeCol, categoryCodeCol)) {
                return;
            }

            String materialCode = currentRowData.get(materialCodeCol);
            String categoryCode = currentRowData.get(categoryCodeCol);

            if (materialCode != null && !materialCode.isBlank() && categoryCode != null && !categoryCode.isBlank()) {
                mapping.put(materialCode.trim(), categoryCode.trim());
            }
        }

        private String resolveCellValue(String type, String value) {
            if (value == null || value.isEmpty()) return null;
            if ("s".equals(type)) {
                try {
                    int idx = Integer.parseInt(value);
                    return sst.getItemAt(idx).getString();
                } catch (Exception e) {
                    return value;
                }
            }
            if ("str".equals(type)) return value;
            if ("b".equals(type)) return "1".equals(value) ? "true" : "false";
            return value;
        }

        private int colFromRef(String ref) {
            String letters = ref.replaceAll("\\d+", "");
            int col = 0;
            for (char c : letters.toCharArray()) {
                col = col * 26 + (c - 'A' + 1);
            }
            return col - 1;
        }

        private String getAttr(Attributes attrs, String name) {
            for (int i = 0; i < attrs.getLength(); i++) {
                if (name.equals(attrs.getLocalName(i)) || name.equals(attrs.getQName(i))) {
                    return attrs.getValue(i);
                }
            }
            return null;
        }
    }

    @Transactional
    public Material syncFromExternal(String externalId, String materialCode, String materialName,
                                     String projectModel, String specModel, String unit,
                                     Long moq, Long mpq, String region, String attribute,
                                     String category, Boolean isActive, Integer leadTimeDays,
                                     String originPlace) {
        // Check if already synced by externalId
        Material existing = null;
        if (externalId != null) {
            // Use count + select to avoid loading all data
            if (materialMapper.countByExternalId(externalId) > 0) {
                // Find by externalId - use list with filter
                List<Material> found = materialMapper.selectAll(null, null, null, externalId, null);
                existing = found.stream()
                        .filter(m -> externalId.equals(m.externalId()))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (existing != null) {
            // Update existing
            Material updated = new Material(
                    existing.id(), materialCode, materialName, projectModel, specModel, unit,
                    moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                    Material.SOURCE_API_SYNC, externalId, false, existing.createdAt(), null,
                    existing.materialCategoryId()
            );
            materialMapper.update(updated);
            log.info("Material synced (updated): externalId={}, code={}", externalId, materialCode);
            return getById(existing.id());
        } else {
            // Check by material code
            existing = materialMapper.selectByCode(materialCode);
            if (existing != null) {
                // Update externalId
                Material updated = new Material(
                        existing.id(), materialCode, materialName, projectModel, specModel, unit,
                        moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                        Material.SOURCE_API_SYNC, externalId, false, existing.createdAt(), null,
                        existing.materialCategoryId()
                );
                materialMapper.update(updated);
                log.info("Material synced (linked): code={}, externalId={}", materialCode, externalId);
                return getById(existing.id());
            } else {
                // Create new
                Material newMaterial = new Material(
                        null, materialCode, materialName, projectModel, specModel, unit,
                        moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                        Material.SOURCE_API_SYNC, externalId, false, null, null, null
                );
                materialMapper.insert(newMaterial);
                log.info("Material synced (created): code={}, externalId={}", materialCode, externalId);
                return newMaterial;
            }
        }
    }
}
