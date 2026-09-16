package com.mrp.masterdata.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.NotFoundException;
import com.mrp.common.exception.ValidationException;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.repository.MaterialCategoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MaterialCategoryService {

    private static final Logger log = LoggerFactory.getLogger(MaterialCategoryService.class);

    private final MaterialCategoryMapper categoryMapper;

    public MaterialCategoryService(MaterialCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public MaterialCategory getById(Long id) {
        MaterialCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new NotFoundException("MRP_CATEGORY_NOT_FOUND", "分类不存在: " + id);
        }
        return category;
    }

    public MaterialCategory getByCode(String code) {
        MaterialCategory category = categoryMapper.selectByCode(code);
        if (category == null) {
            throw new NotFoundException("MRP_CATEGORY_NOT_FOUND", "分类不存在: " + code);
        }
        return category;
    }

    public List<MaterialCategory> listAll() {
        return categoryMapper.selectAll();
    }

    public List<MaterialCategory> listEnabled() {
        return categoryMapper.selectEnabled();
    }

    public List<MaterialCategory> listDisabled() {
        List<MaterialCategory> all = categoryMapper.selectAll();
        return all.stream().filter(c -> !Boolean.TRUE.equals(c.getEnabled())).toList();
    }

    public List<MaterialCategory> getTree() {
        List<MaterialCategory> all = categoryMapper.selectEnabled();
        return buildTree(all, null);
    }

    public List<MaterialCategory> getByParentId(Long parentId) {
        return categoryMapper.selectByParentId(parentId);
    }

    @Transactional
    public MaterialCategory create(MaterialCategory category) {
        if (category.getCode() == null || category.getCode().isBlank()) {
            throw new ValidationException("分类编码不能为空");
        }
        if (category.getName() == null || category.getName().isBlank()) {
            throw new ValidationException("分类名称不能为空");
        }
        if (categoryMapper.countByCode(category.getCode()) > 0) {
            throw new ValidationException("分类编码已存在: " + category.getCode());
        }

        // Determine level
        if (category.getParentId() != null) {
            MaterialCategory parent = getById(category.getParentId());
            category.setLevel(parent.getLevel() + 1);
        } else {
            category.setLevel(1);
        }

        if (category.getSort() == null) {
            category.setSort(0);
        }
        if (category.getEnabled() == null) {
            category.setEnabled(true);
        }

        categoryMapper.insert(category);
        log.info("Category created: code={}, id={}", category.getCode(), category.getId());
        return category;
    }

    @Transactional
    public MaterialCategory update(MaterialCategory category) {
        MaterialCategory existing = getById(category.getId());

        // Code cannot be changed
        if (!existing.getCode().equals(category.getCode())) {
            throw new ValidationException("分类编码不允许修改");
        }

        categoryMapper.update(category);
        log.info("Category updated: id={}", category.getId());
        return getById(category.getId());
    }

    @Transactional
    public void delete(Long id) {
        getById(id); // Check exists

        // Check if has children
        if (categoryMapper.countByParentId(id) > 0) {
            throw new ValidationException("该分类下存在子分类，不允许删除");
        }

        // Check if referenced by materials
        if (categoryMapper.countMaterialReferences(id) > 0) {
            throw new ValidationException("该分类已被物料引用，不允许删除");
        }

        categoryMapper.logicalDelete(id);
        log.info("Category logically deleted: id={}", id);
    }

    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        getById(id); // Check exists
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setEnabled(enabled);
        // We need to set other fields for the update to work
        MaterialCategory existing = getById(id);
        existing.setEnabled(enabled);
        categoryMapper.update(existing);
        log.info("Category {} {}: id={}", enabled ? "enabled" : "disabled", id);
    }

    private List<MaterialCategory> buildTree(List<MaterialCategory> all, Long parentId) {
        List<MaterialCategory> roots = new ArrayList<>();
        for (MaterialCategory c : all) {
            boolean match = (parentId == null && c.getParentId() == null)
                    || (parentId != null && parentId.equals(c.getParentId()));
            if (match) {
                roots.add(c);
            }
        }
        for (MaterialCategory root : roots) {
            root.setChildren(buildTree(all, root.getId()));
        }
        return roots;
    }
}
