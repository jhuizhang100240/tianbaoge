package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Category;
import com.tbgbook.bookstore.book.mapper.CategoryMapper;
import com.tbgbook.bookstore.book.service.ICategoryService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;

import lombok.RequiredArgsConstructor;

/**
 * 图书分类 Service 业务层处理。
 *
 * <p>本类演示了**树形基础数据**的写法，后续 部门 / 库位 / 分类类资源照此复制：
 * <ol>
 *   <li>{@code ancestors} 与 {@code categoryLevel} 由服务端按 {@code parentId} 推导，
 *       客户端传入值一律覆盖 —— 否则前端漏传一次就把树的层级关系写坏了</li>
 *   <li>只支持两级（需求 §4.1.5「维护二级分类树」），因此父变更时若该节点已有子分类，
 *       会拒绝 —— 否则会出现三级，超出约定</li>
 *   <li>删除保护两类：有子分类、被图书引用（FR-4.1 规则 4）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService
{
    /** 一级分类的父 ID 约定（接口文档：0=一级） */
    private static final long ROOT_PARENT_ID = 0L;

    /** 分类树最大层级（需求 §4.1.5 为二级分类树） */
    private static final int MAX_LEVEL = 2;

    private final ReferenceGuard referenceGuard;

    @Override
    public List<Category> selectCategoryList(Category category)
    {
        return baseMapper.selectCategoryList(category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCategory(Category category)
    {
        fillTreeFields(category);
        return baseMapper.insert(category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCategory(Category category)
    {
        if (category.getCategoryId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的分类");
        }
        Category current = referenceGuard.requireExists(baseMapper, category.getCategoryId(), "分类");

        Long newParentId = (category.getParentId() == null) ? ROOT_PARENT_ID : category.getParentId();
        boolean parentChanged = !newParentId.equals(current.getParentId());

        // 只支持两级：已有子分类的节点不能再挂到父分类下，否则会造出三级
        if (parentChanged && newParentId != ROOT_PARENT_ID && hasChildren(category.getCategoryId()))
        {
            throw BizErrors.ruleViolated(
                    "该分类下已有子分类，不能再挂到其它分类下（本项目只支持二级分类）");
        }
        // 不能把自己挂到自己下面
        if (category.getCategoryId().equals(newParentId))
        {
            throw BizErrors.ruleViolated("不能把分类的上级设为自己");
        }

        category.setParentId(newParentId);
        fillTreeFields(category);
        return baseMapper.updateById(category);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeCategories(Long[] categoryIds)
    {
        List<Long> idList = (categoryIds == null) ? Collections.emptyList() : Arrays.asList(categoryIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "分类");

        for (Serializable id : ids)
        {
            // ① 有子分类 → 拒绝（主从完整性：删父留子会造出孤儿行）
            long children = baseMapper.selectCount(
                    Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
            if (children > 0)
            {
                throw BizErrors.ruleViolated(
                        "该分类下还有 " + children + " 个子分类，不能删除；请先删除或转移子分类");
            }

            // ② 被图书引用 → 拒绝（FR-4.1 规则 4，需求点名的三处之一）
            referenceGuard.requireNoReference(
                    "biz_book", "category_id", id, "del_flag",
                    "图书", "分类", "改为停用");
        }

        // 逻辑删除（@TableLogic：del_flag 0→2）
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 按 {@code parentId} 推导并覆盖 {@code ancestors} 与 {@code categoryLevel}。
     *
     * <p>无论客户端传什么，这里都会重算 —— 这两个字段是服务端的事实。
     */
    private void fillTreeFields(Category category)
    {
        Long parentId = category.getParentId();
        if (parentId == null || parentId == ROOT_PARENT_ID)
        {
            category.setParentId(ROOT_PARENT_ID);
            // 一级分类的祖级列表按 RuoYi 约定记为 "0"（便于 replaceFirst 做子树迁移）
            category.setAncestors(String.valueOf(ROOT_PARENT_ID));
            category.setCategoryLevel(1);
            return;
        }

        Category parent = referenceGuard.requireExists(baseMapper, parentId, "上级分类");
        int parentLevel = (parent.getCategoryLevel() == null) ? 1 : parent.getCategoryLevel();
        if (parentLevel >= MAX_LEVEL)
        {
            throw BizErrors.ruleViolated(
                    "「" + parent.getCategoryName() + "」已是第 " + parentLevel + " 级分类，"
                            + "不能再挂子分类（本项目只支持 " + MAX_LEVEL + " 级分类）");
        }
        category.setAncestors(parent.getAncestors() + "," + parentId);
        category.setCategoryLevel(parentLevel + 1);
    }

    /** 该分类是否有未删除的子分类 */
    private boolean hasChildren(Long categoryId)
    {
        return baseMapper.selectCount(
                Wrappers.<Category>lambdaQuery().eq(Category::getParentId, categoryId)) > 0;
    }
}
