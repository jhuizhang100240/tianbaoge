package com.tbgbook.bookstore.member.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.domain.MemberTagRel;
import com.tbgbook.bookstore.member.domain.aggregate.MemberPanoramaView;
import com.tbgbook.bookstore.member.domain.aggregate.MemberPortraitView;
import com.tbgbook.bookstore.member.domain.aggregate.MemberTimelineEntry;
import com.tbgbook.bookstore.member.domain.aggregate.TimelinePage;
import com.tbgbook.bookstore.member.mapper.MemberAnalysisMapper;
import com.tbgbook.bookstore.member.mapper.MemberBalanceMapper;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberStudentMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagRelMapper;
import com.tbgbook.bookstore.member.service.IMemberAnalysisService;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.common.core.domain.model.LoginUser;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 会员全景 / 时间轴 / 画像 Service 实现（阶段五步骤③c-3）。
 *
 * <p><b>自定口径（登记 A41）</b>：
 * <ol>
 *   <li><b>画像窗口</b>：固定近 <b>12 个月</b>（自应用侧 {@code now} 往前 12 个月，A32 同口径）；
 *       RFM 的 R 用「距今天数」而非窗口起点，样本 = 窗口内有订单的全体会员；</li>
 *   <li><b>订单口径</b>：{@code is_red=0} 且 {@code status in (1,2,3,4,6,7)} —— 排除 5已取消；
 *       6已退款 / 7已红冲保留（金额可能为负），与阶段五收银口径一致；</li>
 *   <li><b>客单价</b> = 窗口内实付合计 ÷ 订单数（订单为 0 时 0.00）；分布五档固定桶
 *       {@code 0-50 / 50-100 / 100-200 / 200-500 / 500+}（左闭右开，首档含 0）；</li>
 *   <li><b>活跃时段</b>四段：凌晨 &lt;6 / 上午 6~12 / 下午 12~18 / 晚间 ≥18（无数据的段补零）；</li>
 *   <li><b>品类偏好</b>按窗口内订单明细的 {@code share_amount}（实付分摊）聚合，取金额倒序，
 *       占比 = 该品类金额 ÷ 窗口总实付；</li>
 *   <li><b>RFM 评分</b>：R/F/M 各按同店样本的<b>百分位</b>映射 1~5 分
 *       （R：天数为 0 得 5 分、最长得 1 分；F/M：值越大分越高），
 *       {@code totalScore} = R+F+M（3~15）；样本会员数 &lt; 3 时三项与合计一律返回 null；</li>
 *   <li><b>成本口径</b>：{@code grossMarginAmount}（Σ 实付 − 成本快照×数量）仅当调用方持有
 *       `member:cost:view` 时返回，否则为 null（不报错，前端据此隐藏）；</li>
 *   <li><b>时间轴</b>：订单 / 借阅 / 积分变动 / 储值变动四类 UNION，按时间倒序；
 *       积分按 100 积分 = 1 元折算为元；{@code types} 白名单 order/borrow/point/stored；</li>
 *   <li><b>全景</b>：积分「即将到期」窗口取 {@code biz.points.expire.notify.days}（默认 30 天）；
 *       借阅统计沿用会员注销闸门口径（1在借 / 3已逾期）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberAnalysisServiceImpl implements IMemberAnalysisService
{
    /** 画像窗口月数 */
    private static final int WINDOW_MONTHS = 12;

    /** 积分折算：100 积分 = 1 元 */
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    /** 成本口径权限串（5 个成本权限之一） */
    private static final String COST_PERMISSION = "member:cost:view";

    /** RuoYi 超级管理员通配权限（admin 的 authorities 里是 `*:*:*`，不是逐条权限串） */
    private static final String ALL_PERMISSION = "*:*:*";

    /** 允许的时间轴类型 */
    private static final Set<String> TIMELINE_TYPES =
            new LinkedHashSet<>(List.of("order", "borrow", "point", "stored"));

    /** 时段标签顺序（固定输出顺序，无数据的段补零） */
    private static final String[] PERIODS = { "凌晨", "上午", "下午", "晚间" };

    /** 客单价固定档位 */
    private static final String[] AMOUNT_BUCKETS =
            { "0-50", "50-100", "100-200", "200-500", "500+" };

    /** 借阅状态：1在借 */
    private static final int BORROW_BORROWING = 1;

    /** 借阅状态：3已逾期 */
    private static final int BORROW_OVERDUE = 3;

    /** 券状态 */
    private static final int COUPON_UNUSED = 1;

    private static final int COUPON_USED = 2;

    private static final int COUPON_EXPIRED = 3;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberAnalysisMapper analysisMapper;

    private final MemberBalanceMapper memberBalanceMapper;

    private final MemberStudentMapper memberStudentMapper;

    private final MemberLevelMapper memberLevelMapper;

    private final MemberTagMapper memberTagMapper;

    private final MemberTagRelMapper memberTagRelMapper;

    private final CouponMapper couponMapper;

    private final ISysConfigService configService;

    // ==================================================================
    // 全景
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public MemberPanoramaView panorama(Long memberId)
    {
        Member member = referenceGuard.requireExists(memberMapper, memberId, "会员");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusMonths(WINDOW_MONTHS);

        MemberPanoramaView view = new MemberPanoramaView();
        view.setMember(member);
        view.setStudent(toStudentView(memberId));
        view.setLevel(buildLevel(member, now));
        view.setBalance(memberBalanceMapper.selectOne(new LambdaQueryWrapper<MemberBalance>()
                .eq(MemberBalance::getMemberId, memberId)));
        view.setPoints(buildPoints(member, now));
        view.setCoupons(buildCoupons(memberId));
        view.setTags(buildTags(memberId));
        view.setStats(buildStats(member));
        view.setBorrowStat(buildBorrowStat(memberId));
        view.setPortrait(buildPortraitSummary(memberId, from, now));
        return view;
    }

    /** 教育信息精简视图（学号经 @Sensitive 脱敏） */
    private MemberPanoramaView.StudentView toStudentView(Long memberId)
    {
        MemberStudent student = memberStudentMapper.selectById(memberId);
        if (student == null)
        {
            return null;
        }
        MemberPanoramaView.StudentView view = new MemberPanoramaView.StudentView();
        view.setSchool(student.getSchool());
        view.setStudentNo(student.getStudentNo());
        view.setMajor(student.getMajor());
        view.setGrade(student.getGrade());
        view.setCertStatus(student.getCertStatus());
        return view;
    }

    /** 等级 + 下一等级门槛（下一档 = 启用中 growth_threshold 大于当前值的最小档） */
    private MemberPanoramaView.LevelView buildLevel(Member member, LocalDateTime now)
    {
        MemberPanoramaView.LevelView view = new MemberPanoramaView.LevelView();
        view.setLevelId(member.getLevelId());
        view.setLevelName(member.getLevelName());
        int growth = member.getGrowthValue() == null ? 0 : member.getGrowthValue();
        view.setGrowthValue(growth);
        List<MemberLevel> levels = memberLevelMapper.selectList(
                new LambdaQueryWrapper<MemberLevel>()
                        .eq(MemberLevel::getStatus, "0")
                        .orderByAsc(MemberLevel::getLevelRank));
        for (MemberLevel level : levels)
        {
            int threshold = level.getGrowthThreshold() == null ? 0 : level.getGrowthThreshold();
            if (threshold > growth)
            {
                view.setNextLevelId(level.getLevelId());
                view.setNextLevelName(level.getLevelName());
                view.setNextGrowthThreshold(threshold);
                view.setGrowthGap(threshold - growth);
                return view;
            }
        }
        view.setGrowthGap(0);
        return view;
    }

    /** 积分概览：总账 + 即将到期（窗口由配置 biz.points.expire.notify.days 决定） */
    private MemberPanoramaView.PointsView buildPoints(Member member, LocalDateTime now)
    {
        int days = notifyDays();
        MemberPanoramaView.PointsView view = new MemberPanoramaView.PointsView();
        view.setPoints(member.getPoints() == null ? 0 : member.getPoints());
        view.setExpiringDays(days);
        Integer expiring = analysisMapper.selectExpiringPoints(member.getMemberId(),
                now.toLocalDate().plusDays(1).atStartOfDay(), now.plusDays(days));
        view.setExpiringPoints(expiring == null ? 0 : expiring);
        return view;
    }

    /** 持券统计（按状态三分类，不把「已过期」算进未使用） */
    private MemberPanoramaView.CouponsView buildCoupons(Long memberId)
    {
        MemberPanoramaView.CouponsView view = new MemberPanoramaView.CouponsView();
        int unused = couponCount(memberId, COUPON_UNUSED, null);
        // 未使用但已过期 → 前端看到的是「已过期」
        int effectiveUnused = couponCount(memberId, COUPON_UNUSED, LocalDateTime.now());
        int expiredUnused = unused - effectiveUnused;
        int used = couponCount(memberId, COUPON_USED, null);
        int expired = couponCount(memberId, COUPON_EXPIRED, null) + expiredUnused;
        view.setUnused(effectiveUnused);
        view.setUsed(used);
        view.setExpired(expired);
        view.setTotal(effectiveUnused + used + expired);
        return view;
    }

    /** 券计数：extra=null 时按状态全量；非 null 时只算 valid_end 未过期的 */
    private int couponCount(Long memberId, int status, LocalDateTime validBefore)
    {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getMemberId, memberId)
                .eq(Coupon::getStatus, status);
        if (validBefore != null)
        {
            wrapper.and(w -> w.isNull(Coupon::getValidEnd)
                    .or().ge(Coupon::getValidEnd, validBefore));
        }
        Long count = couponMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    /** 标签数组（关系表 → 标签定义，按标签ID升序） */
    private List<MemberPanoramaView.TagView> buildTags(Long memberId)
    {
        List<MemberTagRel> rels = memberTagRelMapper.selectList(
                new LambdaQueryWrapper<MemberTagRel>()
                        .eq(MemberTagRel::getMemberId, memberId)
                        .orderByAsc(MemberTagRel::getTagId));
        List<MemberPanoramaView.TagView> tags = new ArrayList<>();
        for (MemberTagRel rel : rels)
        {
            MemberTag tag = memberTagMapper.selectById(rel.getTagId());
            if (tag == null || "1".equals(tag.getStatus()))
            {
                continue;
            }
            MemberPanoramaView.TagView view = new MemberPanoramaView.TagView();
            view.setTagId(tag.getTagId());
            view.setTagName(tag.getTagName());
            view.setTagType(tag.getTagType());
            view.setColor(tag.getColor());
            tags.add(view);
        }
        return tags;
    }

    /** 消费统计：取自会员主档汇总列（结算时累加），客单价现场算 */
    private MemberPanoramaView.StatsView buildStats(Member member)
    {
        int count = member.getConsumeCount() == null ? 0 : member.getConsumeCount();
        BigDecimal amount = member.getConsumeAmount() == null
                ? BigDecimal.ZERO : member.getConsumeAmount();
        MemberPanoramaView.StatsView view = new MemberPanoramaView.StatsView();
        view.setConsumeCount(count);
        view.setConsumeAmount(amount);
        view.setAvgOrderAmount(count == 0 ? BigDecimal.ZERO.setScale(2)
                : amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        view.setLastConsumeTime(member.getLastConsumeTime() == null ? null
                : member.getLastConsumeTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return view;
    }

    /** 借阅统计：在借（1/3）/ 逾期（3）/ 历史总数 */
    private MemberPanoramaView.BorrowStatView buildBorrowStat(Long memberId)
    {
        Map<String, Object> row = analysisMapper.selectBorrowStat(memberId);
        MemberPanoramaView.BorrowStatView view = new MemberPanoramaView.BorrowStatView();
        view.setBorrowing(intOf(row, "borrowing"));
        view.setOverdue(intOf(row, "overdue"));
        view.setTotal(intOf(row, "total"));
        return view;
    }

    /** 全景页画像摘要：频次 / 客单价档 / 主活跃时段 / RFM 合计 */
    private MemberPanoramaView.PortraitSummaryView buildPortraitSummary(Long memberId,
            LocalDateTime from, LocalDateTime now)
    {
        MemberPortraitView portrait = portrait(memberId);
        MemberPanoramaView.PortraitSummaryView view = new MemberPanoramaView.PortraitSummaryView();
        view.setFrequency12m(portrait.getSpending() == null ? 0
                : portrait.getSpending().getOrderCount());
        view.setAvgOrderBucket(portrait.getSpending() == null ? null
                : bucketLabel(portrait.getSpending().getAvgOrderAmount()));
        String active = null;
        int max = 0;
        for (MemberPortraitView.Bucket bucket : portrait.getActiveDays())
        {
            if (bucket.getCount() != null && bucket.getCount() > max)
            {
                max = bucket.getCount();
                active = bucket.getLabel();
            }
        }
        view.setActivePeriod(active);
        view.setRfmScore(portrait.getRfm() == null ? null : portrait.getRfm().getTotalScore());
        return view;
    }

    // ==================================================================
    // 时间轴
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public TimelinePage timeline(Long memberId, String types, int pageNum, int pageSize)
    {
        referenceGuard.requireExists(memberMapper, memberId, "会员");
        int page = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);
        String typesCsv = normalizeTypes(types);
        long total = analysisMapper.countTimeline(memberId, typesCsv);
        List<MemberTimelineEntry> rows = analysisMapper.selectTimeline(memberId, typesCsv,
                size, (page - 1) * size);
        return new TimelinePage(rows, total);
    }

    /** 类型白名单校验（未知类型 400，避免拼进 SQL） */
    private String normalizeTypes(String types)
    {
        if (types == null || types.isBlank())
        {
            return null;
        }
        StringBuilder csv = new StringBuilder();
        for (String part : types.split(","))
        {
            String token = part.trim().toLowerCase();
            if (token.isEmpty())
            {
                continue;
            }
            if (!TIMELINE_TYPES.contains(token))
            {
                throw BizErrors.badRequest("时间轴类型只能是 order/borrow/point/stored，收到：" + token);
            }
            if (csv.indexOf(token) < 0)
            {
                if (csv.length() > 0)
                {
                    csv.append(',');
                }
                csv.append(token);
            }
        }
        return csv.length() == 0 ? null : csv.toString();
    }

    // ==================================================================
    // 画像
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public MemberPortraitView portrait(Long memberId)
    {
        Member member = referenceGuard.requireExists(memberMapper, memberId, "会员");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusMonths(WINDOW_MONTHS);
        boolean costVisible = hasPermission(COST_PERMISSION);

        MemberPortraitView view = new MemberPortraitView();
        view.setWindowDays(WINDOW_MONTHS * 30);
        view.setCostVisible(costVisible);
        view.setSpending(buildSpending(memberId, from, now, costVisible));
        view.setCategoryPreference(buildCategoryPreference(memberId, from, now,
                view.getSpending().getTotalAmount()));
        view.setAvgOrderDistribution(buildAmountDistribution(memberId, from, now));
        view.setActiveDays(buildActivePeriods(memberId, from, now));
        view.setRfm(buildRfm(memberId, from, now));
        return view;
    }

    /** 消费频次与金额（成本口径受权限控制） */
    private MemberPortraitView.Spending buildSpending(Long memberId, LocalDateTime from,
            LocalDateTime to, boolean costVisible)
    {
        Map<String, Object> row = analysisMapper.selectConsumeStat(memberId, from, to);
        int orderCount = intOf(row, "orderCount");
        BigDecimal total = decimalOf(row, "totalAmount");
        MemberPortraitView.Spending spending = new MemberPortraitView.Spending();
        spending.setOrderCount(orderCount);
        spending.setTotalAmount(total);
        spending.setAvgOrderAmount(orderCount == 0 ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP));
        spending.setMonthlyFrequency(BigDecimal.valueOf(orderCount)
                .divide(BigDecimal.valueOf(WINDOW_MONTHS), 2, RoundingMode.HALF_UP));
        spending.setActiveDayCount(intOf(row, "activeDays"));
        Object last = row == null ? null : row.get("lastOrderTime");
        spending.setLastOrderTime(last == null ? null : last.toString());
        spending.setGrossMarginAmount(costVisible ? decimalOf(row, "grossMargin") : null);
        return spending;
    }

    /** 品类偏好（金额倒序 + 占比） */
    private List<MemberPortraitView.CategoryShare> buildCategoryPreference(Long memberId,
            LocalDateTime from, LocalDateTime to, BigDecimal total)
    {
        List<Map<String, Object>> rows =
                analysisMapper.selectCategoryPreference(memberId, from, to);
        List<MemberPortraitView.CategoryShare> list = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            MemberPortraitView.CategoryShare share = new MemberPortraitView.CategoryShare();
            share.setCategoryId(longOf(row, "categoryId"));
            share.setCategoryName(stringOf(row, "categoryName"));
            BigDecimal amount = decimalOf(row, "amount");
            share.setAmount(amount);
            share.setRatio(ratio(amount, total));
            list.add(share);
        }
        return list;
    }

    /** 客单价五档固定桶 */
    private List<MemberPortraitView.Bucket> buildAmountDistribution(Long memberId,
            LocalDateTime from, LocalDateTime to)
    {
        int[] counts = new int[AMOUNT_BUCKETS.length];
        int total = 0;
        for (BigDecimal amount : analysisMapper.selectOrderAmounts(memberId, from, to))
        {
            counts[bucketIndex(amount)]++;
            total++;
        }
        List<MemberPortraitView.Bucket> list = new ArrayList<>();
        for (int i = 0; i < AMOUNT_BUCKETS.length; i++)
        {
            list.add(bucket(AMOUNT_BUCKETS[i], counts[i], total));
        }
        return list;
    }

    /** 活跃时段四段固定输出 */
    private List<MemberPortraitView.Bucket> buildActivePeriods(Long memberId, LocalDateTime from,
            LocalDateTime to)
    {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String period : PERIODS)
        {
            map.put(period, 0);
        }
        int total = 0;
        for (Map<String, Object> row : analysisMapper.selectActivePeriods(memberId, from, to))
        {
            String period = stringOf(row, "period");
            int count = intOf(row, "cnt");
            if (map.containsKey(period))
            {
                map.put(period, count);
                total += count;
            }
        }
        List<MemberPortraitView.Bucket> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : map.entrySet())
        {
            list.add(bucket(entry.getKey(), entry.getValue(), total));
        }
        return list;
    }

    /** RFM：同店样本百分位 → 1~5 分；样本 <3 人时返回 null */
    private MemberPortraitView.Rfm buildRfm(Long memberId, LocalDateTime from, LocalDateTime to)
    {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> samples = analysisMapper.selectRfmSamples(from, to, now);
        if (samples.size() < 3)
        {
            return null;
        }
        List<Integer> recencies = new ArrayList<>();
        List<Integer> frequencies = new ArrayList<>();
        List<BigDecimal> monetaries = new ArrayList<>();
        Integer myRecency = null;
        Integer myFrequency = null;
        BigDecimal myMonetary = null;
        for (Map<String, Object> row : samples)
        {
            int recency = intOf(row, "recencyDays");
            int frequency = intOf(row, "orderCount");
            BigDecimal monetary = decimalOf(row, "monetary");
            recencies.add(recency);
            frequencies.add(frequency);
            monetaries.add(monetary);
            Object id = row.get("memberId");
            if (id != null && ((Number) id).longValue() == memberId.longValue())
            {
                myRecency = recency;
                myFrequency = frequency;
                myMonetary = monetary;
            }
        }
        if (myFrequency == null)
        {
            return null;
        }
        MemberPortraitView.Rfm rfm = new MemberPortraitView.Rfm();
        rfm.setRecencyDays(myRecency);
        rfm.setFrequency(myFrequency);
        rfm.setMonetary(myMonetary);
        // R：天数越小越好 → 用「比它差（天数更多或相等）的样本占比」定分
        rfm.setRecencyScore(percentileScore(recencies, myRecency, false));
        rfm.setFrequencyScore(percentileScore(frequencies, myFrequency, true));
        rfm.setMonetaryScore(percentileScore(monetaries, myMonetary, true));
        rfm.setTotalScore(rfm.getRecencyScore() + rfm.getFrequencyScore()
                + rfm.getMonetaryScore());
        return rfm;
    }

    /** 百分位评分：higherIsBetter=true 时值越大分越高，否则越小分越高 */
    private int percentileScore(List<? extends Number> values, Number mine, boolean higherIsBetter)
    {
        int better = 0;
        int equal = 0;
        for (Number value : values)
        {
            int cmp = compare(value, mine);
            if (higherIsBetter ? cmp > 0 : cmp < 0)
            {
                better++;
            }
            else if (cmp == 0)
            {
                equal++;
            }
        }
        // 分数 = 1 + 4 ×（比我差的占比）；并列时给同一分
        double ratio = (double) better / values.size();
        return Math.max(1, Math.min(5, 1 + (int) Math.round(ratio * 4)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private int compare(Number a, Number b)
    {
        if (a instanceof BigDecimal || b instanceof BigDecimal)
        {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        return Integer.compare(a.intValue(), b.intValue());
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 当前登录人是否持有某权限串。
     *
     * <p>⚠️ 必须走 {@link LoginUser#getPermissions()}：RuoYi 的
     * {@code LoginUser#getAuthorities()} 恒返回 {@code null}，
     * 直接读 {@code Authentication#getAuthorities()} 会 NPE（实测踩到）。
     * 与平台 {@code PermissionService#hasPermi} 同口径：含 {@code *:*:*} 即全权。
     */
    private boolean hasPermission(String permission)
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getPermissions() == null)
            {
                return false;
            }
            Set<String> permissions = loginUser.getPermissions();
            return permissions.contains(ALL_PERMISSION) || permissions.contains(permission);
        }
        catch (Exception ignored)
        {
            // 无安全上下文（如定时任务）→ 按无成本权限处理
            return false;
        }
    }

    /** 积分到期提醒窗口（配置缺失时 30 天） */
    private int notifyDays()
    {
        String raw = configService.selectConfigByKey("biz.points.expire.notify.days");
        try
        {
            return (raw == null || raw.isBlank()) ? 30 : Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return 30;
        }
    }

    /** 客单价档位标签（与分布桶同口径） */
    private String bucketLabel(BigDecimal amount)
    {
        return AMOUNT_BUCKETS[bucketIndex(amount)];
    }

    private int bucketIndex(BigDecimal amount)
    {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.compareTo(new BigDecimal("50")) < 0)
        {
            return 0;
        }
        if (value.compareTo(new BigDecimal("100")) < 0)
        {
            return 1;
        }
        if (value.compareTo(new BigDecimal("200")) < 0)
        {
            return 2;
        }
        if (value.compareTo(new BigDecimal("500")) < 0)
        {
            return 3;
        }
        return 4;
    }

    private MemberPortraitView.Bucket bucket(String label, int count, int total)
    {
        MemberPortraitView.Bucket bucket = new MemberPortraitView.Bucket();
        bucket.setLabel(label);
        bucket.setCount(count);
        bucket.setRatio(total == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        return bucket;
    }

    private BigDecimal ratio(BigDecimal part, BigDecimal total)
    {
        if (total == null || total.signum() == 0 || part == null)
        {
            return BigDecimal.ZERO.setScale(4);
        }
        return part.divide(total, 4, RoundingMode.HALF_UP);
    }

    private int intOf(Map<String, Object> row, String key)
    {
        Object value = (row == null) ? null : row.get(key);
        return value == null ? 0 : ((Number) value).intValue();
    }

    private Long longOf(Map<String, Object> row, String key)
    {
        Object value = (row == null) ? null : row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private BigDecimal decimalOf(Map<String, Object> row, String key)
    {
        Object value = (row == null) ? null : row.get(key);
        return value == null ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private String stringOf(Map<String, Object> row, String key)
    {
        Object value = (row == null) ? null : row.get(key);
        return value == null ? null : value.toString();
    }
}
