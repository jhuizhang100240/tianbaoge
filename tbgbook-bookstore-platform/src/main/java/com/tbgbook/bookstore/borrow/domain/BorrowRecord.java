package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅单对象 biz_borrow（页面 A-53~A-56、B-17，FR-4.5.2 / FR-4.5.3 / FR-4.5.8）。
 *
 * <p><b>一单一册</b>（设计决策 7）：借 3 本产生 3 个借阅单，续借 / 罚金 / 赔偿都按册计算。
 * 借还**不走本资源 CRUD**：借出 {@code POST /borrow/lend}、归还 {@code POST /borrow/return}、
 * 续借 {@code POST /borrow/record/{borrowId}/renew}。
 *
 * <p>{@code dueTime = borrowTime + 等级借期}；{@code depositAmount} 是借出时押金快照，
 * 会员后续改等级不影响本单。逾期天数以**自然日**为单位，**归还当日不计入**。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_borrow")
public class BorrowRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：1在借 */
    public static final int STATUS_BORROWING = 1;

    /** 状态：2已归还 */
    public static final int STATUS_RETURNED = 2;

    /** 状态：3已逾期 */
    public static final int STATUS_OVERDUE = 3;

    /** 状态：4已赔偿 */
    public static final int STATUS_COMPENSATED = 4;

    /** 图书状况：1完好 */
    public static final int CONDITION_GOOD = 1;

    /** 图书状况：2轻微破损 */
    public static final int CONDITION_SLIGHT = 2;

    /** 图书状况：3严重破损 */
    public static final int CONDITION_SERIOUS = 3;

    /** 借阅单ID */
    @TableId(value = "borrow_id", type = IdType.AUTO)
    private Long borrowId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 借阅单号（BR+yyyyMMddHHmmss+4 位流水） */
    @TableField("borrow_no")
    private String borrowNo;

    /** 会员ID */
    @NotNull(message = "会员ID不能为空")
    @TableField("member_id")
    private Long memberId;

    /** 会员卡号（冗余，锁定当时值） */
    @TableField("member_no")
    private String memberNo;

    /** 会员昵称（冗余，锁定当时值） */
    @TableField("member_name")
    private String memberName;

    /** 借阅时等级ID（锁定当时额度） */
    @TableField("level_id")
    private Long levelId;

    /** 图书ID */
    @NotNull(message = "图书ID不能为空")
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余） */
    @TableField("title")
    private String title;

    /** 图书条码（BC+...，二手册条码；正价书为空） */
    @TableField("bar_code")
    private String barCode;

    /** 取书库位ID */
    @TableField("location_id")
    private Long locationId;

    /** 本单押金快照 */
    @TableField("deposit_amount")
    private BigDecimal depositAmount;

    /** 借出时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("borrow_time")
    private LocalDateTime borrowTime;

    /** 应还时间（borrowTime + 等级借期） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("due_time")
    private LocalDateTime dueTime;

    /** 实际归还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("return_time")
    private LocalDateTime returnTime;

    /** 已续借次数 */
    @TableField("renew_count")
    private Integer renewCount;

    /** 是否逾期（0否 1是） */
    @TableField("is_overdue")
    private Integer isOverdue;

    /** 逾期天数（归还当日不计入） */
    @TableField("overdue_days")
    private Integer overdueDays;

    /** 逾期罚金 */
    @TableField("fine_amount")
    private BigDecimal fineAmount;

    /** 已缴罚金 */
    @TableField("fine_paid")
    private BigDecimal finePaid;

    /** 是否已走赔偿流程（0否 1是） */
    @TableField("is_compensated")
    private Integer isCompensated;

    /** 状态（1在借 2已归还 3已逾期 4已赔偿） */
    @TableField("status")
    private Integer status;

    /** 办理人 */
    @TableField("operator_by")
    private String operatorBy;

    /** 查询用：借出时间区间（契约 {@code params[beginTime]} / {@code params[endTime]}，不落库） */
    @JsonIgnore
    @TableField(exist = false)
    private java.util.Map<String, Object> params;

    /**
     * 通用查询参数（首次访问时初始化）。
     *
     * @return 参数映射（beginTime / endTime 等）
     */
    public java.util.Map<String, Object> getParams()
    {
        if (params == null)
        {
            params = new java.util.HashMap<>();
        }
        return params;
    }

    public void setParams(java.util.Map<String, Object> params)
    {
        this.params = params;
    }
}
