package com.tbgbook.bookstore.app.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.domain.request.AppLoginRequest;
import com.tbgbook.bookstore.app.domain.request.StudentCertRequest;
import com.tbgbook.bookstore.app.service.IAppAuthService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端认证 Controller（B-01/B-02）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/auth")
public class AppAuthController extends BaseController
{
    private final IAppAuthService appAuthService;

    @PostMapping("/login")
    public AjaxResult login(@Validated @RequestBody AppLoginRequest request)
    {
        return success(appAuthService.login(request));
    }

    @PostMapping("/student-cert")
    public AjaxResult submitStudentCert(@Validated @RequestBody StudentCertRequest request)
    {
        return success(appAuthService.submitStudentCert(request));
    }

    @GetMapping("/student-cert")
    public AjaxResult studentCertStatus()
    {
        return success(appAuthService.studentCertStatus());
    }

    @PostMapping("/refresh")
    public AjaxResult refresh()
    {
        return success(appAuthService.refresh());
    }
}
