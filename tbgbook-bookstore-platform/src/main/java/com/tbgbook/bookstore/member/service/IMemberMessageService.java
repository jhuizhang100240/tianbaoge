package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.domain.MessageSendRequest;
import com.tbgbook.bookstore.member.domain.MessageSendResult;
import com.tbgbook.bookstore.member.domain.MessageTemplate;
import com.tbgbook.bookstore.member.domain.SubscribeAuth;
import com.tbgbook.bookstore.member.domain.TargetPreviewRequest;
import com.tbgbook.bookstore.member.domain.TargetPreviewResult;

/**
 * 会员消息 Service 接口（阶段五步骤③c-2，页面 A-51，FR-4.4.10）。
 *
 * <p>三块内容：① 会员消息 CRUD（{@code biz_message}，物理删除，只保留「待发送」可改）；
 * ② 消息模板 CRUD（{@code biz_message_template}，模板编码唯一、占位符白名单校验）；
 * ③ 订阅授权只读 + 圈选预览 + 发送（同步扇出，逐会员校验订阅次数）。
 *
 * <p><b>自定口径见实现类（登记 A40）</b>。
 *
 * @author tbgbook
 */
public interface IMemberMessageService
{
    // ------------------------------------------------------------------
    // 会员消息
    // ------------------------------------------------------------------

    /**
     * 查询会员消息列表。
     *
     * @param message 查询条件
     * @return 消息集合
     */
    List<MemberMessage> selectMemberMessageList(MemberMessage message);

    /**
     * 查询会员消息详情。
     *
     * @param messageId 消息ID
     * @return 消息；不存在时抛 404
     */
    MemberMessage selectMemberMessageById(Long messageId);

    /**
     * 新增会员消息（人工单条消息，落库为「待发送」）。
     *
     * @param message 消息
     * @return 影响行数
     */
    int addMemberMessage(MemberMessage message);

    /**
     * 修改会员消息（仅「待发送」可改；已发送 / 发送失败恒 422）。
     *
     * @param message 消息
     * @return 影响行数
     */
    int updateMemberMessage(MemberMessage message);

    /**
     * 删除会员消息（**已发送（sendStatus=2）禁止删除**，保留留痕；物理删除）。
     *
     * @param messageIds 消息主键数组
     * @return 影响行数
     */
    int removeMemberMessages(Long[] messageIds);

    // ------------------------------------------------------------------
    // 消息模板
    // ------------------------------------------------------------------

    /**
     * 查询消息模板列表。
     *
     * @param template 查询条件
     * @return 模板集合
     */
    List<MessageTemplate> selectMessageTemplateList(MessageTemplate template);

    /**
     * 查询消息模板详情。
     *
     * @param templateId 模板ID
     * @return 模板；不存在时抛 404
     */
    MessageTemplate selectMessageTemplateById(Long templateId);

    /**
     * 新增消息模板（templateCode 唯一 409；占位符白名单校验 422）。
     *
     * @param template 模板
     * @return 影响行数
     */
    int addMessageTemplate(MessageTemplate template);

    /**
     * 修改消息模板（不可改为已被占用的 templateCode）。
     *
     * @param template 模板
     * @return 影响行数
     */
    int updateMessageTemplate(MessageTemplate template);

    /**
     * 删除消息模板（逻辑删除）。
     *
     * @param templateIds 模板主键数组
     * @return 影响行数
     */
    int removeMessageTemplates(Long[] templateIds);

    // ------------------------------------------------------------------
    // 订阅授权（只读）
    // ------------------------------------------------------------------

    /**
     * 查询订阅授权记录列表。
     *
     * @param auth 查询条件
     * @return 记录集合
     */
    List<SubscribeAuth> selectSubscribeAuthList(SubscribeAuth auth);

    /**
     * 查询订阅授权记录详情。
     *
     * @param authId 授权ID
     * @return 记录；不存在时抛 404
     */
    SubscribeAuth selectSubscribeAuthById(Long authId);

    // ------------------------------------------------------------------
    // 圈选预览 / 发送
    // ------------------------------------------------------------------

    /**
     * 圈选人群预览：命中数 / 可触达数（有可用订阅授权）/ 不可触达数。
     *
     * @param request 圈选请求（1按标签 2按等级 3按消费区间）
     * @return 预览结果
     */
    TargetPreviewResult previewTarget(TargetPreviewRequest request);

    /**
     * 发送会员消息：按模板渲染并同步扇出，逐会员校验订阅次数。
     *
     * @param request 发送请求
     * @return 任务标识 / 目标人数 / 发送前可触达数
     */
    MessageSendResult send(MessageSendRequest request);
}
