package com.tbgbook.bookstore.app.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.app.mapper.AppTradeMapper;

import lombok.RequiredArgsConstructor;

/**
 * 会员端待支付订单超时取消（每 60 秒）。
 */
@Component
@RequiredArgsConstructor
public class AppOrderTimeoutTask
{
    private final AppTradeMapper tradeMapper;

    private final IAppOrderService orderService;

    @Scheduled(fixedDelay = 60000L, initialDelay = 30000L)
    public void cancelExpired()
    {
        List<Map<String, Object>> rows = tradeMapper.expiredOrders(LocalDateTime.now());
        for (Map<String, Object> row : rows)
        {
            Object id = row.get("orderId");
            if (id != null)
            {
                orderService.cancelExpired(Long.valueOf(String.valueOf(id)));
            }
        }
    }
}
