package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 续借结果（`POST /borrow/record/{borrowId}/renew` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class RenewResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 续借前应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beforeDueTime;

    /** 续借后应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime afterDueTime;

    /** 本次延长天数 */
    private Integer renewDays;

    /** 累计已续借次数 */
    private Integer renewCount;

    /** 剩余可续借次数 */
    private Integer remainRenewTimes;
}
