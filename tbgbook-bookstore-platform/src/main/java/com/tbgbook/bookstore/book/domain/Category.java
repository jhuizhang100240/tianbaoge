package com.tbgbook.bookstore.book.domain;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书分类对象 biz_category
 *
 * <p>二级分类树（需求 §4.1.5，页面 A-09 用 T3「树 + 列表」模板）。
 *
 * <p><b>两个字段由服务端维护，不接受客户端传入</b>（接口文档「图书分类」业务约束）：
 * <ul>
 *   <li>{@code ancestors} —— 祖级列表，按 {@code parentId} 自动推导（如二级分类为 {@code 0,1}）</li>
 *   <li>{@code categoryLevel} —— 层级，由父分类的层级 +1 得出（只允许两级）</li>
 * </ul>
 * 客户端即使传了这两个字段也会被 service 覆盖，见 {@code CategoryServiceImpl#fillTreeFields}。
 *
 * <p><b>{@code storeId}</b>：单店部署（`store_id` 恒为 1），库列为
 * {@code NOT NULL DEFAULT 1}。实体不显式赋值 —— MP 的插入策略会跳过 null 字段，
 * 由数据库默认值兜住，因此 Java 侧不出现硬编码的 1。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：
 * <ol>
 *   <li>删掉生成器从列注释里误解析出的 {@code readConverterExp}（如
 *       {@code 冗=余，用于删除前提示引用数量}）—— 那是散文不是枚举映射</li>
 *   <li>补 Jakarta Validation 注解（NFR-M2）</li>
 *   <li>补 {@code children} 瞬态字段，供前端树表使用</li>
 * </ol>
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_category")
public class Category extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    @TableId(value = "category_id", type = IdType.AUTO)
    private Long categoryId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充，不接受客户端传入） */
    @TableField("store_id")
    private Long storeId;

    /** 父分类ID（0=一级） */
    @TableField("parent_id")
    private Long parentId;

    /** 祖级列表，逗号分隔。**服务端维护**，如二级分类为 {@code 0,1} */
    @TableField("ancestors")
    private String ancestors;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 个字符")
    @TableField("category_name")
    private String categoryName;

    /** 层级（1一级 2二级）。**服务端维护**，由父分类层级 +1 得出 */
    @TableField("category_level")
    private Integer categoryLevel;

    /** 显示顺序 */
    @TableField("order_num")
    private Long orderNum;

    /**
     * 关联图书数（冗余统计）。
     *
     * <p>列表查询时由 XML 里的子查询实时算出（不用同步任务维护），
     * 用途是删除前提示引用数量。
     */
    @TableField(exist = false)
    private Long bookCount;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值只能是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除） */
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    /** 子分类（瞬态，供前端树表使用；不落库） */
    @TableField(exist = false)
    private List<Category> children;
}
