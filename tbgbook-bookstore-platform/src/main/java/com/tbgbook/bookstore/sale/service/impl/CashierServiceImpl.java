package com.tbgbook.bookstore.sale.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.sale.domain.CashierScanRequest;
import com.tbgbook.bookstore.sale.domain.CashierScanResult;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.domain.MemberBrief;
import com.tbgbook.bookstore.sale.domain.MemberLookupRequest;
import com.tbgbook.bookstore.sale.domain.MemberLookupResult;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.domain.TrialRequest;
import com.tbgbook.bookstore.sale.domain.TrialResult;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.mapper.MemberReadMapper;
import com.tbgbook.bookstore.sale.service.ICashierService;
import com.tbgbook.bookstore.sale.support.PricingSupport;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 收银台基础层 Service 实现（阶段四步骤③）。
 *
 * <p>三处要紧的口径：
 * <ol>
 *   <li><b>三个接口都不落库、不动库存</b>：收银开单过程的即时计算，真正的扣减在结算（步骤④）</li>
 *   <li><b>扫码时没有会员上下文</b>（契约的 scan 请求体只有 code/qty），故展示的是
 *       **散客视角的活动价**；会员价与等级折扣在试算时才叠加。收银员先扫码、后识别会员，
 *       这个顺序与页面流程一致</li>
 *   <li><b>优惠按金额比例分摊到行</b>（§4.3 规则 5）：{@code shareAmount} 是毛利核算口径，
 *       不分摊就没法算单品的毛利</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CashierServiceImpl implements ICashierService
{
    /** 积分抵扣上限比例（§3.2 / `biz.points.deduct.ratio`，默认 20%） */
    private static final String CFG_POINTS_RATIO = "biz.points.deduct.ratio";

    /** 折扣审批：折扣率低于此值需店长审批（`biz.discount.approval.rate`，默认 0.7） */
    private static final String CFG_APPROVAL_RATE = "biz.discount.approval.rate";

    /** 折扣审批：折扣总额超订单金额此比例需审批（`biz.discount.approval.ratio`，默认 0.3） */
    private static final String CFG_APPROVAL_RATIO = "biz.discount.approval.ratio";

    /** 二手书副本条码前缀（`biz_secondhand_stock.bar_code`） */
    private static final String USED_BARCODE_PREFIX = "BC";

    /** 积分与金额的换算：**100 积分 = 1 元** */
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    private static final int SCALE = 2;

    private final BookMapper bookMapper;

    private final StockMapper stockMapper;

    private final MemberReadMapper memberReadMapper;

    private final CouponMapper couponMapper;

    private final CouponTemplateMapper couponTemplateMapper;

    private final PricingSupport pricingSupport;

    private final ISysConfigService configService;

    // ==================================================================
    // 扫码加商品
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public CashierScanResult scan(CashierScanRequest request)
    {
        String code = request.getCode() == null ? "" : request.getCode().trim();

        // 二手书按册扫条码：二手书池属**阶段六**，明确拒绝而不是当正价书查
        if (code.toUpperCase().startsWith(USED_BARCODE_PREFIX))
        {
            throw BizErrors.notFound("二手书条码「" + code + "」暂不可用：二手书库存池属阶段六，"
                    + "尚未接入收银台；请先用正价书结算");
        }

        Book book = findByCode(code);
        if (book == null)
        {
            throw BizErrors.notFound("条码「" + code + "」未命中任何图书");
        }
        Integer status = book.getStatus() == null ? null : Integer.valueOf(book.getStatus());
        if (Objects.equals(status, 2) || Objects.equals(status, 4))
        {
            throw BizErrors.ruleViolated("「" + book.getTitle() + "」已"
                    + (Objects.equals(status, 2) ? "下架" : "停售") + "，不能加入购物车");
        }

        // 扫码时无会员上下文 → 按散客解析（会员价与等级折扣在试算时叠加）
        PricingSupport.PriceInfo price = pricingSupport.resolve(book, null);
        int qty = (request.getQty() == null) ? 1 : request.getQty();

        CashierScanResult result = new CashierScanResult();
        result.setBookType(OrderItem.BOOK_TYPE_NORMAL);
        result.setBookId(book.getBookId());
        result.setUsedBookId(0L);
        result.setIsbn(book.getIsbn());
        result.setTitle(book.getTitle());
        result.setPriceType(price.getPriceType());
        result.setOriginPrice(price.getOriginPrice());
        result.setUnitPrice(price.getUnitPrice());
        result.setQty(qty);
        result.setQtyAvailable(availableOf(book.getBookId()));
        return result;
    }

    /** 按 ISBN 或图书编号（BK...）精确命中 */
    private Book findByCode(String code)
    {
        Book byIsbn = bookMapper.selectOne(Wrappers.<Book>lambdaQuery().eq(Book::getIsbn, code).last("limit 1"));
        if (byIsbn != null)
        {
            return byIsbn;
        }
        return bookMapper.selectOne(Wrappers.<Book>lambdaQuery().eq(Book::getBookNo, code).last("limit 1"));
    }

    // ==================================================================
    // 会员识别
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public MemberLookupResult memberLookup(MemberLookupRequest request)
    {
        MemberBrief member = memberReadMapper.selectMemberByKeyword(request.getKeyword().trim());
        if (member == null)
        {
            throw BizErrors.notFound("未查到会员（识别内容：" + request.getKeyword() + "）");
        }

        MemberLookupResult result = new MemberLookupResult();
        result.setMemberId(member.getMemberId());
        result.setMemberNo(member.getMemberNo());
        result.setNickname(member.getNickname());
        // NFR-S8：手机号**必须脱敏**，收银只需核对尾号
        result.setPhone(maskPhone(member.getPhone()));
        result.setLevelId(member.getLevelId());
        result.setLevelName(member.getLevelName());
        result.setDiscountRate(member.getDiscountRate());
        result.setPoints(member.getPoints() == null ? 0 : member.getPoints());
        result.setBalanceTotal(member.getBalanceTotal());

        List<MemberLookupResult.Coupon> coupons = new ArrayList<>();
        for (Coupon c : memberReadMapper.selectUsableCoupons(member.getMemberId()))
        {
            MemberLookupResult.Coupon one = new MemberLookupResult.Coupon();
            one.setCouponId(c.getCouponId());
            one.setCouponCode(c.getCouponCode());
            one.setCouponName(c.getCouponName());
            one.setFaceValue(c.getFaceValue());
            one.setThresholdAmount(c.getThresholdAmount());
            one.setValidEnd(c.getValidEnd() == null ? null : c.getValidEnd().toString());
            coupons.add(one);
        }
        result.setCoupons(coupons);

        MemberLookupResult.BorrowStatus borrow = new MemberLookupResult.BorrowStatus();
        borrow.setBorrowingCount(memberReadMapper.countBorrowing(member.getMemberId()));
        borrow.setHasOverdue(memberReadMapper.countOverdue(member.getMemberId()) > 0);
        result.setBorrowStatus(borrow);
        return result;
    }

    /** 手机号脱敏：138****1234（NFR-S8） */
    private String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ==================================================================
    // 优惠试算
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public TrialResult trial(TrialRequest request)
    {
        Long memberId = (request.getMemberId() == null) ? 0L : request.getMemberId();
        MemberBrief member = (memberId > 0)
                ? memberReadMapper.selectMemberByKeywordById(memberId) : null;
        Long levelId = (member == null) ? null : member.getLevelId();

        // ---------- 逐行定价（① 单品折扣 → ② 会员等级折扣） ----------
        List<TrialResult.Line> lines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;   // 商品合计（原价小计）
        BigDecimal singleDiscount = BigDecimal.ZERO;
        BigDecimal memberDiscount = BigDecimal.ZERO;
        BigDecimal afterMember = BigDecimal.ZERO;   // 单品+会员折扣后的金额（券与积分的计算基数）
        boolean specialPrice = false;

        for (TrialRequest.Line req : request.getItems())
        {
            if (req.getUsedBookId() != null && req.getUsedBookId() > 0)
            {
                throw BizErrors.notFound("二手书（副本ID=" + req.getUsedBookId()
                        + "）暂不可用：二手书库存池属阶段六，尚未接入收银台");
            }
            Book book = (req.getBookId() == null) ? null : bookMapper.selectById(req.getBookId());
            if (book == null)
            {
                throw BizErrors.notFound("图书不存在（图书ID=" + req.getBookId() + "）");
            }
            int qty = (req.getQty() == null) ? 1 : req.getQty();
            PricingSupport.PriceInfo price = pricingSupport.resolve(book, levelId);
            specialPrice = specialPrice || price.isSpecialPrice();

            BigDecimal q = BigDecimal.valueOf(qty);
            BigDecimal originLine = price.getOriginPrice().multiply(q);
            BigDecimal singleLine = price.getSingleDiscount().multiply(q);
            BigDecimal memberLine = price.getMemberDiscount().multiply(q);
            BigDecimal amountLine = price.getUnitPrice().multiply(q);

            TrialResult.Line line = new TrialResult.Line();
            line.setBookId(book.getBookId());
            line.setUsedBookId(0L);
            line.setIsbn(book.getIsbn());
            line.setTitle(book.getTitle());
            line.setBookType(OrderItem.BOOK_TYPE_NORMAL);
            line.setPriceType(price.getPriceType());
            line.setOriginPrice(price.getOriginPrice());
            line.setUnitPrice(price.getUnitPrice());
            line.setQty(qty);
            line.setAmount(scale(originLine));
            line.setSingleDiscount(scale(singleLine));
            line.setMemberDiscount(scale(memberLine));
            lines.add(line);

            totalAmount = totalAmount.add(originLine);
            singleDiscount = singleDiscount.add(singleLine);
            memberDiscount = memberDiscount.add(memberLine);
            afterMember = afterMember.add(amountLine);
        }

        // ---------- ③ 优惠券 ----------
        BigDecimal couponAmount = BigDecimal.ZERO;
        if (request.getCouponId() != null && request.getCouponId() > 0)
        {
            couponAmount = resolveCoupon(request.getCouponId(), memberId, afterMember, specialPrice);
        }

        // ---------- ④ 积分抵扣 ----------
        BigDecimal pointAmount = BigDecimal.ZERO;
        int pointUsed = 0;
        if (request.getUsePoints() != null && request.getUsePoints() > 0)
        {
            pointUsed = request.getUsePoints();
            pointAmount = BigDecimal.valueOf(pointUsed).divide(POINTS_PER_YUAN, SCALE, RoundingMode.DOWN);

            if (member == null)
            {
                throw BizErrors.ruleViolated("散客不能使用积分抵扣");
            }
            if (pointUsed > nzi(member.getPoints()))
            {
                throw BizErrors.ruleViolated("使用积分（" + pointUsed + "）超过会员可用积分（"
                        + nzi(member.getPoints()) + "）");
            }
            // 契约：422 积分抵扣超过订单金额的 biz.points.deduct.ratio（默认 20%）
            BigDecimal base = afterMember.subtract(couponAmount);
            BigDecimal cap = base.multiply(pointsRatio()).setScale(SCALE, RoundingMode.DOWN);
            if (pointAmount.compareTo(cap) > 0)
            {
                throw BizErrors.ruleViolated("积分抵扣 " + pointAmount + " 元超过订单金额的 "
                        + pointsRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros()
                        + "%（上限 " + cap + " 元）");
            }
        }

        BigDecimal discountTotal = singleDiscount.add(memberDiscount).add(couponAmount).add(pointAmount);
        BigDecimal payAmount = totalAmount.subtract(discountTotal).max(BigDecimal.ZERO);

        // ---------- 优惠按金额比例分摊到行（§4.3 规则 5，毛利核算口径） ----------
        allocate(lines, couponAmount, pointAmount, afterMember);

        // ---------- 折扣审批判定（§3.2 价格规则 2） ----------
        List<String> reasons = new ArrayList<>();
        if (totalAmount.signum() > 0)
        {
            BigDecimal rate = payAmount.divide(totalAmount, 4, RoundingMode.HALF_UP);
            if (rate.compareTo(approvalRate()) < 0)
            {
                reasons.add("实付折扣率 " + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros()
                        + "% 低于阈值 " + approvalRate().multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros() + "%（" + CFG_APPROVAL_RATE + "）");
            }
            BigDecimal ratio = discountTotal.divide(totalAmount, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(approvalRatio()) > 0)
            {
                reasons.add("优惠合计 " + scale(discountTotal) + " 元超过订单金额的 "
                        + approvalRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros()
                        + "%（" + CFG_APPROVAL_RATIO + "）");
            }
        }

        TrialResult result = new TrialResult();
        result.setTotalAmount(scale(totalAmount));
        result.setSingleDiscount(scale(singleDiscount));
        result.setMemberDiscount(scale(memberDiscount));
        result.setCouponAmount(scale(couponAmount));
        result.setPointAmount(scale(pointAmount));
        result.setDiscountTotal(scale(discountTotal));
        result.setPayAmount(scale(payAmount));
        result.setPointUsed(pointUsed);
        // 消费 1 元 = 1 积分（向下取整）
        result.setPointGain(payAmount.setScale(0, RoundingMode.DOWN).intValue());
        result.setNeedApproval(reasons.isEmpty() ? 0 : 1);
        result.setApprovalReasons(reasons);
        result.setHasSpecialPrice(specialPrice ? 1 : 0);
        result.setItems(lines);
        return result;
    }

    /** 券的可用性与抵扣额；不可用一律 422 并说明原因（契约：券不满足门槛或已过期） */
    private BigDecimal resolveCoupon(Long couponId, Long memberId, BigDecimal amount, boolean specialPrice)
    {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null)
        {
            throw BizErrors.notFound("优惠券不存在（券ID=" + couponId + "）");
        }
        if (!Objects.equals(coupon.getMemberId(), memberId))
        {
            throw BizErrors.ruleViolated("该券不属于当前会员");
        }
        if (!Objects.equals(coupon.getStatus(), Coupon.STATUS_UNUSED))
        {
            throw BizErrors.ruleViolated("该券已"
                    + (Objects.equals(coupon.getStatus(), Coupon.STATUS_USED) ? "使用" : "过期"));
        }
        if (coupon.getValidEnd() != null && coupon.getValidEnd().isBefore(LocalDateTime.now()))
        {
            throw BizErrors.ruleViolated("该券已过期");
        }
        // §3.2 价格规则 3：特价商品**不参与优惠券叠加**
        if (specialPrice)
        {
            throw BizErrors.ruleViolated("本单含特价商品，不能使用优惠券（特价不参与优惠券叠加）");
        }
        if (coupon.getThresholdAmount() != null && amount.compareTo(coupon.getThresholdAmount()) < 0)
        {
            throw BizErrors.ruleViolated("商品金额 " + scale(amount) + " 未达该券的使用门槛 "
                    + coupon.getThresholdAmount());
        }

        CouponTemplate template = couponTemplateMapper.selectById(coupon.getTemplateId());
        if (template != null && Objects.equals(template.getCouponType(), CouponTemplate.TYPE_DISCOUNT)
                && template.getDiscountRate() != null && template.getDiscountRate().signum() > 0)
        {
            // 折扣券：按折扣率算
            return amount.subtract(amount.multiply(template.getDiscountRate()))
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }
        // 满减券：抵扣额取「面额」与「金额」的较小者（避免抵成负数）
        return nz(coupon.getFaceValue()).min(amount).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 把券与积分按**各行的实付金额占比**分摊到行（§4.3 规则 5）。
     *
     * <p>最后一行用「总额减前面各行的和」兜底，避免四舍五入后分摊之和与总额差一分钱 ——
     * 那是毛利报表对不上的经典来源。
     */
    private void allocate(List<TrialResult.Line> lines, BigDecimal couponAmount,
            BigDecimal pointAmount, BigDecimal afterMember)
    {
        BigDecimal couponLeft = couponAmount;
        BigDecimal pointLeft = pointAmount;
        for (int i = 0; i < lines.size(); i++)
        {
            TrialResult.Line line = lines.get(i);
            boolean last = (i == lines.size() - 1);
            BigDecimal ratio = (afterMember.signum() == 0) ? BigDecimal.ZERO
                    : line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQty()))
                            .divide(afterMember, 6, RoundingMode.HALF_UP);

            BigDecimal cShare = last ? couponLeft : couponAmount.multiply(ratio).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal pShare = last ? pointLeft : pointAmount.multiply(ratio).setScale(SCALE, RoundingMode.HALF_UP);
            couponLeft = couponLeft.subtract(cShare);
            pointLeft = pointLeft.subtract(pShare);

            line.setCouponShare(cShare);
            line.setPointShare(pShare);
            BigDecimal paid = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQty()))
                    .subtract(cShare).subtract(pShare);
            line.setShareAmount(scale(paid));
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private int availableOf(Long bookId)
    {
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery().eq(Stock::getBookId, bookId));
        int sum = 0;
        for (Stock s : rows)
        {
            sum += (s.getQtyAvailable() == null ? 0 : s.getQtyAvailable());
        }
        return sum;
    }

    private BigDecimal pointsRatio()
    {
        return cfgDecimal(CFG_POINTS_RATIO, new BigDecimal("0.2"));
    }

    private BigDecimal approvalRate()
    {
        return cfgDecimal(CFG_APPROVAL_RATE, new BigDecimal("0.7"));
    }

    private BigDecimal approvalRatio()
    {
        return cfgDecimal(CFG_APPROVAL_RATIO, new BigDecimal("0.3"));
    }

    private BigDecimal cfgDecimal(String key, BigDecimal fallback)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return new BigDecimal(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private BigDecimal scale(BigDecimal v)
    {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
