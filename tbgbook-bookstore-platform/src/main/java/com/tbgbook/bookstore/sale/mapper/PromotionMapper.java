package com.tbgbook.bookstore.sale.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.Promotion;

/**
 * 促销活动 Mapper 接口。
 *
 * @author tbgbook
 */
public interface PromotionMapper extends BaseMapper<Promotion>
{
    /**
     * 查询促销活动列表。
     *
     * @param promotion 查询条件
     * @return 活动集合
     */
    List<Promotion> selectPromotionList(Promotion promotion);

    /**
     * 取 {@code biz_promotion} 的下一个自增主键（用于预生成 promotionNo）。
     *
     * <p>{@code promotion_no} 是 {@code NOT NULL} 且**无默认值**、还带唯一索引
     * （{@code uk_promotion_no}），因此不能「先插入再回填」—— 插入时就必须给出一个
     * 既非空又唯一的号。手法同 {@code StockInMapper#selectNextInId}。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextPromotionId();

    /**
     * 取**此刻生效且覆盖指定图书**的活动（按优先级倒序）。
     *
     * <p>覆盖判定：{@code scopeType=1全场} / {@code =2} 且 scope_value 含该分类 / 
     * {@code =3} 且 scope_value 含该图书；另外只要该图书在 {@code biz_promotion_book} 里有行也算命中
     * （限时特价/专享价就是靠明细配活动价的）。
     *
     * <p>⚠️ 只取 {@code status=2进行中} 且当前时间落在 {@code start_time}~{@code end_time} 内的；
     * **同一图书命中多个时按 {@code priority} 倒序返回，调用方取第一条**
     * —— 契约业务约束：命中多个活动时按优先级**取一、不相加**。
     */
    /**
     * 取与本活动**时间区间重叠的其它进行中活动**（供启用时的**非阻断告警**）。
     *
     * <p>重叠判定用标准的区间相交：{@code a.start <= b.end AND b.start <= a.end}。
     * 两端任一为空时视为「无边界」（不限时间），按重叠处理 —— 运营没填时间就说不清边界，
     * 与其漏报不如报出来让人自己看。
     *
     * <p>⚠️ 只返回**其它**活动（{@code promotion_id <> 自身}），且只要 {@code status=2 进行中} 的：
     * 草稿/已结束/已停用的活动不会真的参与计算，报出来是噪音。
     *
     * @param promotionId 当前活动ID（排除自身）
     * @param startTime   当前活动开始时间（可空）
     * @param endTime     当前活动结束时间（可空）
     * @return 冲突的其它进行中活动（按优先级倒序，最多若干条）
     */
    List<Promotion> selectActiveForBook(@Param("bookId") Long bookId,
            @Param("categoryId") Long categoryId,
            @Param("nowTime") LocalDateTime nowTime);

    List<Promotion> selectRunningOverlapping(@Param("promotionId") Long promotionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
