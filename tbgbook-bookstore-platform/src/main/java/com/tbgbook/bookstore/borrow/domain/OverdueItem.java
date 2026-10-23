package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 逾期清单条目（`GET /borrow/record/overdue` 的 `data.list` 元素）。
 *
 * @author tbgbook
 */
@Data
public class OverdueItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 借阅单ID */
    private Long borrowId;

    /** 借阅单号 */
    private String borrowNo;

    /** 会员ID */
    private Long memberId;

    /** 会员昵称 */
    private String memberName;

    /** 手机号（脱敏由服务端返回，如 138****1234） */
    private String phone;

    /** 书名 */
    private String title;

    /** 图书条码 */
    private String barCode;

    /** 应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueTime;

    /** 逾期天数（自然日，当日不计） */
    private Integer overdueDays;

    /** 试算罚金（已封顶） */
    private BigDecimal fineAmount;

    /** 是否已触顶 */
    private Boolean fineCapped;

    /** 图书定价（计算基数，内部使用，不返回前端） */
    @JsonIgnore
    private BigDecimal calcBase;
}
