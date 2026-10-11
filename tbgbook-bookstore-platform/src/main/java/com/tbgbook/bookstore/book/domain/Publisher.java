package com.tbgbook.bookstore.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.tbgbook.common.annotation.Excel;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出版社对象 biz_publisher
 *
 * <p>本类是**项目样板**：后续 80 余张业务表照此风格复制
 * （{@code plan/backend-plan.md} 阶段一任务 1.4）。
 *
 * <p>由代码生成器产出后人工裁剪，改动点：
 * <ol>
 *   <li>补 Jakarta Validation 注解（NFR-M2 参数校验前置）——
 *       生成器不产出校验注解，全靠人工补</li>
 *   <li>删除无用的 {@code @Version} import（该表无 version 列）。
 *       <b>生成器模板已同步修复</b>（{@code vm/java/domain.java.vm}），
 *       后续表不再需要手工删这一行</li>
 * </ol>
 *
 * <p>注意：本表**无 version 列**，因此并发修改不受乐观锁保护。
 * 出版社是低频维护的基础数据，可接受；有并发写风险的实体（如 {@code biz_stock}）
 * 必须带 version 列并在实体上加 {@code @Version}（后端 CLAUDE.md §五）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_publisher")
public class Publisher extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 出版社ID */
    @TableId(value = "publisher_id", type = IdType.AUTO)
    private Long publisherId;

    /** 出版社名称 */
    @Excel(name = "出版社名称")
    @NotBlank(message = "出版社名称不能为空")
    @Size(max = 100, message = "出版社名称不能超过 100 个字符")
    @TableField("publisher_name")
    private String publisherName;

    /** 联系人 */
    @Excel(name = "联系人")
    @Size(max = 50, message = "联系人不能超过 50 个字符")
    @TableField("contact_person")
    private String contactPerson;

    /** 联系电话 */
    @Excel(name = "联系电话")
    @Size(max = 20, message = "联系电话不能超过 20 个字符")
    @TableField("contact_phone")
    private String contactPhone;

    /** 地址 */
    @Excel(name = "地址")
    @Size(max = 200, message = "地址不能超过 200 个字符")
    @TableField("address")
    private String address;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    @Pattern(regexp = "^[01]$", message = "状态取值只能是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除）。**基础数据表**；`READ_ONLY` 防止客户端注入，见 `Promotion#delFlag` */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    /**
     * 该出版社下的图书品种数（冗余统计，{@code biz_book} 无物理外键，查询时聚合）。
     * 非表字段，用于列表页展示与删除前的引用提示。
     */
    @TableField(exist = false)
    private Long bookCount;
}
