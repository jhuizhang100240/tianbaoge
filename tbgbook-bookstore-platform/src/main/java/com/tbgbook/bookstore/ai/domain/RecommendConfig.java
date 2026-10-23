package com.tbgbook.bookstore.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 推荐位配置（biz_recommend_config）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recommend_config")
public class RecommendConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private Long storeId;

    private String positionCode;

    private String positionName;

    /** 1个性化算法 2人工指定 3混合 */
    private Integer strategy;

    private Integer algoWeight;

    private Integer manualWeight;

    private String poolScope;

    /** 1热销榜 2新书上架 3关闭推荐位 */
    private Integer fallbackStrategy;

    private Integer displayQty;

    private String updateFrequency;

    /** char：0正常 / 1停用 */
    private String status;
}
