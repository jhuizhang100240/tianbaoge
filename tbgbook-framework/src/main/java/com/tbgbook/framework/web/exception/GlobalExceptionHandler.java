package com.tbgbook.framework.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.unit.DataSize;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Objects;
import com.tbgbook.common.constant.HttpStatus;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.text.Convert;
import com.tbgbook.common.exception.DemoModeException;
import com.tbgbook.common.exception.ServiceException;
import com.tbgbook.common.utils.StringUtils;
import com.tbgbook.common.utils.html.EscapeUtil;

/**
 * 全局异常处理器
 * 
 * @author ruoyi
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 容器上传大小上限（与 {@code spring.servlet.multipart.max-file-size} 同源）
     *
     * <p>用来给超限响应填一个**看得懂的上限值** ——
     * {@code MaxUploadSizeExceededException.getMaxUploadSize()} 由 Tomcat 的 multipart
     * 解析抛出时返回 -1，只靠它只能写出「系统限制」这种没有信息量的话（实测踩到）。
     */
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;

    /**
     * 权限校验异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    /**
     * 上传文件超过 {@code spring.servlet.multipart.max-file-size} 限制
     *
     * <p>⚠️ 这个异常由 Servlet 容器的 multipart 解析阶段抛出，**早于** Controller 方法体，
     * 因此业务代码里自己写的「文件超过 10MB 抛 413」根本没机会执行 ——
     * 不接住它，超限上传会掉进兜底的 {@code Exception} 分支变成 500
     * （接口文档要求 413，实测踩到）。
     *
     * <p>与 {@code BizErrors.payloadTooLarge} 同码同义：容器限制是外层兜底，
     * 业务侧的尺寸校验是各接口自己的口径。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public AjaxResult handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletRequest request)
    {
        String limit = resolveUploadLimit(e.getMaxUploadSize());
        log.warn("请求地址'{}',上传文件超过大小限制 {}", request.getRequestURI(), limit);
        return AjaxResult.error(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件超过大小限制（" + limit + "），请压缩后重试");
    }

    /** 取上传上限的可读文本：优先用异常自带的字节数，拿不到时回落到配置值 */
    private String resolveUploadLimit(long maxBytes)
    {
        if (maxBytes > 0)
        {
            return (maxBytes / 1024 / 1024) + "MB";
        }
        try
        {
            return DataSize.parse(maxFileSize).toMegabytes() + "MB";
        }
        catch (Exception ignored)
        {
            return maxFileSize;
        }
    }

    /**
     * 唯一键冲突（409）
     *
     * <p>⚠️ <b>这道兜底是必需的，因为「逻辑删除 + 业务键唯一索引」会绕过服务层的预检。</b>
     * 服务层的重复检查走 MyBatis-Plus（{@code @TableLogic} 自动排除已删行），所以
     * 「编码/ISBN 已被一条**已逻辑删除**的记录占用」这种情况预检**会判为可用**，
     * 真正撞上的是数据库的唯一索引 —— 不接住 {@code DuplicateKeyException}
     * 就会冒到兜底分支变成 500（实测踩到：删过的库位编码无法再用，且报「系统异常」）。
     *
     * <p>本处理器只保证**码正确**（409 而非 500）与**不泄露 SQL**；面向用户的友好文案
     * 仍由各服务的预检给出（那是能说清「哪个字段重了」的地方）。这里把冲突键名留在日志里，
     * 便于定位是哪张表的哪个索引。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public AjaxResult handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request)
    {
        log.warn("请求地址'{}',唯一键冲突：{}", request.getRequestURI(), rootMessage(e));
        return AjaxResult.error(HttpStatus.CONFLICT,
                "数据与既有记录冲突（唯一约束不通过），请检查是否与已删除或已存在的记录重复");
    }

    /** 取最内层异常的消息，避免把 MyBatis 的多行 SQL 详情回给前端 */
    private String rootMessage(Throwable e)
    {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause)
        {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    /**
     * 请求方式不支持（405）
     *
     * <p>⚠️ 这是**客户端错误**，信封码必须是 405 而不是 500 ——
     * {@code AjaxResult.error(msg)} 的默认码是 500，会把「用错方法」报成「服务端故障」，
     * 让调用方去排查后端。日志也降到 warn（根 CLAUDE.md §6.3：用户输入错误不该打 error）。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public AjaxResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        log.warn("请求地址'{}',不支持'{}'请求", request.getRequestURI(), e.getMethod());
        return AjaxResult.error(HttpStatus.BAD_METHOD, e.getMessage());
    }

    /**
     * 请求的资源不存在（404）
     *
     * <p>路径写错、或访问了不存在/未实现的端点时 Spring 抛此异常。**不处理会落到兜底分支变成 500**，
     * 让「地址写错了」看起来像「服务器挂了」（实测踩到：对只读资源发 POST 得到 500）。
     * 注意与 {@link #handleHttpRequestMethodNotSupported} 的分工：路径不存在 → 404，
     * 路径存在但方法不对 → 405。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public AjaxResult handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request)
    {
        log.warn("请求地址'{}'不存在", request.getRequestURI());
        return AjaxResult.error(HttpStatus.NOT_FOUND, "请求的接口不存在：" + request.getRequestURI());
    }

    /**
     * 缺少必需的请求参数（400）
     *
     * <p>与缺路径变量同类，都是**客户端错误**；不处理会被兜底成 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public AjaxResult handleMissingServletRequestParameterException(MissingServletRequestParameterException e,
            HttpServletRequest request)
    {
        log.warn("请求地址'{}',缺少必需的请求参数'{}'", request.getRequestURI(), e.getParameterName());
        return AjaxResult.error(HttpStatus.BAD_REQUEST,
                "缺少必需的请求参数[" + e.getParameterName() + "]");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量（400，客户端错误）
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        log.warn("请求路径'{}'中缺少必需的路径变量'{}'", request.getRequestURI(), e.getVariableName());
        return AjaxResult.error(HttpStatus.BAD_REQUEST,
                String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配（400，客户端错误）
     *
     * <p>⚠️ 信封码必须是 400：参数类型写错是调用方的问题，返 500 会把排查方向引到服务端
     * （实测踩到：`?pageSize=abc` 曾返 500）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.warn("请求参数类型不匹配'{}',参数[{}]", request.getRequestURI(), e.getName());
        return AjaxResult.error(HttpStatus.BAD_REQUEST,
                String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'",
                        e.getName(), e.getRequiredType().getName(), value));
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 自定义验证异常
     *
     * <p>参数校验失败属**用户输入错误**，按接口文档 §业务错误码 返回 **400**，
     * 不是 500（500 表示服务器内部错误，前端会当成故障处理）。
     * 日志用 warn —— 用户输入错误不该打 error，否则真正的故障会被淹没（后端 CLAUDE.md §六）。
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.warn("参数校验失败: {}", e.getMessage());
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return AjaxResult.error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 自定义验证异常（{@code @Valid @RequestBody}）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.warn("参数校验失败: {}", e.getMessage());
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError == null) ? "请求参数不合法" : fieldError.getDefaultMessage();
        return AjaxResult.error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 自定义验证异常（{@code @RequestParam} / {@code @PathVariable} 上的约束注解）。
     *
     * <p>Spring 6.1 起，方法参数上的 {@code @Min} / {@code @NotBlank} 等不再抛
     * {@code ConstraintViolationException}，改为 {@link HandlerMethodValidationException}。
     * 不接这个异常就会落到兜底的 500 分支上。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public AjaxResult handleHandlerMethodValidationException(HandlerMethodValidationException e)
    {
        log.warn("参数校验失败: {}", e.getMessage());
        String message = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("请求参数不合法");
        return AjaxResult.error(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
