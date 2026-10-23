package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.MemberBrief;

/**
 * 收银台所需的**会员域只读查询**（跨域读，属阶段五的表；只读不写）。
 *
 * <p>放一个专用 Mapper 而不是复用会员域的 Service：会员域实现要等阶段五，
 * 而收银台现在就得出票。集中在这一处，将来阶段五落地时只需把这里的调用换成 Service。
 */
public interface MemberReadMapper
{
    /**
     * 按手机号 / 会员卡号 / 会员码识别会员（三者都当作同一个 {@code keyword} 精确匹配）。
     *
     * @param keyword 手机号 / 会员卡号 / 会员码
     * @return 会员简要信息；未命中返回 null
     */
    MemberBrief selectMemberByKeyword(@Param("keyword") String keyword);

    /** 按会员ID取简要信息（试算要等级折扣率与可用积分） */
    MemberBrief selectMemberByKeywordById(@Param("memberId") Long memberId);

    /** 储值账户本金余额（消费按「本金:赠送金」比例分摊扣减用） */
    java.math.BigDecimal selectPrincipalBalance(@Param("memberId") Long memberId);

    /** 某会员**当前可用**的券（未使用且在有效期内） */
    List<Coupon> selectUsableCoupons(@Param("memberId") Long memberId);

    /** 在借册数（`biz_borrow` 状态为借出中） */
    int countBorrowing(@Param("memberId") Long memberId);

    /** 逾期未还册数 */
    int countOverdue(@Param("memberId") Long memberId);
}
