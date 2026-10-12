package com.tbgbook.bookstore.usedbook.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;

/**
 * 二手书成色等级 Mapper（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface ConditionGradeMapper extends BaseMapper<ConditionGrade>
{
    /**
     * 查询成色等级列表。
     *
     * @param grade 查询条件
     * @return 成色等级集合
     */
    List<ConditionGrade> selectConditionGradeList(ConditionGrade grade);
}
