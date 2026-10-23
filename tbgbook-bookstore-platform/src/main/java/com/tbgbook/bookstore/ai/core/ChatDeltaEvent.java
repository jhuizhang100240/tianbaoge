package com.tbgbook.bookstore.ai.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 流式事件（阶段九 SSE 传输层用）：type = delta / done / error。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class ChatDeltaEvent
{
    private String type;

    private String content;
}
