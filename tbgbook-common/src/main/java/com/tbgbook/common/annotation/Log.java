package com.tbgbook.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.tbgbook.common.enums.BusinessType;
import com.tbgbook.common.enums.OperatorType;

/**
 * 自定义操作日志记录注解
 * 
 * @author ruoyi
 *
 */
@Target({ ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log
{
    /**
     * 模块
     */
    public String title() default "";

    /**
     * 功能
     */
    public BusinessType businessType() default BusinessType.OTHER;

    /**
     * 操作人类别
     */
    public OperatorType operatorType() default OperatorType.MANAGE;

    /**
     * 是否保存请求的参数
     */
    public boolean isSaveRequestData() default true;

    /**
     * 是否保存响应的参数
     */
    public boolean isSaveResponseData() default true;

    /**
     * 排除指定的请求参数
     */
    public String[] excludeParamNames() default {};

    /**
     * 是否为敏感操作（写入 sys_oper_log.is_sensitive）。
     *
     * <p>报损、退款、调价、权限变更、积分调整等关键操作置 true，日志保留 ≥180 天。
     */
    public boolean isSensitive() default false;

    /**
     * 业务类型（写入 sys_oper_log.biz_type），如 loss / refund / price / permission / point / coupon。
     */
    public String bizType() default "";
}
