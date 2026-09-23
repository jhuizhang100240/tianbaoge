package com.tbgbook.bookstore.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教材扩展信息对象 biz_book_textbook
 *
 * <p><b>与图书是 1:1</b>（接口文档业务约束）：非教材**没有行**；
 * 教材类图书（{@code biz_book.book_type = 2}）**必须维护**
 * {@code major} / {@code term} / {@code college} 三个字段（FR-4.1 规则 6）。
 *
 * <p><b>主键即外键</b>：{@code book_id} 既是本表主键也是指向 {@code biz_book} 的外键，
 * 因此 {@code @TableId} 用 {@link IdType#INPUT}（值由客户端提供，**不是自增**）。
 * 零物理外键（`docs/db/README.md` §6.1），引用存在性由 service 校验。
 *
 * <p><b>本表没有 {@code del_flag}</b> —— 它是图书的扩展属性而非独立业务实体，
 * 删除即物理删除（不会丢历史：历史在图书的变更日志里）。因此实体**不加**
 * {@code @TableLogic}，{@code deleteByIds} 就是真删除。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_book_textbook")
public class BookTextbook extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 图书ID（主键即外键） */
    @TableId(value = "book_id", type = IdType.INPUT)
    private Long bookId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 适用专业 */
    @NotBlank(message = "适用专业不能为空")
    @Size(max = 100, message = "适用专业不能超过 100 个字符")
    @TableField("major")
    private String major;

    /** 适用学期（如 2026 秋季） */
    @NotBlank(message = "适用学期不能为空")
    @Size(max = 20, message = "适用学期不能超过 20 个字符")
    @TableField("term")
    private String term;

    /** 开课院系 */
    @NotBlank(message = "开课院系不能为空")
    @Size(max = 100, message = "开课院系不能超过 100 个字符")
    @TableField("college")
    private String college;

    /** 课程名称 */
    @Size(max = 100, message = "课程名称不能超过 100 个字符")
    @TableField("course_name")
    private String courseName;

    /** 是否必修（1必修 0选修） */
    @Min(value = 0, message = "是否必修取值 0 或 1")
    @Max(value = 1, message = "是否必修取值 0 或 1")
    @TableField("is_required")
    private Integer isRequired;
}
