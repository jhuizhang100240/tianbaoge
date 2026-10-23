package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 二手书回收明细对象 biz_recycle_item（页面 A-41、B-14）。
 *
 * <p><b>同时保存「会员提交成色」与「店员判定成色」</b>：{@code submitGradeId/Name} 与
 * {@code judgeGradeId/Name} 成对存储；两者不一致时置 {@code isMismatch=1} 且
 * **强制要求填 {@code judgeNote} 判定说明**（R6 纠纷防范）。
 * {@code finalPrice = basePrice × priceFactor}；验收合格后生成 {@code biz_used_book} 并回填
 * {@code usedBookId}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_recycle_item")
public class RecycleItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细状态：1待验收 */
    public static final int STATUS_PENDING = 1;

    /** 明细状态：2合格已收 */
    public static final int STATUS_ACCEPTED = 2;

    /** 明细状态：3拒收 */
    public static final int STATUS_REJECTED = 3;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 回收单ID */
    @TableField("recycle_id")
    private Long recycleId;

    /** ISBN */
    @TableField("isbn")
    private String isbn;

    /** 书名 */
    @TableField("title")
    private String title;

    /** 会员提交的成色等级ID */
    @TableField("submit_grade_id")
    private Long submitGradeId;

    /** 会员提交的成色名称（快照） */
    @TableField("submit_grade_name")
    private String submitGradeName;

    /** 店员判定的成色等级ID */
    @TableField("judge_grade_id")
    private Long judgeGradeId;

    /** 店员判定的成色名称（快照） */
    @TableField("judge_grade_name")
    private String judgeGradeName;

    /** 判定是否不一致（0否 1是） */
    @TableField("is_mismatch")
    private Integer isMismatch;

    /** 成色判定说明（不一致时必填） */
    @TableField("judge_note")
    private String judgeNote;

    /** 基准价 */
    @TableField("base_price")
    private BigDecimal basePrice;

    /** 成色系数 */
    @TableField("price_factor")
    private BigDecimal priceFactor;

    /** 最终回收价（基准价 × 系数） */
    @TableField("final_price")
    private BigDecimal finalPrice;

    /** 状态（1待验收 2合格已收 3拒收） */
    @TableField("item_status")
    private Integer itemStatus;

    /** 拒收原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 验收合格后生成的二手书副本ID */
    @TableField("used_book_id")
    private Long usedBookId;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 查询用：创建时间区间（契约 params[*]，不落库） */
    @JsonIgnore
    @TableField(exist = false)
    private Map<String, Object> params;

    /**
     * 通用查询参数（首次访问时初始化）。
     *
     * @return 参数映射（beginTime / endTime）
     */
    public Map<String, Object> getParams()
    {
        if (params == null)
        {
            params = new HashMap<>();
        }
        return params;
    }

    public void setParams(Map<String, Object> params)
    {
        this.params = params;
    }
}
