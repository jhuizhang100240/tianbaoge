package com.tbgbook.bookstore.analysis.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.analysis.domain.BizTodo;

/**
 * 待办中心 Service。
 */
public interface ITodoService extends IService<BizTodo>
{
    List<BizTodo> selectTodoList(BizTodo query);

    BizTodo getTodo(Long todoId);

    /**
     * 状态流转：2已读 / 3已处理 / 4已忽略；重复设置同状态幂等。
     */
    void updateStatus(Long todoId, Integer status, String remark);

    /**
     * 当前登录人的未读待办全部置已读。
     */
    int readAll(Integer todoType);
}
