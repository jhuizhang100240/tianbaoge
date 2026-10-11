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

import com.tbgbook.bookstore.member.domain.MessageTemplate;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 消息模板 Controller（页面 A-51）。
 *
 * <p>{@code contentTpl} 支持占位符，白名单见实现类（A40）；保存即校验，
 * 出现白名单外的占位符返回 422。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/message-template")
public class MessageTemplateController extends BaseController
{
    private final IMemberMessageService memberMessageService;

    /**
     * 查询消息模板列表（可按编码 / 名称 / 类型 / 状态与时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(MessageTemplate template)
    {
        startPage();
        List<MessageTemplate> list = memberMessageService.selectMessageTemplateList(template);
        return getDataTable(list);
    }

    /**
     * 获取消息模板详情。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/{templateId}")
    public AjaxResult getInfo(@PathVariable("templateId") Long templateId)
    {
        return success(memberMessageService.selectMessageTemplateById(templateId));
    }

    /**
     * 新增消息模板（templateCode 唯一）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:add')")
    @Log(title = "消息模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MessageTemplate template)
    {
        return toAjax(memberMessageService.addMessageTemplate(template));
    }

    /**
     * 修改消息模板（支持部分字段；必填校验在 Service 内按「合并后的值」执行）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:edit')")
    @Log(title = "消息模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody MessageTemplate template)
    {
        return toAjax(memberMessageService.updateMessageTemplate(template));
    }

    /**
     * 删除消息模板（逻辑删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:remove')")
    @Log(title = "消息模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable Long[] templateIds)
    {
        return toAjax(memberMessageService.removeMessageTemplates(templateIds));
    }
}
