package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 等级借阅额度对照（`GET /borrow/rule/level-quota` 的 `data`）。
 *
 * <p>合并「等级相关额度」（{@code biz_member_level}）与「全局规则」（{@code biz_borrow_rule}），
 * 供 A-57 规则配置页与 B-18 说明页共用。
 *
 * @author tbgbook
 */
@Data
public class LevelQuotaView implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 等级额度列表 */
    private List<LevelQuota> levels = new ArrayList<>();

    /** 全局规则 */
    private BorrowRule globalRule;

    /** 相关 sys_config 键值（biz.borrow.* / biz.points.*） */
    private Map<String, String> configKeys = new LinkedHashMap<>();

    /** 单等级额度 */
    @Data
    public static class LevelQuota implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 等级ID */
        private Long levelId;

        /** 等级名 */
        private String levelName;

        /** 折扣率 */
        private BigDecimal discountRate;

        /** 可借册数上限 */
        private Integer borrowQtyLimit;

        /** 借期（天） */
        private Integer borrowDays;

        /** 押金金额 */
        private BigDecimal depositAmount;

        /** 是否允许续借（0否 1是） */
        private Integer canRenew;
    }
}
