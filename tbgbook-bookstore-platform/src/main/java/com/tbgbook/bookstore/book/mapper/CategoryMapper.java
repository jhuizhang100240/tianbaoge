package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.Category;

/**
 * 图书分类 Mapper 接口。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，此处只声明 XML 覆盖不了的复杂查询。
 *
 * @author tbgbook
 */
public interface CategoryMapper extends BaseMapper<Category>
{
    /**
     * 查询图书分类列表（含关联图书数）。
     *
     * @param category 查询条件
     * @return 分类集合（扁平，前端用 handleTree 组装成树）
     */
    List<Category> selectCategoryList(Category category);
}
