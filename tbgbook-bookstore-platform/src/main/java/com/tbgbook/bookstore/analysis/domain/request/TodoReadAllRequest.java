package com.tbgbook.bookstore.analysis.domain.request;

import lombok.Data;

/**
 * 全部标记已读请求。
 */
@Data
public class TodoReadAllRequest
{
    private Integer todoType;
}
