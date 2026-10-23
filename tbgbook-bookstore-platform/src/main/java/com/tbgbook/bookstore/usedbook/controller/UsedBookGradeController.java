package com.tbgbook.bookstore.usedbook.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.PricePreviewRequest;
import com.tbgbook.bookstore.usedbook.service.IUsedBookGradeService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 二手书成色等级 Controller（页面 A-42，FR-4.2.3）。
 *
 * <p>默认三档：九九新 1.00 / 九成新 0.90 / 八成新 0.80；系数可后台调整（C3）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/usedbook/grade")
public class UsedBookGradeController extends BaseController
{
    private final IUsedBookGradeService gradeService;

    /**
     * 查询成色等级列表（可按编码 / 名称 / 状态与创建时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:list')")
    @GetMapping("/list")
    public TableDataInfo list(ConditionGrade grade)
    {
        startPage();
        List<ConditionGrade> list = gradeService.selectConditionGradeList(grade);
        return getDataTable(list);
    }

    /**
     * 获取成色等级详情。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:list')")
    @GetMapping("/{gradeId}")
    public AjaxResult getInfo(@PathVariable("gradeId") Long gradeId)
    {
        return success(gradeService.selectConditionGradeById(gradeId));
    }

    /**
     * 新增成色等级（gradeCode 同店唯一）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:add')")
    @Log(title = "二手书成色", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ConditionGrade grade)
    {
        return toAjax(gradeService.addConditionGrade(grade));
    }

    /**
     * 修改成色等级（定价系数可调，无需改代码；支持部分字段，必填与范围校验在 Service 内按合并后的值执行）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:edit')")
    @Log(title = "二手书成色", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ConditionGrade grade)
    {
        return toAjax(gradeService.updateConditionGrade(grade));
    }

    /**
     * 删除成色等级（被回收明细 / 二手书库存引用的档位禁止删除）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:remove')")
    @Log(title = "二手书成色", businessType = BusinessType.DELETE)
    @DeleteMapping("/{gradeIds}")
    public AjaxResult remove(@PathVariable Long[] gradeIds)
    {
        return toAjax(gradeService.removeConditionGrades(gradeIds));
    }

    /**
     * 回收价试算（基准价 × 成色系数；C3 定价系数的验证入口）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:grade:pricePreview')")
    @PostMapping("/price-preview")
    public AjaxResult pricePreview(@RequestBody PricePreviewRequest request)
    {
        return success(gradeService.pricePreview(request));
    }
}
