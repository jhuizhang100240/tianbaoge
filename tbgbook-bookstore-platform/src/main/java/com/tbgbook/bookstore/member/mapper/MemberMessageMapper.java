package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberMessage;

/**
 * 会员消息 Mapper 接口（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface MemberMessageMapper extends BaseMapper<MemberMessage>
{
    /**
     * 查询会员消息列表。
     *
     * @param message 查询条件
     * @return 消息集合
     */
    List<MemberMessage> selectMemberMessageList(MemberMessage message);
}
