package com.tbgbook.bookstore.app.service;

import java.util.Map;

import com.tbgbook.bookstore.app.domain.request.AppLoginRequest;
import com.tbgbook.bookstore.app.domain.request.StudentCertRequest;

/**
 * 会员端认证 Service。
 */
public interface IAppAuthService
{
    Map<String, Object> login(AppLoginRequest request);

    Map<String, Object> submitStudentCert(StudentCertRequest request);

    Map<String, Object> studentCertStatus();

    Map<String, Object> refresh();
}
