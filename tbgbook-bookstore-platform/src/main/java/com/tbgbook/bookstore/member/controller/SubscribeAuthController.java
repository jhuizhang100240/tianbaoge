package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.SubscribeAuth;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 订阅消息授权记录 Controller（页面 A-51，B-27）。
 *
 * <p>**只读资源**：授权由小程序端回写，次数由发送动作扣减；
 * 后台只能查询，不提供 POST / PUT / DELETE。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/subscribe-auth")
public class SubscribeAuthController extends BaseController
{
    private final IMemberMessageService memberMessageService;

    /**
     * 查询订阅授权记录列表（可按会员 / 模板编码 / 状态与时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(SubscribeAuth auth)
    {
        startPage();
        List<SubscribeAuth> list = memberMessageService.selectSubscribeAuthList(auth);
        return getDataTable(list);
    }

    /**
     * 获取订阅授权记录详情。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/{authId}")
    public AjaxResult getInfo(@PathVariable("authId") Long authId)
    {
        return success(memberMessageService.selectSubscribeAuthById(authId));
    }
}
