package com.tbgbook.bookstore.ai.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.domain.AiCallLog;
import com.tbgbook.bookstore.ai.mapper.AiCallLogMapper;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * AI 调用日志与费用记录（biz_ai_call_log，只增不删）。
 *
 * <p>费用按 biz.ai.model.price 的单价 JSON 计算（元/千 token，保留 4 位小数）；
 * 记录失败只 warn，不得影响业务结果。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class CallLogRecorder
{
    private static final Logger log = LoggerFactory.getLogger(CallLogRecorder.class);

    private static final long STORE_ID = 1L;

    private final AiCallLogMapper aiCallLogMapper;

    private final ISysConfigService configService;

    private final ObjectMapper objectMapper;

    public void record(CallLogEntry entry)
    {
        try
        {
            AiCallLog callLog = new AiCallLog();
            callLog.setStoreId(STORE_ID);
            callLog.setScene(entry.getScene());
            callLog.setSessionId(entry.getSessionId() == null ? 0L : entry.getSessionId());
            callLog.setMemberId(entry.getMemberId() == null ? 0L : entry.getMemberId());
            callLog.setModelName(entry.getModelName() == null ? "" : entry.getModelName());
            callLog.setPromptTokens(entry.getPromptTokens());
            callLog.setCompletionTokens(entry.getCompletionTokens());
            callLog.setTotalTokens(entry.getPromptTokens() + entry.getCompletionTokens());
            callLog.setCacheHit(0);
            callLog.setCost(calculateCost(entry));
            callLog.setLatencyMs(entry.getLatencyMs());
            callLog.setIsSuccess(entry.isSuccess() ? 1 : 0);
            callLog.setErrorMsg(truncate(entry.getErrorMsg()));
            aiCallLogMapper.insert(callLog);
        }
        catch (Exception e)
        {
            log.warn("record ai call log failed, scene={}", entry.getScene(), e);
        }
    }

    private BigDecimal calculateCost(CallLogEntry entry)
    {
        BigDecimal zero = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (!StringUtils.hasText(entry.getModelName()))
        {
            return zero;
        }
        try
        {
            String priceJson = configService.selectConfigByKey("biz.ai.model.price");
            if (!StringUtils.hasText(priceJson))
            {
                return zero;
            }
            JsonNode root = objectMapper.readTree(priceJson);
            JsonNode model = root.get(entry.getModelName());
            if (model == null)
            {
                return zero;
            }
            BigDecimal inputPer1k = BigDecimal.valueOf(model.path("inputPer1k").asDouble(0D));
            BigDecimal outputPer1k = BigDecimal.valueOf(model.path("outputPer1k").asDouble(0D));
            BigDecimal cost = inputPer1k.multiply(BigDecimal.valueOf(entry.getPromptTokens()))
                    .add(outputPer1k.multiply(BigDecimal.valueOf(entry.getCompletionTokens())))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            return cost.setScale(4, RoundingMode.HALF_UP);
        }
        catch (Exception e)
        {
            log.warn("calculate ai cost failed, model={}", entry.getModelName(), e);
            return zero;
        }
    }

    private String truncate(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
