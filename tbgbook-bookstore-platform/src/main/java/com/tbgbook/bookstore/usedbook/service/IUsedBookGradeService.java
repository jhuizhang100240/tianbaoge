package com.tbgbook.bookstore.usedbook.service;

import java.util.List;

import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.PricePreviewRequest;
import com.tbgbook.bookstore.usedbook.domain.PricePreviewResult;

/**
 * 二手书成色等级 Service 接口（阶段六第一批，页面 A-42，FR-4.2.3）。
 *
 * <p>C3 定价系数可后台调整；成色判定说明是纠纷防范关键字段（R6）。
 *
 * @author tbgbook
 */
public interface IUsedBookGradeService
{
    /**
     * 查询成色等级列表。
     *
     * @param grade 查询条件
     * @return 成色等级集合
     */
    List<ConditionGrade> selectConditionGradeList(ConditionGrade grade);

    /**
     * 查询成色等级详情。
     *
     * @param gradeId 成色等级ID
     * @return 成色等级；不存在时抛 404
     */
    ConditionGrade selectConditionGradeById(Long gradeId);

    /**
     * 新增成色等级（gradeCode 同店唯一 409）。
     *
     * @param grade 成色等级
     * @return 影响行数
     */
    int addConditionGrade(ConditionGrade grade);

    /**
     * 修改成色等级（被引用不影响改名 / 调系数）。
     *
     * @param grade 成色等级
     * @return 影响行数
     */
    int updateConditionGrade(ConditionGrade grade);

    /**
     * 删除成色等级（被回收明细 / 二手书库存引用的档位禁止删除，建议改为停用）。
     *
     * @param gradeIds 成色等级主键数组
     * @return 影响行数
     */
    int removeConditionGrades(Long[] gradeIds);

    /**
     * 按成色系数试算回收价（C3 定价系数的验证入口）。
     *
     * @param request 试算请求（basePrice 必填且 &gt; 0；gradeId / gradeCode 二选一）
     * @return 试算结果
     */
    PricePreviewResult pricePreview(PricePreviewRequest request);

    /**
     * 取启用中的成色等级（按 gradeId 或 gradeCode，二选一）。
     *
     * @param gradeId   等级ID（可空）
     * @param gradeCode 等级编码（可空）
     * @return 成色等级；不存在时抛 404
     */
    ConditionGrade requireGrade(Long gradeId, String gradeCode);
}
