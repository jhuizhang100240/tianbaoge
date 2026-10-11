package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 即将到期积分查询结果（`GET /member/point/expiring` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class PointExpiringResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细列表 */
    private List<PointExpiringItem> list = new ArrayList<>();

    /** 涉及会员数 */
    private Integer memberCount = 0;

    /** 涉及积分总数 */
    private Integer totalPoints = 0;
}
