package com.tbgbook.bookstore.ai.domain;

import java.math.BigDecimal;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 配额配置（biz_ai_quota_config）。
 *
 * <p>status 是 char：0 正常 / 1 停用（与多数表约定相反，注意别搞反）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_ai_quota_config", autoResultMap = true)
public class AiQuotaConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private Long storeId;

    /** 1全局 2单会员 3按场景 */
    private Integer scope;

    private String scopeValue;

    private Integer hourlyLimit;

    private Integer dailyLimit;

    private BigDecimal monthBudget;

    private Integer globalDailyLimit;

    /** 按场景配模型（JSON 对象：chat/recommend/summary/analysis/selection → 模型名） */
    @TableField(value = "scene_model_map", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> sceneModelMap;

    private String overLimitStrategy;

    private String status;
}
