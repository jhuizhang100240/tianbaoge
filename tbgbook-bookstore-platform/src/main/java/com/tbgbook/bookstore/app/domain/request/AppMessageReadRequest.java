package com.tbgbook.bookstore.app.domain.request;

import java.util.List;

import lombok.Data;

/**
 * 会员端消息已读请求。
 */
@Data
public class AppMessageReadRequest
{
    private List<Long> messageIds;
}
