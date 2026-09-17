package com.tbgbook.framework.config;

import java.util.Date;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.tbgbook.common.utils.SecurityUtils;

/**
 * MyBatis-Plus 审计字段自动填充
 *
 * 覆盖数据库约定要求的审计五字段中的四个：
 * create_by / create_time / update_by / update_time
 * （remark 由业务自行填写）
 *
 * @author tbgbook
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler
{
    /** 无登录上下文时的兜底操作人 */
    private static final String FALLBACK_OPERATOR = "system";

    @Override
    public void insertFill(MetaObject metaObject)
    {
        Date now = new Date();
        String operator = currentOperator();
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
        this.strictInsertFill(metaObject, "updateTime", Date.class, now);
        this.strictInsertFill(metaObject, "createBy", String.class, operator);
        this.strictInsertFill(metaObject, "updateBy", String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject)
    {
        this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
        this.strictUpdateFill(metaObject, "updateBy", String.class, currentOperator());
    }

    /**
     * 取当前登录用户名；无登录上下文（定时任务、初始化、匿名调用）时回退为 "system"，不抛异常。
     */
    private String currentOperator()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return username != null ? username : FALLBACK_OPERATOR;
        }
        catch (Exception e)
        {
            return FALLBACK_OPERATOR;
        }
    }
}
