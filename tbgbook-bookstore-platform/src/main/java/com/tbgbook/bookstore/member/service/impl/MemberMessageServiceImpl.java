package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.domain.MessageSendRequest;
import com.tbgbook.bookstore.member.domain.MessageSendResult;
import com.tbgbook.bookstore.member.domain.MessageTemplate;
import com.tbgbook.bookstore.member.domain.SubscribeAuth;
import com.tbgbook.bookstore.member.domain.TargetPreviewRequest;
import com.tbgbook.bookstore.member.domain.TargetPreviewResult;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberMessageMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagMapper;
import com.tbgbook.bookstore.member.mapper.MessageTemplateMapper;
import com.tbgbook.bookstore.member.mapper.SubscribeAuthMapper;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员消息 Service 实现（阶段五步骤③c-2，页面 A-51）。
 *
 * <p><b>自定口径（登记 A40）</b>：
 * <ol>
 *   <li><b>同步扇出</b>：契约写「异步任务，本接口立即返回」，按 A27 先例先做同步扇出
 *       （本步骤不引入 MQ / 线程池），{@code taskId} 取首条消息ID；失败原因逐会员落
 *       {@code failReason}；</li>
 *   <li><b>圈选</b>：预览 targetType 1=标签ID（逗号分隔多个，命中其一即入圈）/ 2=等级ID /
 *       3=消费区间（{@code 500-2000} 闭区间）；发送 targetType 1=标签ID / 2=等级ID /
 *       3=**手工名单（会员ID 逗号分隔）**；一律排除已注销（status=4）；</li>
 *   <li><b>可触达</b>：{@code subscribeAvailable} = 命中人群中存在可用订阅授权的人数
 *       （authStatus=1 且 usedTimes &lt; authTimes 且未过期，now 取应用侧时间，A32）；</li>
 *   <li><b>占位符白名单</b>：{@code {nickname}/{昵称}}、{@code {realName}/{姓名}}、
 *       {@code {memberNo}/{会员卡号}}、{@code {bizNo}/{业务单号}}、{@code {date}/{日期}}；
 *       出现白名单外的占位符 → 422（模板保存与发送两处都校验）；</li>
 *   <li><b>渠道语义</b>：1微信订阅消息 = 有可用授权则扣 1 次并置 2已发送（真实微信调用留阶段九），
 *       无授权置 3发送失败「未授权订阅消息或授权次数已用完」；2站内信 = 直接置 2已发送；
 *       3短信 = 阶段九才接入短信通道，一律置 3发送失败；</li>
 *   <li><b>消息生命周期</b>：人工 {@code POST /member/message} 落库即「1待发送」；**仅 1待发送可改**，
 *       已发送（2）不可改不可删（留痕），发送失败（3）不可改但可删；</li>
 *   <li><b>模板</b>：{@code templateCode} 唯一（409）；模板保存即校验占位符白名单；
 *       停用模板不能用于发送（404「不存在或已停用」）；</li>
 *   <li><b>试推</b>：{@code testSend=true} 只取目标人群中的第一个会员 —— 后台账号本身不是会员，
 *       无法真「推给本人」，按「先发给名单第一人预览渲染效果」实现。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberMessageServiceImpl extends ServiceImpl<MemberMessageMapper, MemberMessage>
        implements IMemberMessageService
{
    /** 消息发送状态：1待发送 */
    private static final int SEND_PENDING = 1;

    /** 消息发送状态：2已发送 */
    private static final int SEND_SENT = 2;

    /** 消息发送状态：3发送失败 */
    private static final int SEND_FAILED = 3;

    /** 发送渠道：1微信订阅消息 */
    private static final int CHANNEL_WECHAT = 1;

    /** 发送渠道：2站内信 */
    private static final int CHANNEL_INBOX = 2;

    /** 发送渠道：3短信 */
    private static final int CHANNEL_SMS = 3;

    /** 模板状态：0正常 */
    private static final String TEMPLATE_NORMAL = "0";

    /** 会员状态：4已注销 */
    private static final int MEMBER_CANCELLED = 4;

    /** 未授权 / 次数用尽的标准失败原因（B-27） */
    private static final String FAIL_NO_AUTH = "未授权订阅消息或授权次数已用完";

    /** 短信通道未接入的失败原因（真实短信调用留阶段九） */
    private static final String FAIL_SMS_OFFLINE = "短信通道未接入（阶段九）";

    /** 占位符形态：一个或多个 {xxx} */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

    /** 占位符白名单（键为花括号内的内容，A40） */
    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "nickname", "realName", "memberNo", "bizNo", "date",
            "昵称", "姓名", "会员卡号", "业务单号", "日期");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberTagMapper memberTagMapper;

    private final MemberLevelMapper memberLevelMapper;

    private final MessageTemplateMapper messageTemplateMapper;

    private final SubscribeAuthMapper subscribeAuthMapper;

    private final JdbcTemplate jdbcTemplate;

    // ==================================================================
    // 会员消息 CRUD
    // ==================================================================

    @Override
    public List<MemberMessage> selectMemberMessageList(MemberMessage message)
    {
        return baseMapper.selectMemberMessageList(message);
    }

    @Override
    public MemberMessage selectMemberMessageById(Long messageId)
    {
        return referenceGuard.requireExists(baseMapper, messageId, "会员消息");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberMessage(MemberMessage message)
    {
        requireMessageFields(message);
        referenceGuard.requireExists(memberMapper, message.getMemberId(), "会员");
        message.setMessageId(null);
        message.setTitle(message.getTitle().trim());
        if (message.getContent() == null)
        {
            message.setContent("");
        }
        if (message.getLinkUrl() == null)
        {
            message.setLinkUrl("");
        }
        if (message.getBizNo() == null)
        {
            message.setBizNo("");
        }
        if (message.getSendChannel() == null)
        {
            message.setSendChannel(CHANNEL_WECHAT);
        }
        // 人工消息落库即待发送；发送状态 / 时间 / 已读只能由业务流转写入
        message.setSendStatus(SEND_PENDING);
        message.setFailReason("");
        message.setSendTime(null);
        message.setIsRead(0);
        message.setReadTime(null);
        message.setCreateTime(LocalDateTime.now());
        return baseMapper.insert(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberMessage(MemberMessage message)
    {
        if (message.getMessageId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的会员消息");
        }
        MemberMessage existing = referenceGuard.requireExists(baseMapper, message.getMessageId(), "会员消息");
        if (!Integer.valueOf(SEND_PENDING).equals(existing.getSendStatus()))
        {
            throw BizErrors.ruleViolated("仅「待发送」的会员消息可以修改（当前 sendStatus="
                    + existing.getSendStatus() + "）");
        }
        Long memberId = (message.getMemberId() == null) ? existing.getMemberId() : message.getMemberId();
        referenceGuard.requireExists(memberMapper, memberId, "会员");
        if (message.getSendChannel() != null)
        {
            requireChannel(message.getSendChannel());
        }
        if (message.getMsgType() != null) {
            requireMsgType(message.getMsgType());
        }
        if (message.getTitle() != null)
        {
            if (message.getTitle().isBlank())
            {
                throw BizErrors.ruleViolated("标题不能为空");
            }
            message.setTitle(message.getTitle().trim());
        }
        // 服务端接管字段：发送状态 / 发送时间 / 已读 / 失败原因一律从库里拷回
        message.setMemberId(memberId);
        message.setSendStatus(existing.getSendStatus());
        message.setFailReason(existing.getFailReason());
        message.setSendTime(existing.getSendTime());
        message.setIsRead(existing.getIsRead());
        message.setReadTime(existing.getReadTime());
        message.setCreateTime(existing.getCreateTime());
        return baseMapper.updateById(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberMessages(Long[] messageIds)
    {
        List<Long> idList = (messageIds == null) ? Collections.emptyList() : Arrays.asList(messageIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员消息");
        for (Serializable id : ids)
        {
            MemberMessage row = referenceGuard.requireExists(baseMapper, id, "会员消息");
            if (Integer.valueOf(SEND_SENT).equals(row.getSendStatus()))
            {
                throw BizErrors.ruleViolated("已发送的会员消息需要留痕，不能删除（messageId=" + id + "）");
            }
        }
        // biz_message 无 del_flag —— 物理删除，仅对未发送 / 发送失败的行开放
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 消息模板 CRUD
    // ==================================================================

    @Override
    public List<MessageTemplate> selectMessageTemplateList(MessageTemplate template)
    {
        return messageTemplateMapper.selectMessageTemplateList(template);
    }

    @Override
    public MessageTemplate selectMessageTemplateById(Long templateId)
    {
        return referenceGuard.requireExists(messageTemplateMapper, templateId, "消息模板");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMessageTemplate(MessageTemplate template)
    {
        normalizeTemplate(template);
        requireTemplateCodeUnique(template.getTemplateCode(), null);
        LocalDateTime now = LocalDateTime.now();
        template.setTemplateId(null);
        template.setCreateBy(currentUsername());
        template.setCreateTime(now);
        template.setUpdateBy(currentUsername());
        template.setUpdateTime(now);
        return messageTemplateMapper.insert(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMessageTemplate(MessageTemplate template)
    {
        if (template.getTemplateId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的消息模板");
        }
        MessageTemplate existing =
                referenceGuard.requireExists(messageTemplateMapper, template.getTemplateId(), "消息模板");
        if (template.getTemplateCode() == null)
        {
            template.setTemplateCode(existing.getTemplateCode());
        }
        if (template.getTemplateName() == null)
        {
            template.setTemplateName(existing.getTemplateName());
        }
        if (template.getMsgType() == null)
        {
            template.setMsgType(existing.getMsgType());
        }
        if (template.getWxTemplateId() == null)
        {
            template.setWxTemplateId(existing.getWxTemplateId());
        }
        normalizeTemplate(template);
        requireTemplateCodeUnique(template.getTemplateCode(), template.getTemplateId());
        template.setUpdateBy(currentUsername());
        template.setUpdateTime(LocalDateTime.now());
        template.setCreateBy(existing.getCreateBy());
        template.setCreateTime(existing.getCreateTime());
        return messageTemplateMapper.updateById(template);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMessageTemplates(Long[] templateIds)
    {
        List<Long> idList = (templateIds == null) ? Collections.emptyList() : Arrays.asList(templateIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "消息模板");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(messageTemplateMapper, id, "消息模板");
        }
        // 逻辑删除（@TableLogic）：已发送的历史消息保留模板编码快照，不做物理解引用
        return messageTemplateMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 订阅授权（只读）
    // ==================================================================

    @Override
    public List<SubscribeAuth> selectSubscribeAuthList(SubscribeAuth auth)
    {
        return subscribeAuthMapper.selectSubscribeAuthList(auth);
    }

    @Override
    public SubscribeAuth selectSubscribeAuthById(Long authId)
    {
        return referenceGuard.requireExists(subscribeAuthMapper, authId, "订阅授权记录");
    }

    // ==================================================================
    // 圈选预览 / 发送
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public TargetPreviewResult previewTarget(TargetPreviewRequest request)
    {
        if (request == null)
        {
            throw BizErrors.badRequest("圈选参数不能为空");
        }
        Integer targetType = request.getTargetType();
        String targetValue = trimToNull(request.getTargetValue());
        if (targetType == null || targetValue == null)
        {
            throw BizErrors.badRequest("targetType 与 targetValue 不能为空");
        }
        List<Long> targetIds = selectTargetMemberIds(targetType, targetValue, false);
        Set<Long> usable = new HashSet<>(subscribeAuthMapper.selectUsableMemberIds(null, LocalDateTime.now()));
        int reachable = 0;
        for (Long memberId : targetIds)
        {
            if (usable.contains(memberId))
            {
                reachable++;
            }
        }
        TargetPreviewResult result = new TargetPreviewResult();
        result.setCount(targetIds.size());
        result.setSubscribeAvailable(reachable);
        result.setUnreachable(targetIds.size() - reachable);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageSendResult send(MessageSendRequest request)
    {
        validateSendRequest(request);
        MessageTemplate template = findSendableTemplate(request.getTemplateCode());
        render(template, null, request.getBizNo());
        List<Long> targetIds = selectTargetMemberIds(request.getTargetType(),
                request.getTargetValue().trim(), true);
        if (Boolean.TRUE.equals(request.getTestSend()) && targetIds.size() > 1)
        {
            targetIds = new ArrayList<>(targetIds.subList(0, 1));
        }
        List<Member> members = selectMembers(targetIds);
        if (members.isEmpty())
        {
            throw BizErrors.ruleViolated("目标人群为空，没有可发送的会员");
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> available = new LinkedHashSet<>();
        if (request.getSendChannel() == CHANNEL_WECHAT)
        {
            available.addAll(subscribeAuthMapper.selectUsableMemberIds(
                    template.getTemplateCode(), now));
        }
        int reachable = 0;
        Long firstMessageId = null;
        for (Member member : members)
        {
            if (request.getSendChannel() == CHANNEL_WECHAT && available.contains(member.getMemberId()))
            {
                reachable++;
            }
            Long messageId = dispatch(template, member, request, now);
            if (firstMessageId == null)
            {
                firstMessageId = messageId;
            }
        }
        MessageSendResult result = new MessageSendResult();
        result.setTaskId(firstMessageId);
        result.setTargetCount(members.size());
        result.setSubscribeAvailable(reachable);
        return result;
    }

    // ==================================================================
    // 内部：发送与模板渲染
    // ==================================================================

    /** 单会员发送：写一条 {@code biz_message}，微信渠道同时扣减订阅次数 */
    private Long dispatch(MessageTemplate template, Member member, MessageSendRequest request,
            LocalDateTime now)
    {
        Integer channel = request.getSendChannel();
        int sendStatus = SEND_SENT;
        String failReason = "";
        LocalDateTime sendTime = now;
        if (channel == CHANNEL_WECHAT)
        {
            SubscribeAuth auth = subscribeAuthMapper.selectUsableAuth(
                    member.getMemberId(), template.getTemplateCode(), now);
            if (auth == null || subscribeAuthMapper.consumeAuth(auth.getAuthId()) <= 0)
            {
                sendStatus = SEND_FAILED;
                failReason = FAIL_NO_AUTH;
                sendTime = null;
            }
        }
        else if (channel == CHANNEL_SMS)
        {
            sendStatus = SEND_FAILED;
            failReason = FAIL_SMS_OFFLINE;
            sendTime = null;
        }
        MemberMessage message = new MemberMessage();
        message.setMemberId(member.getMemberId());
        message.setMsgType(template.getMsgType() == null ? 6 : template.getMsgType());
        message.setTitle(template.getTemplateName());
        message.setContent(render(template, member, request.getBizNo()));
        message.setLinkUrl(request.getLinkUrl() == null ? "" : request.getLinkUrl().trim());
        message.setBizNo(request.getBizNo() == null ? "" : request.getBizNo().trim());
        message.setSendChannel(channel);
        message.setSendStatus(sendStatus);
        message.setFailReason(failReason);
        message.setSendTime(sendTime);
        message.setIsRead(0);
        message.setReadTime(null);
        message.setCreateTime(now);
        baseMapper.insert(message);
        return message.getMessageId();
    }

    /** 渲染模板：白名单外的占位符直接 422（{@code member=null} 时仅做语法校验） */
    private String render(MessageTemplate template, Member member, String bizNo)
    {
        String content = (template.getContentTpl() == null) ? "" : template.getContentTpl();
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder rendered = new StringBuilder();
        boolean missingBizNo = false;
        while (matcher.find())
        {
            String key = matcher.group(1).trim();
            if (!PLACEHOLDER_KEYS.contains(key))
            {
                throw BizErrors.ruleViolated("模板含不支持的占位符 {" + key + "}；支持："
                        + "{nickname}/{昵称}、{realName}/{姓名}、{memberNo}/{会员卡号}、"
                        + "{bizNo}/{业务单号}、{date}/{日期}");
            }
            if (("bizNo".equals(key) || "业务单号".equals(key))
                    && (bizNo == null || bizNo.isBlank()))
            {
                missingBizNo = true;
            }
            String value = (member == null) ? "" : placeholderValue(key, member, bizNo);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        if (member != null && missingBizNo)
        {
            throw BizErrors.ruleViolated("模板含 {业务单号} 占位符，但请求未提供 bizNo");
        }
        return rendered.toString();
    }

    /** 单词占位符取值（缺失用空串，避免把 null 字面量渲染进消息） */
    private String placeholderValue(String key, Member member, String bizNo)
    {
        String value;
        switch (key)
        {
            case "nickname":
            case "昵称":
                value = member.getNickname();
                break;
            case "realName":
            case "姓名":
                value = member.getRealName();
                break;
            case "memberNo":
            case "会员卡号":
                value = member.getMemberNo();
                break;
            case "bizNo":
            case "业务单号":
                value = bizNo;
                break;
            case "date":
            case "日期":
                value = LocalDate.now().format(DATE_FORMAT);
                break;
            default:
                value = "";
        }
        if (value == null || value.isBlank())
        {
            return ("nickname".equals(key) || "昵称".equals(key)) ? nz(member.getMemberNo()) : "";
        }
        return value.trim();
    }

    /** 发送前校验模板：不存在或停用 → 404 */
    private MessageTemplate findSendableTemplate(String templateCode)
    {
        MessageTemplate template = messageTemplateMapper.selectOne(
                new LambdaQueryWrapper<MessageTemplate>()
                        .eq(MessageTemplate::getTemplateCode, templateCode.trim()));
        if (template == null || !TEMPLATE_NORMAL.equals(template.getStatus()))
        {
            throw BizErrors.notFound("消息模板不存在或已停用（templateCode=" + templateCode + "）");
        }
        return template;
    }

    private void validateSendRequest(MessageSendRequest request)
    {
        if (request == null)
        {
            throw BizErrors.badRequest("发送参数不能为空");
        }
        if (trimToNull(request.getTemplateCode()) == null)
        {
            throw BizErrors.badRequest("templateCode 不能为空");
        }
        if (request.getTargetType() == null || trimToNull(request.getTargetValue()) == null)
        {
            throw BizErrors.badRequest("targetType 与 targetValue 不能为空");
        }
        if (request.getSendChannel() == null
                || request.getSendChannel() < CHANNEL_WECHAT
                || request.getSendChannel() > CHANNEL_SMS)
        {
            throw BizErrors.ruleViolated("发送渠道取值必须是 1微信 / 2站内信 / 3短信");
        }
    }

    // ==================================================================
    // 内部：圈选人群
    // ==================================================================

    /**
     * 圈选会员ID（排除已注销）。
     *
     * @param targetType 1标签 2等级 3消费区间（预览）/ 手工名单（发送）
     * @param targetValue 圈选值
     * @param forSend     是否发送场景（决定 3 的语义：区间 vs 会员名单）
     * @return 命中的会员ID（按会员ID升序，便于 testSend 取第一人）
     */
    private List<Long> selectTargetMemberIds(Integer targetType, String targetValue, boolean forSend)
    {
        StringBuilder sql = new StringBuilder(
                "select distinct m.member_id from biz_member m where m.status <> ?");
        List<Object> args = new ArrayList<>();
        args.add(MEMBER_CANCELLED);
        switch (targetType)
        {
            case 1:
            {
                List<Long> tagIds = parseIds(targetValue, "标签ID");
                for (Long tagId : tagIds)
                {
                    referenceGuard.requireExists(memberTagMapper, tagId, "会员标签");
                }
                sql.append(" and exists (select 1 from biz_member_tag_rel r where r.member_id = m.member_id")
                        .append(" and r.tag_id in (").append(placeholders(tagIds.size())).append("))");
                args.addAll(tagIds);
                break;
            }
            case 2:
            {
                List<Long> levelIds = parseIds(targetValue, "等级ID");
                if (levelIds.size() > 1)
                {
                    throw BizErrors.badRequest("按等级圈选只能传一个等级ID");
                }
                Long levelId = levelIds.get(0);
                referenceGuard.requireExists(memberLevelMapper, levelId, "会员等级");
                sql.append(" and m.level_id = ?");
                args.add(levelId);
                break;
            }
            case 3:
            {
                if (forSend)
                {
                    List<Long> memberIds = parseIds(targetValue, "会员ID");
                    sql.append(" and m.member_id in (").append(placeholders(memberIds.size())).append(")");
                    args.addAll(memberIds);
                }
                else
                {
                    BigDecimal[] range = parseAmountRange(targetValue);
                    sql.append(" and m.consume_amount >= ? and m.consume_amount <= ?");
                    args.add(range[0]);
                    args.add(range[1]);
                }
                break;
            }
            default:
                throw BizErrors.ruleViolated("圈选方式取值必须是 1按标签 / 2按等级 / 3"
                        + (forSend ? "手工名单" : "消费区间"));
        }
        sql.append(" order by m.member_id asc");
        List<Long> ids = jdbcTemplate.queryForList(sql.toString(), Long.class, args.toArray());
        if (ids.isEmpty())
        {
            throw BizErrors.ruleViolated("目标人群为空，没有符合条件的会员");
        }
        return ids;
    }

    /** 按主键批量取会员（保持圈选顺序，供发送渲染） */
    private List<Member> selectMembers(List<Long> memberIds)
    {
        if (memberIds.isEmpty())
        {
            return Collections.emptyList();
        }
        List<Member> rows = memberMapper.selectList(new LambdaQueryWrapper<Member>()
                .in(Member::getMemberId, memberIds)
                .ne(Member::getStatus, MEMBER_CANCELLED));
        List<Member> ordered = new ArrayList<>(memberIds.size());
        Set<Long> remain = new LinkedHashSet<>(memberIds);
        for (Long id : memberIds)
        {
            for (Member row : rows)
            {
                if (row.getMemberId().equals(id) && remain.contains(id))
                {
                    ordered.add(row);
                    remain.remove(id);
                    break;
                }
            }
        }
        return ordered;
    }

    /** 解析逗号分隔的ID串（去重、保序、正整数） */
    private List<Long> parseIds(String raw, String label)
    {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : raw.split(","))
        {
            String token = part.trim();
            if (token.isEmpty())
            {
                continue;
            }
            long id;
            try
            {
                id = Long.parseLong(token);
            }
            catch (NumberFormatException e)
            {
                throw BizErrors.badRequest(label + "格式不正确：" + token);
            }
            if (id <= 0)
            {
                throw BizErrors.badRequest(label + "必须是正整数：" + token);
            }
            ids.add(id);
        }
        if (ids.isEmpty())
        {
            throw BizErrors.badRequest(label + "不能为空");
        }
        return new ArrayList<>(ids);
    }

    /** 解析消费区间 {@code 500-2000}（闭区间，允许小数） */
    private BigDecimal[] parseAmountRange(String raw)
    {
        String value = raw.replace("~", "-").replace("～", "-").replace(" ", "");
        int dash = value.indexOf('-');
        if (dash <= 0 || dash == value.length() - 1)
        {
            throw BizErrors.badRequest("消费区间格式应为 500-2000");
        }
        BigDecimal min;
        BigDecimal max;
        try
        {
            min = new BigDecimal(value.substring(0, dash));
            max = new BigDecimal(value.substring(dash + 1));
        }
        catch (NumberFormatException e)
        {
            throw BizErrors.badRequest("消费区间格式应为 500-2000");
        }
        if (min.signum() < 0 || max.signum() < 0)
        {
            throw BizErrors.ruleViolated("消费区间不能为负数");
        }
        if (min.compareTo(max) > 0)
        {
            throw BizErrors.ruleViolated("消费区间下限不能大于上限");
        }
        return new BigDecimal[] { min, max };
    }

    /** 生成 n 个 {@code ?} 占位符 */
    private String placeholders(int count)
    {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    // ==================================================================
    // 内部：字段校验
    // ==================================================================

    /** 人工消息必填校验（msgType / sendChannel 枚举） */
    private void requireMessageFields(MemberMessage message)
    {
        if (message.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        requireMsgType(message.getMsgType());
        if (message.getTitle() == null || message.getTitle().isBlank())
        {
            throw BizErrors.ruleViolated("标题不能为空");
        }
        if (message.getTitle().trim().length() > 200)
        {
            throw BizErrors.ruleViolated("标题不能超过 200 个字符");
        }
        if (message.getContent() != null && message.getContent().length() > 1000)
        {
            throw BizErrors.ruleViolated("内容不能超过 1000 个字符");
        }
        if (message.getSendChannel() != null)
        {
            requireChannel(message.getSendChannel());
        }
    }

    private void requireMsgType(Integer msgType)
    {
        if (msgType == null || msgType < 1 || msgType > 7)
        {
            throw BizErrors.ruleViolated("消息类型取值必须是 1~7");
        }
    }

    private void requireChannel(Integer channel)
    {
        if (channel == null || channel < CHANNEL_WECHAT || channel > CHANNEL_SMS)
        {
            throw BizErrors.ruleViolated("发送渠道取值必须是 1微信 / 2站内信 / 3短信");
        }
    }

    /** 模板字段规范：编码大写去空格、状态默认正常、空串兜底、占位符白名单校验 */
    private void normalizeTemplate(MessageTemplate template)
    {
        if (template.getTemplateCode() == null || template.getTemplateCode().isBlank())
        {
            throw BizErrors.badRequest("模板编码不能为空");
        }
        String code = template.getTemplateCode().trim().toUpperCase(Locale.ROOT);
        if (code.length() > 50)
        {
            throw BizErrors.ruleViolated("模板编码不能超过 50 个字符");
        }
        template.setTemplateCode(code);
        if (template.getTemplateName() == null || template.getTemplateName().isBlank())
        {
            throw BizErrors.badRequest("模板名称不能为空");
        }
        template.setTemplateName(template.getTemplateName().trim());
        requireMsgType(template.getMsgType());
        if (template.getStatus() == null || template.getStatus().isBlank())
        {
            template.setStatus(TEMPLATE_NORMAL);
        }
        else if (!TEMPLATE_NORMAL.equals(template.getStatus()) && !"1".equals(template.getStatus()))
        {
            throw BizErrors.ruleViolated("模板状态取值必须是 0正常 / 1停用");
        }
        if (template.getWxTemplateId() == null)
        {
            template.setWxTemplateId("");
        }
        if (template.getRemark() == null)
        {
            template.setRemark("");
        }
        template.setContentTpl(template.getContentTpl() == null ? "" : template.getContentTpl());
        if (template.getContentTpl().length() > 1000)
        {
            throw BizErrors.ruleViolated("内容模板不能超过 1000 个字符");
        }
        render(template, null, null);
    }

    private void requireTemplateCodeUnique(String templateCode, Long excludeTemplateId)
    {
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getTemplateCode, templateCode);
        if (excludeTemplateId != null)
        {
            wrapper.ne(MessageTemplate::getTemplateId, excludeTemplateId);
        }
        if (messageTemplateMapper.selectCount(wrapper) > 0)
        {
            throw BizErrors.duplicate("消息模板", "模板编码", templateCode);
        }
    }

    // ==================================================================
    // 内部：杂项
    // ==================================================================

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nz(String value)
    {
        return value == null ? "" : value;
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return name == null ? "" : name;
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
