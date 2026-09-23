package com.tbgbook.bookstore.book.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教材清单对象 biz_textbook_list（FR-4.1.8 / 页面 A-12 清单管理）
 *
 * <p><b>四类由服务端维护、客户端传了也不算的字段</b>（接口文档「教材清单」业务约束）：
 * <ul>
 *   <li>{@code listNo} —— 服务端按 {@code TB} + {@code yyyyMMddHHmmss} + 4 位随机生成</li>
 *   <li>{@code bookKindCount} / {@code reserveQty} / {@code arrivedQty} / {@code pickedQty}
 *       —— 由明细聚合，**每次明细增删改后重算**</li>
 *   <li>{@code publishTime} —— 只在「发布」时写入</li>
 * </ul>
 * 因此它们都不在请求体字段清单里；本实体保留是为了读接口能返回。
 * 服务端的清除动作集中在 {@code TextbookListServiceImpl#addTextbookList} 与
 * {@code #updateTextbookList}，见那里的注释。
 *
 * <p><b>状态流转</b>（接口文档）：草稿(1) → 发布(2) → 结束(3)，单向。
 * 发布走 {@code POST /book/textbook/{listId}/publish}、结束走 {@code .../finish}
 * （契约里早有这两个动作端点，不在本表 CRUD 里）。{@code PUT} 也接受 {@code status}，
 * 但**走同一套流转校验**，不允许跳级或回退 —— 详见 {@code TextbookListServiceImpl#updateTextbookList}。
 *
 * <p><b>本表没有 {@code del_flag} 也没有 {@code version}</b>：它是单据而非基础数据
 * （根 CLAUDE.md §6.2），删除即物理删除（明细随主表级联删）。没有乐观锁列，
 * 因此状态流转用**带 {@code status} 条件的单条 UPDATE** 防并发重复提交，
 * 拿 0 行即视为并发冲突。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_textbook_list")
public class TextbookList extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 清单ID */
    @TableId(value = "list_id", type = IdType.AUTO)
    private Long listId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 清单编号（服务端生成） */
    @TableField("list_no")
    private String listNo;

    /** 清单名称 */
    @NotBlank(message = "清单名称不能为空")
    @Size(max = 100, message = "清单名称不能超过 100 个字符")
    @TableField("list_name")
    private String listName;

    /** 学期（如 2026 秋季） */
    @NotBlank(message = "学期不能为空")
    @Size(max = 20, message = "学期不能超过 20 个字符")
    @TableField("term")
    private String term;

    /** 适用专业 */
    @Size(max = 100, message = "适用专业不能超过 100 个字符")
    @TableField("major")
    private String major;

    /** 适用院系 */
    @Size(max = 100, message = "适用院系不能超过 100 个字符")
    @TableField("college")
    private String college;

    /** 教材品种数（服务端由明细聚合） */
    @TableField("book_kind_count")
    private Integer bookKindCount;

    /** 预订总数（服务端由明细聚合） */
    @TableField("reserve_qty")
    private Integer reserveQty;

    /** 到货总数（服务端由明细聚合） */
    @TableField("arrived_qty")
    private Integer arrivedQty;

    /** 已取书总数（服务端由明细聚合） */
    @TableField("picked_qty")
    private Integer pickedQty;

    /** 状态（1草稿 2已发布 3已结束） */
    @Min(value = 1, message = "状态取值必须是 1草稿 / 2已发布 / 3已结束")
    @Max(value = 3, message = "状态取值必须是 1草稿 / 2已发布 / 3已结束")
    @TableField("status")
    private Integer status;

    /** 发布时间（服务端在发布时写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /** 结束时间（结束时可传计划时间；不传则由服务端在结束时补当前时刻） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;
}
