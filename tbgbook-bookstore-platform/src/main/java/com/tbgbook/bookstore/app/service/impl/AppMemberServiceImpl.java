package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.analysis.domain.ExportTask;
import com.tbgbook.bookstore.analysis.domain.request.CreateExportRequest;
import com.tbgbook.bookstore.analysis.service.IExportTaskService;
import com.tbgbook.bookstore.app.domain.request.AppCancelMemberRequest;
import com.tbgbook.bookstore.app.domain.request.AppCouponClaimRequest;
import com.tbgbook.bookstore.app.domain.request.AppProfileUpdateRequest;
import com.tbgbook.bookstore.app.domain.request.AppRechargeRequest;
import com.tbgbook.bookstore.app.domain.request.AppSubscribeAuthRequest;
import com.tbgbook.bookstore.app.mapper.AppMemberMapper;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppMemberService;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.service.IBorrowRecordService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.MessageTemplate;
import com.tbgbook.bookstore.member.domain.PointExpiringItem;
import com.tbgbook.bookstore.member.domain.PointExpiringResult;
import com.tbgbook.bookstore.member.domain.StoredActivity;
import com.tbgbook.bookstore.member.domain.SubscribeAuth;
import com.tbgbook.bookstore.member.mapper.MemberStudentMapper;
import com.tbgbook.bookstore.member.mapper.SubscribeAuthMapper;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.bookstore.member.service.IMemberLevelService;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.service.ICouponService;
import com.tbgbook.bookstore.sale.service.ICouponTemplateService;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 会员端会员中心 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class AppMemberServiceImpl implements IAppMemberService
{
    private static final long STORE_ID = 1L;

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Map<Integer, String> POINT_LABELS = Map.of(
            1, "消费获得",
            2, "抵扣消耗",
            3, "到期清零",
            4, "人工调整",
            5, "退货返还");

    private static final Map<Integer, String> AMOUNT_LABELS = Map.of(
            1, "本金充值",
            2, "赠送金",
            3, "消费扣本金",
            4, "消费扣赠送金",
            5, "退款返本金",
            6, "退款返赠送金");

    private static final Map<Integer, String> MESSAGE_LABELS = Map.of(
            1, "到货通知",
            2, "借阅到期",
            3, "催还通知",
            4, "积分到期",
            5, "活动通知",
            6, "系统消息",
            7, "人工回复");

    private final AppMemberMapper appMemberMapper;

    private final IMemberService memberService;

    private final IMemberLevelService memberLevelService;

    private final IMemberPointService pointService;

    private final IMemberBalanceService balanceService;

    private final MemberStudentMapper memberStudentMapper;

    private final IMemberMessageService messageService;

    private final ICouponService couponService;

    private final ICouponTemplateService couponTemplateService;

    private final IBorrowRecordService borrowRecordService;

    private final SubscribeAuthMapper subscribeAuthMapper;

    private final IExportTaskService exportTaskService;

    private final ISysConfigService configService;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> center()
    {
        Long memberId = AppContext.requireMemberId();
        Member member = requireMember(memberId);
        List<MemberLevel> levels = activeLevels();
        MemberLevel currentLevel = findCurrentLevel(levels, member.getLevelId());
        MemberLevel nextLevel = levels.stream()
                .filter(level -> currentLevel == null
                        || nzi(level.getLevelRank()) > nzi(currentLevel.getLevelRank()))
                .findFirst().orElse(null);
        int growth = nzi(member.getGrowthValue());
        int currentThreshold = currentLevel == null ? 0 : nzi(currentLevel.getGrowthThreshold());
        int nextThreshold = nextLevel == null ? currentThreshold : nzi(nextLevel.getGrowthThreshold());
        int progress = nextLevel == null || nextThreshold <= currentThreshold ? 100
                : Math.max(0, Math.min(100,
                        (growth - currentThreshold) * 100 / (nextThreshold - currentThreshold)));

        Map<String, Object> growthData = new LinkedHashMap<>();
        growthData.put("current", growth);
        growthData.put("nextLevelThreshold", nextLevel == null ? null : nextThreshold);
        growthData.put("progressPercent", progress);

        Map<String, Object> expiring = expiringPointsData(memberId);
        Map<String, Object> pointsData = new LinkedHashMap<>();
        pointsData.put("current", nzi(member.getPoints()));
        pointsData.put("expiringSoon", expiring.get("totalExpiring"));
        List<Map<String, Object>> expiringItems = maps(expiring.get("items"));
        pointsData.put("expireDate", expiringItems.isEmpty() ? null : expiringItems.get(0).get("expireTime"));

        MemberBalance account = balanceAccount(memberId);
        Map<String, Object> balanceData = new LinkedHashMap<>();
        balanceData.put("principal", money(account.getPrincipalBalance()));
        balanceData.put("gift", money(account.getGiftBalance()));
        balanceData.put("total", money(account.getTotalBalance()));

        List<Coupon> coupons = memberCoupons(memberId);
        long availableCoupons = coupons.stream().filter(this::couponAvailable).count();
        BorrowRecord borrowQuery = new BorrowRecord();
        borrowQuery.setMemberId(memberId);
        long borrowing = borrowRecordService.selectBorrowRecordList(borrowQuery).stream()
                .filter(row -> Integer.valueOf(1).equals(row.getStatus())
                        || Integer.valueOf(3).equals(row.getStatus()))
                .count();
        Map<String, Object> borrowSummary = new LinkedHashMap<>();
        borrowSummary.put("currentBorrowed", borrowing);
        borrowSummary.put("quotaLimit", currentLevel == null ? 0 : nzi(currentLevel.getBorrowQtyLimit()));

        int unread = appMemberMapper.countUnreadMessages(memberId);
        Map<String, Object> entryCounts = new LinkedHashMap<>();
        entryCounts.put("messages", unread);
        entryCounts.put("expiringPoints", expiring.get("totalExpiring"));
        entryCounts.put("coupons", availableCoupons);

        Map<String, Object> memberData = new LinkedHashMap<>();
        memberData.put("memberNo", member.getMemberNo());
        memberData.put("nickname", member.getNickname());
        memberData.put("avatar", member.getAvatar());
        memberData.put("phone", maskPhone(member.getPhone()));
        memberData.put("levelId", member.getLevelId());
        memberData.put("levelName", member.getLevelName());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("member", memberData);
        data.put("growth", growthData);
        data.put("points", pointsData);
        data.put("balance", balanceData);
        data.put("deposit", money(member.getDepositBalance()));
        data.put("couponCount", availableCoupons);
        data.put("borrowSummary", borrowSummary);
        data.put("entryCounts", entryCounts);
        return data;
    }

    @Override
    public Map<String, Object> levels()
    {
        Long memberId = AppContext.getMemberId();
        Member member = memberId == null ? null : memberService.selectMemberById(memberId);
        int growth = member == null ? 0 : nzi(member.getGrowthValue());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MemberLevel level : activeLevels())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("levelId", level.getLevelId());
            row.put("levelName", level.getLevelName());
            row.put("levelRank", level.getLevelRank());
            row.put("growthThreshold", level.getGrowthThreshold());
            row.put("discountRate", level.getDiscountRate());
            row.put("borrowQtyLimit", level.getBorrowQtyLimit());
            row.put("borrowDays", level.getBorrowDays());
            row.put("depositAmount", level.getDepositAmount());
            row.put("canRenew", level.getCanRenew());
            row.put("isCurrent", member != null && member.getLevelId() != null
                    && member.getLevelId().equals(level.getLevelId()));
            row.put("unlocked", growth >= nzi(level.getGrowthThreshold()));
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("levels", rows);
        data.put("currentGrowth", growth);
        data.put("pointsRules", List.of(
                "消费每 1 元获得 1 积分",
                "积分有效期为获得之日起 24 个月",
                "积分到期前 30 天推送提醒"));
        data.put("growthRules", List.of(
                "消费每 1 元获得 1 成长值",
                "按时归还图书获得成长值",
                "完成评价获得成长值"));
        return data;
    }

    @Override
    public Map<String, Object> pointLogs(Integer changeType, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        int safeSize = pageSize(pageSize);
        int safeNum = pageNum(pageNum);
        LocalDateTime begin = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = appMemberMapper.selectPointLogs(memberId, changeType,
                begin, end, (safeNum - 1) * safeSize, safeSize);
        for (Map<String, Object> row : rows)
        {
            row.put("changeType", asInt(row.get("changeType")));
            row.put("changeTypeLabel", POINT_LABELS.getOrDefault(asInt(row.get("changeType")), "积分变动"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", appMemberMapper.countPointLogs(memberId, changeType, begin, end));
        data.put("summary", pointSummary(memberId));
        return data;
    }

    @Override
    public Map<String, Object> expiringPoints()
    {
        return expiringPointsData(AppContext.requireMemberId());
    }

    @Override
    public Map<String, Object> balance()
    {
        Long memberId = AppContext.requireMemberId();
        MemberBalance account = balanceAccount(memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("principal", money(account.getPrincipalBalance()));
        data.put("gift", money(account.getGiftBalance()));
        data.put("total", money(account.getTotalBalance()));
        data.put("giftExpireInfo", null);
        data.put("activities", activities());
        data.put("recentLogs", decoratedBalanceLogs(appMemberMapper.selectBalanceLogs(
                memberId, null, null, null, 0, 5)));
        return data;
    }

    @Override
    public Map<String, Object> balanceLogs(Integer amountType, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        int safeSize = pageSize(pageSize);
        int safeNum = pageNum(pageNum);
        LocalDateTime begin = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", decoratedBalanceLogs(appMemberMapper.selectBalanceLogs(memberId, amountType,
                begin, end, (safeNum - 1) * safeSize, safeSize)));
        data.put("total", appMemberMapper.countBalanceLogs(memberId, amountType, begin, end));
        return data;
    }

    @Override
    public Map<String, Object> recharge(AppRechargeRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        requireMember(memberId);
        balanceAccount(memberId);
        BigDecimal amount = money(request.getRechargeAmount());
        if (amount.signum() <= 0)
        {
            throw BizErrors.ruleViolated("充值金额必须大于 0");
        }

        StoredActivity activity = null;
        BigDecimal gift = BigDecimal.ZERO.setScale(2);
        if (request.getActivityId() != null)
        {
            activity = balanceService.selectStoredActivityById(request.getActivityId());
            LocalDateTime now = LocalDateTime.now();
            if (activity == null || !Integer.valueOf(1).equals(activity.getStatus()))
            {
                throw BizErrors.ruleViolated("充值活动不存在或已停用");
            }
            if (activity.getStartTime() != null && now.isBefore(activity.getStartTime()))
            {
                throw BizErrors.ruleViolated("充值活动尚未开始");
            }
            if (activity.getEndTime() != null && now.isAfter(activity.getEndTime()))
            {
                throw BizErrors.ruleViolated("充值活动已结束");
            }
            if (amount.compareTo(money(activity.getRechargeAmount())) != 0)
            {
                throw BizErrors.ruleViolated("充值金额不在活动档位内");
            }
            gift = money(activity.getGiftAmount());
        }

        String rechargeNo = "RC" + LocalDateTime.now().format(NO_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        params.put("package", "prepay_id=mock-" + rechargeNo);
        params.put("signType", "RSA");
        params.put("paySign", "MOCK");

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("rechargeNo", rechargeNo);
        pending.put("memberId", memberId);
        pending.put("rechargeAmount", amount);
        pending.put("giftAmount", gift);
        pending.put("activityId", request.getActivityId());
        pending.put("status", 1);
        pending.put("createTime", LocalDateTime.now().format(DATE_TIME_FORMAT));
        try
        {
            RBucket<String> bucket = redissonClient.getBucket("tbgbook:app:recharge:" + rechargeNo);
            bucket.set(objectMapper.writeValueAsString(pending), 30, TimeUnit.MINUTES);
        }
        catch (Exception e)
        {
            throw BizErrors.serverError("创建充值单失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rechargeNo", rechargeNo);
        data.put("giftAmount", gift);
        data.put("wxPayParams", params);
        data.put("status", 1);
        return data;
    }

    @Override
    public Map<String, Object> couponList(Integer status, boolean usableOnly)
    {
        Long memberId = AppContext.requireMemberId();
        List<Coupon> coupons = memberCoupons(memberId);
        Map<Long, CouponTemplate> templates = couponTemplates(coupons);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Coupon coupon : coupons)
        {
            boolean available = couponAvailable(coupon);
            if (status != null && !status.equals(coupon.getStatus()))
            {
                continue;
            }
            if (usableOnly && !available)
            {
                continue;
            }
            CouponTemplate template = templates.get(coupon.getTemplateId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("couponId", coupon.getCouponId());
            row.put("couponCode", coupon.getCouponCode());
            row.put("couponName", coupon.getCouponName());
            row.put("couponType", template == null ? 1 : template.getCouponType());
            row.put("faceValue", coupon.getFaceValue());
            row.put("thresholdAmount", coupon.getThresholdAmount());
            row.put("validStart", coupon.getValidStart());
            row.put("validEnd", coupon.getValidEnd());
            row.put("daysLeft", daysLeft(now, coupon.getValidEnd()));
            row.put("status", coupon.getStatus());
            row.put("scopeDesc", scopeDesc(template));
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> !Integer.valueOf(1).equals(row.get("status")))
                .thenComparing(row -> (LocalDateTime) row.get("validEnd"),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> (Long) row.get("couponId"), Comparator.reverseOrder()));
        long available = coupons.stream().filter(this::couponAvailable).count();
        long expiringSoon = coupons.stream().filter(this::couponAvailable)
                .filter(row -> daysLeft(now, row.getValidEnd()) <= 7).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("available", available);
        summary.put("expiringSoon", expiringSoon);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("summary", summary);
        return data;
    }

    @Override
    public Map<String, Object> couponCenter()
    {
        Long memberId = AppContext.requireMemberId();
        LocalDateTime now = LocalDateTime.now();
        List<CouponTemplate> templates = couponTemplateService.list(
                new LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getStatus, CouponTemplate.STATUS_PUBLISHED)
                        .eq(CouponTemplate::getGrantType, CouponTemplate.GRANT_CLAIM_CENTER)
                        .and(wrapper -> wrapper.isNull(CouponTemplate::getValidStart)
                                .or().le(CouponTemplate::getValidStart, now))
                        .and(wrapper -> wrapper.isNull(CouponTemplate::getValidEnd)
                                .or().gt(CouponTemplate::getValidEnd, now))
                        .orderByAsc(CouponTemplate::getTemplateId));
        List<Coupon> memberCoupons = memberCoupons(memberId);
        Map<Long, Long> received = new HashMap<>();
        for (Coupon coupon : memberCoupons)
        {
            received.merge(coupon.getTemplateId(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CouponTemplate template : templates)
        {
            long mine = received.getOrDefault(template.getTemplateId(), 0L);
            int total = nzi(template.getTotalQty());
            int issued = nzi(template.getIssuedQty());
            String reason = null;
            if (mine >= nzi(template.getPerLimit()))
            {
                reason = "已达每人限领数量";
            }
            else if (total > 0 && issued >= total)
            {
                reason = "发放总量已领完";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("templateId", template.getTemplateId());
            row.put("templateNo", template.getTemplateNo());
            row.put("couponName", template.getCouponName());
            row.put("couponType", template.getCouponType());
            row.put("faceValue", template.getFaceValue());
            row.put("thresholdAmount", template.getThresholdAmount());
            row.put("validityType", template.getValidityType());
            row.put("validDays", template.getValidDays());
            row.put("scopeDesc", scopeDesc(template));
            row.put("canClaim", reason == null);
            row.put("reason", reason);
            row.put("remainQty", total == 0 ? null : Math.max(0, total - issued));
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claimCoupon(AppCouponClaimRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        requireMember(memberId);
        RLock lock = redissonClient.getLock("tbgbook:app:coupon:claim:template:"
                + request.getTemplateId());
        boolean releaseAfterMethod = true;
        try
        {
            lock.lock(8, TimeUnit.SECONDS);
            if (TransactionSynchronizationManager.isSynchronizationActive())
            {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
                {
                    @Override
                    public void afterCompletion(int status)
                    {
                        if (lock.isHeldByCurrentThread())
                        {
                            lock.unlock();
                        }
                    }
                });
                releaseAfterMethod = false;
            }
            CouponTemplate template = couponTemplateService.selectCouponTemplateById(request.getTemplateId());
            if (template == null || !Integer.valueOf(CouponTemplate.STATUS_PUBLISHED).equals(template.getStatus()))
            {
                throw BizErrors.ruleViolated("该券不在领券中心开放");
            }
            if (!Integer.valueOf(CouponTemplate.GRANT_CLAIM_CENTER).equals(template.getGrantType()))
            {
                throw BizErrors.ruleViolated("该券不在领券中心开放");
            }
            LocalDateTime now = LocalDateTime.now();
            if (template.getValidStart() != null && now.isBefore(template.getValidStart()))
            {
                throw BizErrors.ruleViolated("该优惠券尚未开始发放");
            }
            if (template.getValidEnd() != null && !now.isBefore(template.getValidEnd()))
            {
                throw BizErrors.ruleViolated("该优惠券已过期");
            }
            List<Coupon> existing = couponService.selectCouponList(couponQuery(memberId, template.getTemplateId()));
            if (existing.size() >= nzi(template.getPerLimit()))
            {
                throw BizErrors.ruleViolated("已达每人限领数量");
            }
            if (appMemberMapper.incrementCouponIssued(template.getTemplateId()) != 1)
            {
                throw BizErrors.ruleViolated("发放总量已领完");
            }
            Coupon coupon = new Coupon();
            coupon.setStoreId(STORE_ID);
            coupon.setCouponCode("CP" + now.format(NO_TIME_FORMAT)
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            coupon.setTemplateId(template.getTemplateId());
            coupon.setMemberId(memberId);
            coupon.setCouponName(template.getCouponName());
            coupon.setFaceValue(template.getFaceValue());
            coupon.setThresholdAmount(template.getThresholdAmount());
            if (Integer.valueOf(CouponTemplate.VALIDITY_RELATIVE).equals(template.getValidityType()))
            {
                coupon.setValidStart(now);
                coupon.setValidEnd(now.plusDays(nzi(template.getValidDays())));
            }
            else
            {
                coupon.setValidStart(template.getValidStart());
                coupon.setValidEnd(template.getValidEnd());
            }
            coupon.setStatus(Coupon.STATUS_UNUSED);
            coupon.setUseOrderId(0L);
            coupon.setGrantBatchId(0L);
            coupon.setReceiveTime(now);
            coupon.setCreateTime(now);
            if (!couponService.save(coupon))
            {
                throw BizErrors.serverError("领取优惠券失败");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("couponId", coupon.getCouponId());
            data.put("couponCode", coupon.getCouponCode());
            data.put("couponName", coupon.getCouponName());
            data.put("faceValue", coupon.getFaceValue());
            data.put("thresholdAmount", coupon.getThresholdAmount());
            data.put("validEnd", coupon.getValidEnd());
            return data;
        }
        finally
        {
            if (releaseAfterMethod && lock.isHeldByCurrentThread())
            {
                lock.unlock();
            }
        }
    }

    @Override
    public Map<String, Object> records(String types, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        Set<String> selected = new HashSet<>();
        if (!StringUtils.hasText(types))
        {
            selected.addAll(List.of("order", "borrow", "point", "recycle"));
        }
        else
        {
            for (String value : types.split(","))
            {
                String type = value.trim();
                if (!Set.of("order", "borrow", "point", "recycle").contains(type))
                {
                    throw BizErrors.badRequest("记录类型仅支持 order/borrow/point/recycle");
                }
                selected.add(type);
            }
        }
        int safeSize = pageSize(pageSize);
        int safeNum = pageNum(pageNum);
        LocalDateTime begin = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        boolean order = selected.contains("order");
        boolean borrow = selected.contains("borrow");
        boolean point = selected.contains("point");
        boolean recycle = selected.contains("recycle");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", appMemberMapper.selectTimeline(memberId, order, borrow, point, recycle,
                begin, end, (safeNum - 1) * safeSize, safeSize));
        data.put("total", appMemberMapper.countTimeline(memberId, order, borrow, point, recycle,
                begin, end));
        data.put("summary", appMemberMapper.selectTimelineSummary(memberId));
        return data;
    }

    @Override
    public Map<String, Object> messages(Integer msgType, Integer readStatus, int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        int safeSize = pageSize(pageSize);
        int safeNum = pageNum(pageNum);
        List<Map<String, Object>> rows = appMemberMapper.selectMessages(memberId, msgType, readStatus,
                (safeNum - 1) * safeSize, safeSize);
        for (Map<String, Object> row : rows)
        {
            row.put("msgType", asInt(row.get("msgType")));
            row.put("isRead", asInt(row.get("isRead")));
            row.put("msgTypeLabel", MESSAGE_LABELS.getOrDefault(asInt(row.get("msgType")), "消息"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", appMemberMapper.countMessages(memberId, msgType, readStatus));
        data.put("unreadCount", appMemberMapper.countUnreadMessages(memberId));
        return data;
    }

    @Override
    public Map<String, Object> markMessagesRead(List<Long> messageIds)
    {
        Long memberId = AppContext.requireMemberId();
        int rows = appMemberMapper.markMessagesRead(memberId, messageIds);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("readCount", rows);
        return data;
    }

    @Override
    public Map<String, Object> profile()
    {
        return profileData(requireMember(AppContext.requireMemberId()));
    }

    @Override
    public Map<String, Object> updateProfile(AppProfileUpdateRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        Member member = requireMember(memberId);
        if (request.getNickname() != null)
        {
            member.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null)
        {
            member.setAvatar(request.getAvatar());
        }
        if (request.getGender() != null)
        {
            member.setGender(request.getGender());
        }
        if (request.getBirthday() != null)
        {
            member.setBirthday(request.getBirthday());
        }
        memberService.updateMember(member);
        return profileData(requireMember(memberId));
    }

    @Override
    public Map<String, Object> reportSubscribeAuth(AppSubscribeAuthRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        MessageTemplate template = activeMessageTemplates().stream()
                .filter(row -> request.getTemplateCode().equals(row.getTemplateCode()))
                .findFirst().orElseThrow(() -> BizErrors.notFound("订阅消息模板", request.getTemplateCode()));
        int times = Math.max(1, Math.min(request.getAuthTimes() == null ? 1 : request.getAuthTimes(), 20));
        LocalDateTime now = LocalDateTime.now();
        SubscribeAuth existing = subscribeAuthMapper.selectOne(
                new LambdaQueryWrapper<SubscribeAuth>()
                        .eq(SubscribeAuth::getMemberId, memberId)
                        .eq(SubscribeAuth::getTemplateCode, template.getTemplateCode())
                        .orderByDesc(SubscribeAuth::getAuthId)
                        .last("limit 1"));
        SubscribeAuth auth;
        if (existing == null)
        {
            auth = new SubscribeAuth();
            auth.setMemberId(memberId);
            auth.setTemplateCode(template.getTemplateCode());
            auth.setAuthStatus(1);
            auth.setAuthTimes(times);
            auth.setUsedTimes(0);
            auth.setAuthTime(now);
            auth.setCreateTime(now);
            auth.setUpdateTime(now);
            subscribeAuthMapper.insert(auth);
        }
        else
        {
            auth = existing;
            auth.setAuthStatus(1);
            auth.setAuthTimes(nzi(existing.getAuthTimes()) + times);
            auth.setAuthTime(now);
            auth.setUpdateTime(now);
            subscribeAuthMapper.updateById(auth);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authId", auth.getAuthId());
        data.put("authStatus", 1);
        data.put("remainTimes", Math.max(0, nzi(auth.getAuthTimes()) - nzi(auth.getUsedTimes())));
        return data;
    }

    @Override
    public Map<String, Object> cancel(AppCancelMemberRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        if (!"确认注销".equals(request.getConfirmText()))
        {
            throw BizErrors.ruleViolated("确认文本不匹配");
        }
        String reason = StringUtils.hasText(request.getReason())
                ? request.getReason().trim() : "会员主动注销";
        memberService.cancelMember(memberId, reason);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", memberId);
        data.put("status", 4);
        data.put("anonymized", 1);
        return data;
    }

    @Override
    public Map<String, Object> dataExport()
    {
        Long memberId = AppContext.requireMemberId();
        Member member = requireMember(memberId);
        CreateExportRequest request = new CreateExportRequest();
        request.setExportType("member");
        request.setTaskName("我的数据导出-" + member.getMemberNo());
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("memberId", memberId);
        request.setFilters(filters);
        request.setCostFields(false);
        ExportTask task = exportTaskService.createForMember(memberId, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("status", task.getStatus());
        return data;
    }

    @Override
    public Map<String, Object> subscribeConfig()
    {
        Long memberId = AppContext.requireMemberId();
        SubscribeAuth query = new SubscribeAuth();
        query.setMemberId(memberId);
        List<SubscribeAuth> auths = messageService.selectSubscribeAuthList(query);
        Map<String, SubscribeAuth> latest = new LinkedHashMap<>();
        for (SubscribeAuth auth : auths)
        {
            latest.put(auth.getTemplateCode(), auth);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (MessageTemplate template : activeMessageTemplates())
        {
            SubscribeAuth auth = latest.get(template.getTemplateCode());
            int remain = auth == null ? 0
                    : Math.max(0, nzi(auth.getAuthTimes()) - nzi(auth.getUsedTimes()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateCode", template.getTemplateCode());
            item.put("templateName", template.getTemplateName());
            item.put("msgType", template.getMsgType());
            item.put("wxTemplateId", template.getWxTemplateId());
            item.put("authStatus", auth == null ? 0 : auth.getAuthStatus());
            item.put("remainTimes", remain);
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        return data;
    }

    private Map<String, Object> profileData(Member member)
    {
        MemberStudent student = memberStudentMapper.selectById(member.getMemberId());
        Map<String, Object> studentData = null;
        if (student != null)
        {
            studentData = new LinkedHashMap<>();
            studentData.put("school", student.getSchool());
            studentData.put("major", student.getMajor());
            studentData.put("grade", student.getGrade());
            studentData.put("studentNo", maskStudentNo(student.getStudentNo()));
            studentData.put("certStatus", student.getCertStatus());
        }
        String blockReason = cancelBlockReason(member);
        SubscribeAuth query = new SubscribeAuth();
        query.setMemberId(member.getMemberId());
        boolean subscribed = messageService.selectSubscribeAuthList(query).stream()
                .anyMatch(auth -> Integer.valueOf(1).equals(auth.getAuthStatus())
                        && nzi(auth.getAuthTimes()) > nzi(auth.getUsedTimes()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.getMemberId());
        data.put("memberNo", member.getMemberNo());
        data.put("nickname", member.getNickname());
        data.put("avatar", member.getAvatar());
        data.put("gender", member.getGender());
        data.put("birthday", member.getBirthday());
        data.put("phone", maskPhone(member.getPhone()));
        data.put("phoneBound", StringUtils.hasText(member.getPhone()));
        data.put("student", studentData);
        data.put("canCancel", blockReason == null);
        data.put("cancelBlockReason", blockReason);
        data.put("subscribeAuthStatus", subscribed ? 1 : 0);
        return data;
    }

    private String cancelBlockReason(Member member)
    {
        Map<String, Object> blockers = appMemberMapper.selectCancelBlockers(member.getMemberId());
        List<String> reasons = new ArrayList<>();
        if (asLong(blockers.get("activeBorrows")) > 0)
        {
            reasons.add("存在未还借阅");
        }
        if (money(member.getDepositBalance()).signum() > 0)
        {
            reasons.add("存在在借押金");
        }
        if (money(member.getBalancePrincipal()).add(money(member.getBalanceGift())).signum() > 0)
        {
            reasons.add("储值余额不为 0");
        }
        if (asLong(blockers.get("pendingOrders")) > 0)
        {
            reasons.add("存在待结算订单");
        }
        if (asLong(blockers.get("pendingRecycles")) > 0)
        {
            reasons.add("存在处理中的回收单");
        }
        return reasons.isEmpty() ? null : String.join("；", reasons);
    }

    private Map<String, Object> pointSummary(Long memberId)
    {
        Map<String, Object> expiring = expiringPointsData(memberId);
        Member member = requireMember(memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", nzi(member.getPoints()));
        data.put("expiringSoon", expiring.get("totalExpiring"));
        List<Map<String, Object>> items = maps(expiring.get("items"));
        data.put("expireDate", items.isEmpty() ? null : items.get(0).get("expireTime"));
        return data;
    }

    private Map<String, Object> expiringPointsData(Long memberId)
    {
        int threshold = intConfig("biz.points.expire.notify.days", 30);
        PointExpiringResult result = pointService.selectExpiring(threshold, memberId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (PointExpiringItem item : result.getList())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("remainPoints", item.getRemainPoints());
            row.put("expireTime", item.getExpireTime());
            row.put("daysLeft", item.getDaysLeft());
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalExpiring", result.getTotalPoints());
        data.put("items", items);
        data.put("notifyThresholdDays", threshold);
        return data;
    }

    private List<Map<String, Object>> decoratedBalanceLogs(List<Map<String, Object>> rows)
    {
        for (Map<String, Object> row : rows)
        {
            row.put("amountType", asInt(row.get("amountType")));
            row.put("amountTypeLabel", AMOUNT_LABELS.getOrDefault(asInt(row.get("amountType")), "储值变动"));
        }
        return rows;
    }

    private List<Map<String, Object>> activities()
    {
        StoredActivity query = new StoredActivity();
        query.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StoredActivity activity : balanceService.selectStoredActivityList(query))
        {
            if (activity.getStatus() == null || activity.getStatus() != 1
                    || (activity.getStartTime() != null && now.isBefore(activity.getStartTime()))
                    || (activity.getEndTime() != null && now.isAfter(activity.getEndTime())))
            {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("activityId", activity.getActivityId());
            row.put("activityName", activity.getActivityName());
            row.put("rechargeAmount", activity.getRechargeAmount());
            row.put("giftAmount", activity.getGiftAmount());
            row.put("giftExpireDays", activity.getGiftExpireDays());
            row.put("isRecommend", Integer.valueOf(1).equals(activity.getIsRecommend()));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> !Boolean.TRUE.equals(row.get("isRecommend"))));
        return rows;
    }

    private List<MemberLevel> activeLevels()
    {
        return memberLevelService.list(new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getStatus, "0")
                .orderByAsc(MemberLevel::getLevelRank));
    }

    private MemberLevel findCurrentLevel(List<MemberLevel> levels, Long levelId)
    {
        if (levelId == null)
        {
            return levels.isEmpty() ? null : levels.get(0);
        }
        return levels.stream().filter(level -> levelId.equals(level.getLevelId()))
                .findFirst().orElse(levels.isEmpty() ? null : levels.get(0));
    }

    private MemberBalance balanceAccount(Long memberId)
    {
        MemberBalance query = new MemberBalance();
        query.setMemberId(memberId);
        List<MemberBalance> rows = balanceService.selectMemberBalanceList(query);
        if (rows.isEmpty())
        {
            MemberBalance empty = new MemberBalance();
            empty.setPrincipalBalance(BigDecimal.ZERO);
            empty.setGiftBalance(BigDecimal.ZERO);
            empty.setTotalBalance(BigDecimal.ZERO);
            return empty;
        }
        return rows.get(0);
    }

    private List<Coupon> memberCoupons(Long memberId)
    {
        Coupon query = new Coupon();
        query.setMemberId(memberId);
        return couponService.selectCouponList(query);
    }

    private Coupon couponQuery(Long memberId, Long templateId)
    {
        Coupon query = new Coupon();
        query.setMemberId(memberId);
        query.setTemplateId(templateId);
        return query;
    }

    private Map<Long, CouponTemplate> couponTemplates(List<Coupon> coupons)
    {
        Set<Long> ids = new HashSet<>();
        for (Coupon coupon : coupons)
        {
            if (coupon.getTemplateId() != null)
            {
                ids.add(coupon.getTemplateId());
            }
        }
        Map<Long, CouponTemplate> result = new HashMap<>();
        if (!ids.isEmpty())
        {
            for (CouponTemplate template : couponTemplateService.listByIds(ids))
            {
                result.put(template.getTemplateId(), template);
            }
        }
        return result;
    }

    private boolean couponAvailable(Coupon coupon)
    {
        if (!Integer.valueOf(Coupon.STATUS_UNUSED).equals(coupon.getStatus()))
        {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return (coupon.getValidStart() == null || !now.isBefore(coupon.getValidStart()))
                && (coupon.getValidEnd() == null || now.isBefore(coupon.getValidEnd()));
    }

    private String scopeDesc(CouponTemplate template)
    {
        if (template == null || Integer.valueOf(1).equals(template.getScopeType()))
        {
            return "全场通用";
        }
        String value = StringUtils.hasText(template.getScopeValue()) ? template.getScopeValue() : "";
        return Integer.valueOf(2).equals(template.getScopeType())
                ? "指定范围：" + value : "排除范围：" + value;
    }

    private long daysLeft(LocalDateTime now, LocalDateTime validEnd)
    {
        if (validEnd == null)
        {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), validEnd.toLocalDate()));
    }

    private List<MessageTemplate> activeMessageTemplates()
    {
        MessageTemplate query = new MessageTemplate();
        query.setStatus("0");
        return messageService.selectMessageTemplateList(query);
    }

    private Member requireMember(Long memberId)
    {
        Member member = memberService.selectMemberById(memberId);
        if (member == null)
        {
            throw BizErrors.notFound("会员", memberId);
        }
        return member;
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private int pageNum(int value)
    {
        return Math.max(1, value);
    }

    private int pageSize(int value)
    {
        return Math.max(1, Math.min(value, 100));
    }

    private BigDecimal money(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int nzi(Integer value)
    {
        return value == null ? 0 : value;
    }

    private long asLong(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return bool ? 1L : 0L;
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int asInt(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return bool ? 1 : 0;
        }
        return value instanceof Number number ? number.intValue() : 0;
    }

    private List<Map<String, Object>> maps(Object value)
    {
        if (!(value instanceof List<?> list))
        {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list)
        {
            if (item instanceof Map<?, ?> map)
            {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet())
                {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private String maskPhone(String phone)
    {
        if (!StringUtils.hasText(phone) || phone.length() < 7)
        {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskStudentNo(String studentNo)
    {
        if (!StringUtils.hasText(studentNo) || studentNo.length() <= 4)
        {
            return studentNo;
        }
        if (studentNo.length() <= 6)
        {
            return studentNo.substring(0, 1) + "****" + studentNo.substring(studentNo.length() - 1);
        }
        return studentNo.substring(0, 2) + "****" + studentNo.substring(studentNo.length() - 2);
    }
}
