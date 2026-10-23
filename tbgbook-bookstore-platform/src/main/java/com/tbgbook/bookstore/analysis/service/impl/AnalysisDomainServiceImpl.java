package com.tbgbook.bookstore.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.analysis.mapper.AnalysisQueryMapper;
import com.tbgbook.bookstore.analysis.service.IAnalysisDomainService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 会员 / 库存 / 教材季分析 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AnalysisDomainServiceImpl implements IAnalysisDomainService
{
    private static final String COST_PERM = "dashboard:cost:view";

    private static final int MAX_RANGE_DAYS = 366;

    private final AnalysisQueryMapper analysisQueryMapper;

    private final ISysConfigService configService;

    @Override
    public Map<String, Object> memberSummary(LocalDate startDate, LocalDate endDate)
    {
        requireRange(startDate, endDate);
        Map<String, Object> active = analysisQueryMapper.memberActiveStats(startDate, endDate);
        Map<String, Object> repurchase = analysisQueryMapper.memberRepurchase(startDate, endDate);
        Map<String, Object> consume = analysisQueryMapper.memberConsumeRatio(startDate, endDate);

        BigDecimal buyers = decimal(repurchase == null ? null : repurchase.get("memberBuyers"));
        BigDecimal repeat = decimal(repurchase == null ? null : repurchase.get("repeatBuyers"));
        BigDecimal totalSale = decimal(consume == null ? null : consume.get("totalSale"));
        BigDecimal memberSale = decimal(consume == null ? null : consume.get("memberSale"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newMembers", analysisQueryMapper.memberNewTrend(startDate, endDate));
        data.put("activeMembers", integer(active == null ? null : active.get("activeMembers")));
        data.put("levelDistribution", analysisQueryMapper.memberLevelDistribution());
        data.put("repurchaseRate", buyers.signum() == 0 ? BigDecimal.ZERO
                : repeat.multiply(BigDecimal.valueOf(100)).divide(buyers, 2, RoundingMode.HALF_UP));
        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("weekly", analysisQueryMapper.memberRetentionWeekly(startDate, endDate));
        retention.put("monthly", analysisQueryMapper.memberRetentionMonthly(startDate, endDate));
        data.put("retention", retention);
        data.put("memberConsumeRatio", totalSale.signum() == 0 ? BigDecimal.ZERO
                : memberSale.multiply(BigDecimal.valueOf(100)).divide(totalSale, 2, RoundingMode.HALF_UP));
        data.put("memberSaleAmount", memberSale);
        data.put("totalSaleAmount", totalSale);
        return data;
    }

    @Override
    public Map<String, Object> inventorySummary(LocalDate startDate, LocalDate endDate, Long categoryId)
    {
        requireRange(startDate, endDate);
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Map<String, Object> structure = analysisQueryMapper.inventoryStructure(categoryId);
        Map<String, Object> sales = analysisQueryMapper.inventorySales(startDate, endDate, categoryId);
        Map<String, Object> stockKinds = analysisQueryMapper.inventoryStockKinds(categoryId);
        BigDecimal stockTotal = decimal(structure == null ? null : structure.get("available"))
                .add(decimal(structure == null ? null : structure.get("locked")))
                .add(decimal(structure == null ? null : structure.get("damaged")));
        BigDecimal soldQty = decimal(sales == null ? null : sales.get("soldQty"));
        BigDecimal dailySales = soldQty.divide(BigDecimal.valueOf(Math.max(days, 1)), 4, RoundingMode.HALF_UP);
        BigDecimal turnoverDays = dailySales.signum() == 0 ? null
                : stockTotal.divide(dailySales, 2, RoundingMode.HALF_UP);
        BigDecimal stockKindCount = decimal(stockKinds == null ? null : stockKinds.get("stockKinds"));
        BigDecimal soldKindCount = decimal(sales == null ? null : sales.get("soldKinds"));
        BigDecimal sellThrough = stockKindCount.signum() == 0 ? BigDecimal.ZERO
                : soldKindCount.multiply(BigDecimal.valueOf(100))
                        .divide(stockKindCount, 2, RoundingMode.HALF_UP);
        int slowDays = intConfig("biz.stock.slowmoving.days", 60);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("structure", structure);
        data.put("turnoverDays", turnoverDays);
        data.put("sellThroughRate", sellThrough);
        data.put("outOfStock", analysisQueryMapper.outOfStock(categoryId));
        data.put("slowMoving", analysisQueryMapper.bookRankSlow(
                LocalDateTime.now().minusDays(slowDays), 50));
        data.put("totalAmount", SecurityUtils.hasPermi(COST_PERM)
                ? decimal(analysisQueryMapper.inventoryTotalAmount(categoryId).get("totalAmount")) : null);
        return data;
    }

    @Override
    public Map<String, Object> textbookSeason(Long listId, String term)
    {
        if (listId == null && !StringUtils.hasText(term))
        {
            throw BizErrors.badRequest("listId 与 term 至少提供一个");
        }
        Map<String, Object> header = analysisQueryMapper.textbookSeasonHeader(
                listId, StringUtils.hasText(term) ? term : null);
        if (header == null || integer(header.get("listCount")) == 0)
        {
            if (listId != null)
            {
                throw BizErrors.notFound("教材清单", listId);
            }
            throw BizErrors.notFound("教材清单（学期 " + term + "）", term);
        }
        BigDecimal reserve = decimal(header.get("reserveQty"));
        BigDecimal arrived = decimal(header.get("arrivedQty"));
        BigDecimal picked = decimal(header.get("pickedQty"));
        BigDecimal arriveRate = reserve.signum() == 0 ? BigDecimal.ZERO
                : arrived.multiply(BigDecimal.valueOf(100)).divide(reserve, 2, RoundingMode.HALF_UP);
        BigDecimal pickupRate = reserve.signum() == 0 ? BigDecimal.ZERO
                : picked.multiply(BigDecimal.valueOf(100)).divide(reserve, 2, RoundingMode.HALF_UP);
        BigDecimal remain = arrived.subtract(picked);
        if (remain.signum() < 0)
        {
            remain = BigDecimal.ZERO;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reserveQty", integer(header.get("reserveQty")));
        data.put("arrivedQty", integer(header.get("arrivedQty")));
        data.put("pickedQty", integer(header.get("pickedQty")));
        data.put("arriveRate", arriveRate);
        data.put("pickupRate", pickupRate);
        data.put("remainQty", remain);
        data.put("items", analysisQueryMapper.textbookSeasonItems(listId,
                StringUtils.hasText(term) ? term : null));
        return data;
    }

    private void requireRange(LocalDate start, LocalDate end)
    {
        if (start == null || end == null)
        {
            throw BizErrors.badRequest("开始日期与结束日期不能为空");
        }
        if (end.isBefore(start))
        {
            throw BizErrors.badRequest("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS)
        {
            throw BizErrors.ruleViolated("查询区间不能超过 366 天");
        }
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer integer(Object value)
    {
        return value == null ? 0 : new BigDecimal(String.valueOf(value)).intValue();
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
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
}
