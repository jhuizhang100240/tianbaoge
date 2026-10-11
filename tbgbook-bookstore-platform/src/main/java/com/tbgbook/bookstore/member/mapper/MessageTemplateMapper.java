package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MessageTemplate;

/**
 * 消息模板 Mapper 接口（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface MessageTemplateMapper extends BaseMapper<MessageTemplate>
{
    /**
     * 查询消息模板列表。
     *
     * @param template 查询条件
     * @return 模板集合
     */
    List<MessageTemplate> selectMessageTemplateList(MessageTemplate template);
}
