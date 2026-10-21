package com.tbgbook.bookstore.app.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.domain.request.RecycleApplyRequest;
import com.tbgbook.bookstore.app.service.IAppRecycleService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端二手回收 Controller（B-13/B-14）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/recycle")
public class AppRecycleController extends BaseController
{
    private final IAppRecycleService recycleService;

    @PostMapping("/apply")
    public AjaxResult apply(@Validated @RequestBody RecycleApplyRequest request)
    {
        return success(recycleService.apply(request));
    }

    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "status", required = false) Integer status)
    {
        return success(recycleService.list(status));
    }

    @GetMapping("/{recycleId}")
    public AjaxResult detail(@PathVariable("recycleId") Long recycleId)
    {
        return success(recycleService.detail(recycleId));
    }

    @PostMapping("/{recycleId}/cancel")
    public AjaxResult cancel(@PathVariable("recycleId") Long recycleId,
            @RequestBody(required = false) java.util.Map<String, Object> body)
    {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return success(recycleService.cancel(recycleId, reason));
    }
}
