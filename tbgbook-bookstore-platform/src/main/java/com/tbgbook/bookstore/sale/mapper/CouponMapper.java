package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.Coupon;

/**
 * 会员优惠券实例 Mapper 接口。
 *
 * @author tbgbook
 */
public interface CouponMapper extends BaseMapper<Coupon>
{
    /**
     * 查询会员优惠券实例列表。
     *
     * @param coupon 查询条件
     * @return 券集合
     */
    List<Coupon> selectCouponList(Coupon coupon);

    /**
     * 取 {@code biz_coupon} 的下一个自增主键（用于预生成 couponCode）。
     *
     * <p>手法同 {@code StockInMapper#selectNextInId}。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextCouponId();

    /**
     * 统计某会员持有某模板的**未过期且未使用**券数（每人限领校验用）。
     *
     * <p>只算 {@code status=1未使用} 且未过期的：已使用的券不该继续占用限领额度
     * （否则会员用掉一张就再也领不到了）；已过期的同样不占。
     *
     * @param memberId   会员ID
     * @param templateId 模板ID
     * @return 持有张数
     */
    int countUsableByMemberAndTemplate(@Param("memberId") Long memberId,
            @Param("templateId") Long templateId);

    /**
     * 把已过期但仍标为「未使用」的券批量置为「已过期」（**惰性判定**）。
     *
     * <p>契约明写「过期由定时任务或查询时惰性判定」。本方法供查询路径调用：
     * 每次列表查询前顺手把该会员（或全店）已过期的券状态刷正，
     * 避免出现「状态写着未使用、其实早过期」的脏数据让收银误判可用。
     *
     * @param memberId 限定会员；为 {@code null} 时刷全店
     * @return 更新行数
     */
    int expireOutdated(@Param("memberId") Long memberId);
}
