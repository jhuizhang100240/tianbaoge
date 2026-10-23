package com.tbgbook.bookstore.common.constant;

/**
 * 业务错误码。
 *
 * <p>取值与语义**严格对齐** {@code docs/api-documentation.md} §业务错误码 的 13 个码，
 * 新增码之前先改接口文档规格文件（{@code docs/api-gen/}）并重跑生成器。
 *
 * <p><b>为什么需要这个类</b>：根 {@code CLAUDE.md} §6.1 要求「禁止在业务代码里散写数字字面量」。
 * 裸写 {@code throw new ServiceException("库存不足")} 不带错误码，前端无从分支处理。
 *
 * <p><b>放哪个模块</b>：按 {@code plan/待确认项.md} G8 的裁决放在
 * {@code bookstore-platform} 的公共包 —— 业务错误码只对业务可用，平台侧引不到，边界更清晰。
 *
 * <p>用法：优先用 {@link com.tbgbook.bookstore.common.exception.BizErrors} 的静态工厂，
 * 它把码与文案一起给出，比手工 new ServiceException 不易出错。
 *
 * @author tbgbook
 */
public final class BizErrorCode
{
    /** 成功 */
    public static final int SUCCESS = 200;

    /** 请求参数格式错误（类型、必填、长度） */
    public static final int BAD_REQUEST = 400;

    /** 未认证 / Token 失效 */
    public static final int UNAUTHORIZED = 401;

    /** 无权限（RBAC 校验不通过） */
    public static final int FORBIDDEN = 403;

    /** 资源不存在 */
    public static final int NOT_FOUND = 404;

    /** 该资源不提供此操作（如对流水表执行删除） */
    public static final int NOT_SUPPORTED = 405;

    /** 资源冲突（唯一键重复、状态已变更、幂等键重复、乐观锁冲突） */
    public static final int CONFLICT = 409;

    /** 资源已过期（下载中心文件过期） */
    public static final int GONE = 410;

    /** 上传文件过大 */
    public static final int PAYLOAD_TOO_LARGE = 413;

    /** 业务规则校验不通过 */
    public static final int RULE_VIOLATED = 422;

    /** 账号已锁定（连续登录失败） */
    public static final int ACCOUNT_LOCKED = 423;

    /** 超出限流（AI 会话配额） */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 服务器内部错误 */
    public static final int SERVER_ERROR = 500;

    /** 依赖服务不可用（AI 服务降级） */
    public static final int SERVICE_UNAVAILABLE = 503;

    private BizErrorCode()
    {
    }
}
