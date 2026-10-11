package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.domain.MessageSendRequest;
import com.tbgbook.bookstore.member.domain.TargetPreviewRequest;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员消息 Controller（页面 A-51，FR-4.4.10）。
 *
 * <p>CRUD + 圈选预览 + 发送（同步扇出，逐会员校验订阅次数）。
 * 权限前缀沿用 {@code member:tag}，与接口文档一致。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/message")
public class MemberMessageController extends BaseController
{
    private final IMemberMessageService memberMessageService;

    /**
     * 查询会员消息列表（可按会员 / 类型 / 渠道 / 发送状态 / 已读 / 业务单号与时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberMessage message)
    {
        startPage();
        List<MemberMessage> list = memberMessageService.selectMemberMessageList(message);
        return getDataTable(list);
    }

    /**
     * 获取会员消息详情。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        return success(memberMessageService.selectMemberMessageById(messageId));
    }

    /**
     * 新增会员消息（人工消息，落库即「待发送」）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:add')")
    @Log(title = "会员消息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberMessage message)
    {
        return toAjax(memberMessageService.addMemberMessage(message));
    }

    /**
     * 修改会员消息（仅「待发送」可改）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:edit')")
    @Log(title = "会员消息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberMessage message)
    {
        return toAjax(memberMessageService.updateMemberMessage(message));
    }

    /**
     * 删除会员消息（已发送的消息需要留痕，禁止删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:remove')")
    @Log(title = "会员消息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{messageIds}")
    public AjaxResult remove(@PathVariable Long[] messageIds)
    {
        return toAjax(memberMessageService.removeMemberMessages(messageIds));
    }

    /**
     * 圈选人群预览：命中数 / 可触达数 / 不可触达数。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:targetPreview')")
    @PostMapping("/target-preview")
    public AjaxResult targetPreview(@RequestBody TargetPreviewRequest request)
    {
        return success(memberMessageService.previewTarget(request));
    }

    /**
     * 发送会员消息（按模板渲染并逐会员校验订阅次数）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:send')")
    @Log(title = "会员消息", businessType = BusinessType.INSERT)
    @PostMapping("/send")
    public AjaxResult send(@RequestBody MessageSendRequest request)
    {
        return success(memberMessageService.send(request));
    }
}
