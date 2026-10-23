package com.tbgbook.bookstore.member.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.SubscribeAuth;

/**
 * 订阅消息授权记录 Mapper 接口。
 *
 * <p>{@code consumeAuth} 是发送动作的**条件扣减**（剩余可用才扣；扣到上限置 3已用完）——
 * 并发下影响 0 行即视为不可触达。
 *
 * @author tbgbook
 */
public interface SubscribeAuthMapper extends BaseMapper<SubscribeAuth>
{
    /**
     * 查询订阅授权列表。
     *
     * @param auth 查询条件
     * @return 记录集合
     */
    List<SubscribeAuth> selectSubscribeAuthList(SubscribeAuth auth);

    /**
     * 当前可用（已授权且剩余次数 > 0 且未过期）的会员ID 去重集合。
     *
     * @param templateCode 模板编码；null = 不限定模板
     * @param now          当前时间（应用侧，A32）
     */
    List<Long> selectUsableMemberIds(@Param("templateCode") String templateCode,
            @Param("now") LocalDateTime now);

    /** 扣减一次订阅次数（条件更新；用尽置 auth_status=3） */
    int consumeAuth(@Param("authId") Long authId);

    /**
     * 找一个该会员该模板下可用的授权记录。
     *
     * @param memberId     会员ID
     * @param templateCode 模板编码
     * @param now          当前时间
     * @return 可用记录；无则 null
     */
    SubscribeAuth selectUsableAuth(@Param("memberId") Long memberId,
            @Param("templateCode") String templateCode, @Param("now") LocalDateTime now);
}
