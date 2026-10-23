package com.tbgbook.bookstore.app.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.domain.request.ReserveSubmitRequest;
import com.tbgbook.bookstore.app.service.IAppTextbookService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端教材预订 Controller（B-12）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/reserve")
public class AppReserveController extends BaseController
{
    private final IAppTextbookService textbookService;

    @GetMapping("/textbook-list")
    public AjaxResult lists(
            @RequestParam(value = "term", required = false) String term,
            @RequestParam(value = "major", required = false) String major,
            @RequestParam(value = "college", required = false) String college,
            @RequestParam(value = "listId", required = false) Long listId)
    {
        return success(textbookService.lists(term, major, college, listId));
    }

    @PostMapping
    public AjaxResult submit(@Validated @RequestBody ReserveSubmitRequest request)
    {
        return success(textbookService.submit(request));
    }

    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "status", required = false) Integer status)
    {
        return success(textbookService.myList(status));
    }

    @PostMapping("/{reserveId}/cancel")
    public AjaxResult cancel(@PathVariable("reserveId") Long reserveId,
            @RequestBody(required = false) java.util.Map<String, Object> body)
    {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return success(textbookService.cancel(reserveId, reason));
    }
}
