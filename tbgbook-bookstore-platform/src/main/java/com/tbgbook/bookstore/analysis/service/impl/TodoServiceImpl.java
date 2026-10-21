package com.tbgbook.bookstore.analysis.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.mapper.BizTodoMapper;
import com.tbgbook.bookstore.analysis.service.ITodoService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 待办中心 Service 实现。
 *
 * <p>待办由各业务模块产生，后台只负责查询与状态流转；不做手工新增。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class TodoServiceImpl extends ServiceImpl<BizTodoMapper, BizTodo> implements ITodoService
{
    private static final long STORE_ID = 1L;

    @Override
    public List<BizTodo> selectTodoList(BizTodo query)
    {
        LambdaQueryWrapper<BizTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTodo::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(query.getTodoType() != null, BizTodo::getTodoType, query.getTodoType());
            wrapper.eq(query.getPriority() != null, BizTodo::getPriority, query.getPriority());
            wrapper.eq(StringUtils.hasText(query.getSourceModule()), BizTodo::getSourceModule,
                    query.getSourceModule());
            wrapper.eq(query.getStatus() != null, BizTodo::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(BizTodo::getPriority)
                .orderByDesc(BizTodo::getCreateTime)
                .orderByDesc(BizTodo::getTodoId);
        return list(wrapper);
    }

    @Override
    public BizTodo getTodo(Long todoId)
    {
        if (todoId == null)
        {
            throw BizErrors.badRequest("待办ID不能为空");
        }
        BizTodo todo = getById(todoId);
        if (todo == null)
        {
            throw BizErrors.notFound("待办", todoId);
        }
        return todo;
    }

    @Override
    public void updateStatus(Long todoId, Integer status, String remark)
    {
        if (status == null || (status != 2 && status != 3 && status != 4))
        {
            throw BizErrors.ruleViolated("状态取值 2已读 / 3已处理 / 4已忽略");
        }
        BizTodo existing = getTodo(todoId);
        if (status.equals(existing.getStatus()))
        {
            return;
        }

        String operator = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();
        BizTodo update = new BizTodo();
        update.setTodoId(todoId);
        update.setStatus(status);
        update.setRemark(remark == null ? "" : remark);
        if (status == 2)
        {
            update.setReadBy(operator);
            update.setReadTime(now);
        }
        else
        {
            if (!StringUtils.hasText(existing.getReadBy()))
            {
                update.setReadBy(operator);
                update.setReadTime(now);
            }
            update.setHandleBy(operator);
            update.setHandleTime(now);
        }
        updateById(update);
    }

    @Override
    public int readAll(Integer todoType)
    {
        String operator = SecurityUtils.getUsername();
        BizTodo update = new BizTodo();
        update.setStatus(2);
        update.setReadBy(operator);
        update.setReadTime(LocalDateTime.now());

        LambdaQueryWrapper<BizTodo> wrapper = new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getStoreId, STORE_ID)
                .eq(BizTodo::getStatus, 1);
        if (todoType != null)
        {
            wrapper.eq(BizTodo::getTodoType, todoType);
        }
        return baseMapper.update(update, wrapper);
    }

}
