package com.tbgbook.bookstore.analysis.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.core.AiGuard;
import com.tbgbook.bookstore.ai.core.AiUnavailableException;
import com.tbgbook.bookstore.ai.core.CallLogEntry;
import com.tbgbook.bookstore.ai.core.CallLogRecorder;
import com.tbgbook.bookstore.ai.core.QuotaGuard;
import com.tbgbook.bookstore.ai.core.SensitiveMasker;
import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.domain.AiSession;
import com.tbgbook.bookstore.ai.mapper.AiMessageMapper;
import com.tbgbook.bookstore.ai.service.IAiSessionService;
import com.tbgbook.bookstore.analysis.domain.request.AnalysisAskRequest;
import com.tbgbook.bookstore.analysis.service.IAnalysisAskService;
import com.tbgbook.bookstore.analysis.service.IAnalysisDomainService;
import com.tbgbook.bookstore.analysis.service.IAnalysisSaleService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * AI 经营数据问答 Service 实现。
 *
 * <p>规则解析优先（覆盖高频问法、不产生模型费用）；无法识别时才调用 ChatModel 做白名单意图分类，
 * 数值始终来自后端聚合查询，回答文本由已算指标模板生成，避免模型编造数据。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AnalysisAskServiceImpl implements IAnalysisAskService
{
    private static final String COST_PERM = "dashboard:cost:view";

    private static final int SCENE_ASK = 4;

    private static final Pattern TERM = Pattern.compile("(20\\d{2})\\s*(春|秋)季");

    private static final List<String> METRIC_CODES = List.of(
            "sale_amount", "order_count", "avg_order", "gross_profit",
            "member", "inventory", "textbook");

    private final IAnalysisSaleService saleService;

    private final IAnalysisDomainService domainService;

    private final IAiSessionService aiSessionService;

    private final AiMessageMapper aiMessageMapper;

    private final SensitiveMasker sensitiveMasker;

    private final AiGuard aiGuard;

    private final QuotaGuard quotaGuard;

    private final ChatModel chatModel;

    private final CallLogRecorder callLogRecorder;

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> ask(AnalysisAskRequest request)
    {
        long started = System.currentTimeMillis();
        aiGuard.ensureUsable(AiGuard.AiChannel.CHAT);
        quotaGuard.checkGlobalDaily();

        String question = request.getQuestion().trim();
        LocalDate[] range = parseRange(question);
        String metric = detectMetric(question);
        String modelUsed = "rule";
        int promptTokens = 0;
        int completionTokens = 0;
        if (metric == null)
        {
            if (containsOutOfScope(question))
            {
                throw BizErrors.ruleViolated("问题超出可回答范围");
            }
            ModelIntent intent = classifyWithModel(question);
            metric = intent.metric();
            modelUsed = intent.model();
            promptTokens = intent.promptTokens();
            completionTokens = intent.completionTokens();
        }
        if (metric == null)
        {
            throw BizErrors.ruleViolated("问题超出可回答范围");
        }
        if ("gross_profit".equals(metric) && !SecurityUtils.hasPermi(COST_PERM))
        {
            throw BizErrors.forbidden("问题涉及成本数据但无查看权限");
        }

        Answer answer = buildAnswer(metric, range, Boolean.TRUE.equals(request.getAsChart()));
        // 首字耗时以「回答文本就绪」为准；会话落库不应计入首字指标
        int latency = (int) (System.currentTimeMillis() - started);
        Long sessionId = ensureSession(request.getSessionId());
        saveMessages(sessionId, question, answer, modelUsed, promptTokens, completionTokens, latency);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer.text());
        data.put("metricCodes", answer.metricCodes());
        data.put("chart", answer.chart());
        data.put("citations", answer.citations());
        data.put("sessionId", sessionId);
        data.put("firstTokenMs", latency);
        return data;
    }

    private LocalDate[] parseRange(String question)
    {
        LocalDate today = LocalDate.now();
        if (question.contains("昨天") || question.contains("昨日"))
        {
            LocalDate yesterday = today.minusDays(1);
            return new LocalDate[] { yesterday, yesterday };
        }
        if (question.contains("今天") || question.contains("今日"))
        {
            return new LocalDate[] { today, today };
        }
        if (question.contains("上月") || question.contains("上个月"))
        {
            LocalDate lastMonth = today.minusMonths(1);
            return new LocalDate[] { lastMonth.withDayOfMonth(1),
                    lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()) };
        }
        if (question.contains("本月") || question.contains("这个月"))
        {
            return new LocalDate[] { today.withDayOfMonth(1), today };
        }
        if (question.contains("今年") || question.contains("本年"))
        {
            return new LocalDate[] { today.withDayOfYear(1), today };
        }
        return new LocalDate[] { today.minusDays(29), today };
    }

    private String detectMetric(String question)
    {
        if (question.contains("毛利") || question.contains("利润"))
        {
            return "gross_profit";
        }
        if (question.contains("客单价"))
        {
            return "avg_order";
        }
        if (question.contains("订单") || question.contains("单量") || question.contains("多少单"))
        {
            return "order_count";
        }
        if (question.contains("销售额") || question.contains("营业额") || question.contains("营收")
                || question.contains("收入"))
        {
            return "sale_amount";
        }
        if (question.contains("库存") || question.contains("缺货") || question.contains("积压")
                || question.contains("周转"))
        {
            return "inventory";
        }
        if (question.contains("教材") || question.contains("预订") || question.contains("到货")
                || question.contains("取书"))
        {
            return "textbook";
        }
        if (question.contains("会员") || question.contains("复购") || question.contains("活跃"))
        {
            return "member";
        }
        return null;
    }

    private boolean containsOutOfScope(String question)
    {
        return question.contains("天气") || question.contains("股票") || question.contains("新闻")
                || question.contains("笑话") || question.contains("写诗");
    }

    private ModelIntent classifyWithModel(String question)
    {
        long started = System.currentTimeMillis();
        String system = "你是书店经营数据问答的意图分类器。只能从以下代码中选择一个并直接输出代码："
                + "sale_amount, order_count, avg_order, gross_profit, member, inventory, textbook。"
                + "无法判断时只输出 NONE。不要输出任何其他文字。";
        String model = "";
        int promptTokens = 0;
        int completionTokens = 0;
        try
        {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(system), new UserMessage(question))));
            if (response.getMetadata() != null)
            {
                model = response.getMetadata().getModel() == null ? "" : response.getMetadata().getModel();
                Usage usage = response.getMetadata().getUsage();
                if (usage != null)
                {
                    promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
                }
            }
            String text = response.getResult() == null || response.getResult().getOutput() == null
                    ? "" : response.getResult().getOutput().getText();
            callLogRecorder.record(CallLogEntry.builder().scene(SCENE_ASK).modelName(model)
                    .promptTokens(promptTokens).completionTokens(completionTokens)
                    .latencyMs((int) (System.currentTimeMillis() - started)).success(true).build());
            String code = text == null ? "" : text.trim().split("\\s+")[0].replaceAll("[^a-z_]", "");
            return new ModelIntent(METRIC_CODES.contains(code) ? code : null, model,
                    promptTokens, completionTokens);
        }
        catch (RuntimeException e)
        {
            callLogRecorder.record(CallLogEntry.builder().scene(SCENE_ASK).modelName(model)
                    .promptTokens(promptTokens).completionTokens(completionTokens)
                    .latencyMs((int) (System.currentTimeMillis() - started)).success(false)
                    .errorMsg(e.getMessage()).build());
            throw new AiUnavailableException("AI 意图识别失败：" + e.getMessage(), e);
        }
    }

    private Answer buildAnswer(String metric, LocalDate[] range, boolean asChart)
    {
        String period = range[0] + " 至 " + range[1];
        List<String> citations = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        codes.add(metric);
        Map<String, Object> chart = new LinkedHashMap<>();
        String text;
        switch (metric)
        {
            case "sale_amount", "order_count", "avg_order", "gross_profit" -> {
                Map<String, Object> data = saleService.saleSummary(range[0], range[1], null);
                Map<String, Object> summary = asMap(data.get("summary"));
                citations.add("销售额 = 已支付/待取货/已完成订单实付合计（红冲负数抵扣）");
                citations.add("客单价 = 销售额 ÷ 订单数");
                if ("order_count".equals(metric))
                {
                    text = period + "，订单数为 " + summary.getOrDefault("orderCount", 0) + " 笔。";
                }
                else if ("avg_order".equals(metric))
                {
                    text = period + "，客单价为 ¥" + summary.getOrDefault("avgOrder", 0) + "。";
                }
                else if ("gross_profit".equals(metric))
                {
                    text = period + "，毛利为 ¥" + summary.getOrDefault("grossProfit", 0)
                            + "，毛利率 " + summary.getOrDefault("margin", 0) + "%。";
                }
                else
                {
                    text = period + "，销售额为 ¥" + summary.getOrDefault("saleAmount", 0) + "。";
                }
                if (asChart)
                {
                    chart.put("dates", List.of(range[1].toString()));
                    chart.put("saleAmounts", List.of(summary.getOrDefault("saleAmount", 0)));
                    chart.put("orderCounts", List.of(summary.getOrDefault("orderCount", 0)));
                }
            }
            case "member" -> {
                Map<String, Object> data = domainService.memberSummary(range[0], range[1]);
                long newMembers = 0;
                Object trend = data.get("newMembers");
                if (trend instanceof List<?> list)
                {
                    for (Object item : list)
                    {
                        newMembers += ((Number) asMap(item).getOrDefault("count", 0)).longValue();
                    }
                }
                text = period + "，新增会员 " + newMembers + " 人，活跃会员 "
                        + data.getOrDefault("activeMembers", 0) + " 人，复购率 "
                        + data.getOrDefault("repurchaseRate", 0) + "%。";
                citations.add("活跃会员 = 统计期内有成交订单的会员数");
                citations.add("复购率 = 成交 ≥2 次的会员 ÷ 成交会员数 × 100%");
                if (asChart)
                {
                    chart.put("newMembers", data.get("newMembers"));
                }
            }
            case "inventory" -> {
                Map<String, Object> data = domainService.inventorySummary(range[0], range[1], null);
                Map<String, Object> structure = asMap(data.get("structure"));
                int outOfStock = data.get("outOfStock") instanceof List<?> list ? list.size() : 0;
                text = period + "，库存情况：可售 " + structure.getOrDefault("available", 0) + " 册、锁定 "
                        + structure.getOrDefault("locked", 0) + " 册、借出 "
                        + structure.getOrDefault("borrowed", 0) + " 册、残损 "
                        + structure.getOrDefault("damaged", 0) + " 册；缺货品种 " + outOfStock
                        + " 个，动销率 " + data.getOrDefault("sellThroughRate", 0) + "%。";
                citations.add("周转天数 = 平均库存 ÷ 日均销量");
                citations.add("动销率 = 统计期产生销售的品种数 ÷ 在库品种数 × 100%");
                if (asChart)
                {
                    chart.put("structure", structure);
                }
            }
            case "textbook" -> {
                String term = parseTerm(range[0]);
                Map<String, Object> data;
                try
                {
                    data = domainService.textbookSeason(null, term);
                }
                catch (ServiceException e)
                {
                    data = Map.of();
                }
                if (data.isEmpty())
                {
                    text = period + "，暂无 " + term + " 教材清单数据。";
                }
                else
                {
                    text = period + "（" + term + "），教材预订 " + data.getOrDefault("reserveQty", 0)
                            + " 册，到货 " + data.getOrDefault("arrivedQty", 0) + " 册（到货率 "
                            + data.getOrDefault("arriveRate", 0) + "%），已取 "
                            + data.getOrDefault("pickedQty", 0) + " 册（取书率 "
                            + data.getOrDefault("pickupRate", 0) + "%）。";
                }
                citations.add("到货率 = 到货量 ÷ 预订量 × 100%");
                citations.add("取书率 = 已取量 ÷ 预订量 × 100%");
                if (asChart)
                {
                    chart.put("items", data.get("items"));
                }
            }
            default -> throw BizErrors.ruleViolated("问题超出可回答范围");
        }
        return new Answer(text, codes, chart, citations);
    }

    private String parseTerm(LocalDate date)
    {
        int month = date.getMonthValue();
        if (month >= 2 && month <= 7)
        {
            return date.getYear() + " 春季";
        }
        return (month == 1 ? date.getYear() - 1 : date.getYear()) + " 秋季";
    }

    private Long ensureSession(Long sessionId)
    {
        if (sessionId != null)
        {
            AiSession existing = aiSessionService.getById(sessionId);
            if (existing == null)
            {
                throw BizErrors.notFound("问答会话", sessionId);
            }
            return sessionId;
        }
        AiSession session = new AiSession();
        session.setMemberId(0L);
        session.setMemberName(SecurityUtils.getUsername());
        session.setScene(SCENE_ASK);
        session.setSource(2);
        session.setEntryContext("analysis");
        aiSessionService.addAiSession(session);
        return session.getSessionId();
    }

    private void saveMessages(Long sessionId, String question, Answer answer, String model,
            int promptTokens, int completionTokens, int latency)
    {
        LocalDateTime now = LocalDateTime.now();
        AiMessage questionMessage = new AiMessage();
        questionMessage.setSessionId(sessionId);
        questionMessage.setRole(1);
        questionMessage.setContent(sensitiveMasker.mask(question));
        questionMessage.setConfidence(BigDecimal.ONE);
        questionMessage.setIsLowConfidence(0);
        questionMessage.setIsFallback(0);
        questionMessage.setCitations("[]");
        questionMessage.setRealtimeData("{}");
        questionMessage.setModelName("");
        questionMessage.setPromptTokens(0);
        questionMessage.setCompletionTokens(0);
        questionMessage.setLatencyMs(latency);
        questionMessage.setFirstTokenMs(latency);
        questionMessage.setFeedback(0);
        questionMessage.setCreateTime(now);
        aiMessageMapper.insert(questionMessage);

        AiMessage answerMessage = new AiMessage();
        answerMessage.setSessionId(sessionId);
        answerMessage.setRole(2);
        answerMessage.setContent(sensitiveMasker.mask(answer.text()));
        answerMessage.setConfidence(BigDecimal.ONE);
        answerMessage.setIsLowConfidence(0);
        answerMessage.setIsFallback(0);
        answerMessage.setCitations(toJson(answer.citations()));
        answerMessage.setRealtimeData(toJson(answer.chart()));
        answerMessage.setModelName(model);
        answerMessage.setPromptTokens(promptTokens);
        answerMessage.setCompletionTokens(completionTokens);
        answerMessage.setLatencyMs(latency);
        answerMessage.setFirstTokenMs(latency);
        answerMessage.setFeedback(0);
        answerMessage.setCreateTime(now.plusNanos(1));
        aiMessageMapper.insert(answerMessage);

        AiSession session = aiSessionService.getById(sessionId);
        if (session != null)
        {
            AiSession update = new AiSession();
            update.setSessionId(sessionId);
            update.setTurnCount((session.getTurnCount() == null ? 0 : session.getTurnCount()) + 1);
            aiSessionService.updateById(update);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value)
    {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String toJson(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception e)
        {
            return "{}";
        }
    }

    private record Answer(String text, List<String> metricCodes, Map<String, Object> chart,
            List<String> citations)
    {
    }

    private record ModelIntent(String metric, String model, int promptTokens, int completionTokens)
    {
    }
}
