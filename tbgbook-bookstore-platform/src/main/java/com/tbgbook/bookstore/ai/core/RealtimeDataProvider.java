package com.tbgbook.bookstore.ai.core;

import java.util.Map;

/**
 * 实时业务数据注入接口（阶段九由各业务域提供实现）。
 *
 * <p>按会话入口上下文（订单号/借阅单号/回收单号）返回库存、订单、余额等实时 JSON
 * （含 snapshotTime）。**AI 不得凭空提及业务数据：只有注入值可被引用。**
 *
 * @author tbgbook
 */
public interface RealtimeDataProvider
{
    Map<String, Object> load(String entryContext);
}
