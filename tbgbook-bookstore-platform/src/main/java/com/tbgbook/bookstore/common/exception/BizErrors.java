package com.tbgbook.bookstore.common.exception;

import com.tbgbook.bookstore.common.constant.BizErrorCode;
import com.tbgbook.common.exception.ServiceException;

/**
 * 业务异常工厂。
 *
 * <p>把 {@link BizErrorCode} 的错误码与可读文案一起给出，避免各处手工
 * {@code new ServiceException("...", 422)} 时漏带码或码与语义不符。
 * 抛出的仍是 {@link ServiceException}，由 {@code GlobalExceptionHandler} 统一转成响应，
 * **不把堆栈返回前端**（根 {@code CLAUDE.md} §6.3）。
 *
 * <p><b>文案要求</b>（前端视觉规范 §6.10）：说清「发生了什么」与「怎么办」，
 * 不道歉、不模糊。反例：{@code 「操作失败」}；正例：{@code 「库存不足，当前可售 3 册」}。
 *
 * @author tbgbook
 */
public final class BizErrors
{
    private BizErrors()
    {
    }

    // ------------------------------------------------------------------
    // 按错误码逐档
    // ------------------------------------------------------------------

    /** 400 请求参数格式错误 */
    public static ServiceException badRequest(String message)
    {
        return new ServiceException(message, BizErrorCode.BAD_REQUEST);
    }

    /** 401 未认证 */
    public static ServiceException unauthorized(String message)
    {
        return new ServiceException(message, BizErrorCode.UNAUTHORIZED);
    }

    /** 403 无权限 —— 代码里一般不主动抛，交由 {@code @PreAuthorize} 拦截 */
    public static ServiceException forbidden(String message)
    {
        return new ServiceException(message, BizErrorCode.FORBIDDEN);
    }

    /** 404 资源不存在 */
    public static ServiceException notFound(String message)
    {
        return new ServiceException(message, BizErrorCode.NOT_FOUND);
    }

    /**
     * 404 资源不存在（带主键，便于排查）
     *
     * @param label 资源中文名，如「图书」「会员」
     * @param id    主键值
     */
    public static ServiceException notFound(String label, Object id)
    {
        return notFound(label + "不存在（" + label + "Id=" + id + "）");
    }

    /** 405 该资源不提供此操作 —— 专门用于 7 张流水表（只增不改不删） */
    public static ServiceException notSupported(String label, String operation)
    {
        return new ServiceException(
                label + "是流水记录，不支持" + operation + "；如需更正请通过业务单据反向冲销",
                BizErrorCode.NOT_SUPPORTED);
    }

    /** 409 资源冲突 */
    public static ServiceException conflict(String message)
    {
        return new ServiceException(message, BizErrorCode.CONFLICT);
    }

    /** 410 资源已过期（下载中心文件超过保留天数） */
    public static ServiceException gone(String message)
    {
        return new ServiceException(message, BizErrorCode.GONE);
    }

    /** 413 上传文件过大 */
    public static ServiceException payloadTooLarge(String message)
    {
        return new ServiceException(message, BizErrorCode.PAYLOAD_TOO_LARGE);
    }

    /** 422 业务规则校验不通过 */
    public static ServiceException ruleViolated(String message)
    {
        return new ServiceException(message, BizErrorCode.RULE_VIOLATED);
    }

    /** 423 账号已锁定 */
    public static ServiceException accountLocked(String message)
    {
        return new ServiceException(message, BizErrorCode.ACCOUNT_LOCKED);
    }

    /** 429 超出限流 */
    public static ServiceException tooManyRequests(String message)
    {
        return new ServiceException(message, BizErrorCode.TOO_MANY_REQUESTS);
    }

    /** 500 服务器内部错误 */
    public static ServiceException serverError(String message)
    {
        return new ServiceException(message, BizErrorCode.SERVER_ERROR);
    }

    /** 503 依赖服务不可用（如 AI 服务降级） */
    public static ServiceException serviceUnavailable(String message)
    {
        return new ServiceException(message, BizErrorCode.SERVICE_UNAVAILABLE);
    }

    // ------------------------------------------------------------------
    // 高频复合场景
    // ------------------------------------------------------------------

    /**
     * 409 唯一键重复。
     *
     * @param label 资源中文名，如「图书」
     * @param field 冲突字段的中文名，如「ISBN」
     * @param value 冲突值
     */
    public static ServiceException duplicate(String label, String field, Object value)
    {
        return conflict(label + "「" + field + "=" + value + "」已存在，不能重复建档");
    }

    /**
     * 409 并发修改冲突 —— 乐观锁未命中时的标准文案。
     *
     * <p>触发点：{@code @Version} 保护的行被他人改过，本次 update 影响 0 行。
     * **必须显式检查影响行数**，否则会静默覆盖他人改动（后端 {@code CLAUDE.md} §五）。
     *
     * @param label 资源中文名
     */
    public static ServiceException concurrentModified(String label)
    {
        return conflict("该" + label + "刚被其他人修改过，请刷新后重试");
    }

    /**
     * 422 被引用不可删除 —— 删除保护的标准文案。
     *
     * <p>需求点名的三处「被引用时禁止删除」：分类（A-09）、部门（A-80）、字典（A-81）；
     * 其余基础数据按同一模式处理（{@code docs/db/README.md} §6.1）。
     *
     * <p>文案刻意**不带量词**（不写「还有 1 个图书」）—— 中文量词随被引用对象而变
     * （种书 / 本册 / 条记录 / 个分类），模板里无法用同一个字兜住，索性改成
     * 「已被 N 条 X 引用」，对任何对象都通顺。
     *
     * @param selfLabel 被删除对象的中文名，如「分类」「出版社」
     * @param refLabel  引用方中文名，如「图书」「会员」
     * @param count     引用数量
     * @param fallback  替代建议，如「改为停用」
     */
    public static ServiceException referenced(String selfLabel, String refLabel, long count, String fallback)
    {
        String tail = (fallback == null || fallback.isEmpty()) ? "" : "，建议" + fallback;
        return ruleViolated("该" + selfLabel + "已被 " + count + " 条" + refLabel + "引用，不能删除" + tail);
    }

    /**
     * 422 状态流转非法。
     *
     * @param label     单据中文名，如「采购入库单」
     * @param fromName  当前状态中文名
     * @param toName    目标状态中文名
     */
    public static ServiceException illegalTransition(String label, String fromName, String toName)
    {
        return ruleViolated(label + "当前为「" + fromName + "」，不能变更为「" + toName + "」");
    }

    /**
     * 422 条件不满足。
     *
     * @param subject 主语，如「可售库存」
     * @param detail  说明，如「当前 3 册，需要 5 册」
     */
    public static ServiceException notEnough(String subject, String detail)
    {
        return ruleViolated(subject + "不足：" + detail);
    }
}
