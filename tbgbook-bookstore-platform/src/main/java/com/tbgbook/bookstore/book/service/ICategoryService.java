package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.Category;

/**
 * 图书分类 Service 接口。
 *
 * @author tbgbook
 */
public interface ICategoryService extends IService<Category>
{
    /**
     * 查询图书分类列表（含关联图书数）。
     *
     * @param category 查询条件
     * @return 分类集合
     */
    List<Category> selectCategoryList(Category category);

    /**
     * 新增分类。
     *
     * <p>{@code ancestors} 与 {@code categoryLevel} 由本方法按 {@code parentId} 推导，
     * 客户端传入的值会被覆盖（接口文档业务约束）。
     *
     * @param category 分类
     * @return 影响行数
     */
    int addCategory(Category category);

    /**
     * 修改分类。
     *
     * <p>父分类变更时重算本节点的 {@code ancestors} / {@code categoryLevel}；
     * 只支持两级，因此「已有子分类的节点不能再挂到父分类下」会被拒。
     *
     * @param category 分类
     * @return 影响行数
     */
    int updateCategory(Category category);

    /**
     * 批量删除分类（含**删除保护**）。
     *
     * <p>两类拒绝：① 该分类下还有子分类；② 该分类已被图书引用
     * （FR-4.1 规则 4 —— 需求点名的三处「被引用时禁止删除」之一）。
     *
     * @param categoryIds 分类主键数组
     * @return 实际删除行数
     */
    int removeCategories(Long[] categoryIds);
}
