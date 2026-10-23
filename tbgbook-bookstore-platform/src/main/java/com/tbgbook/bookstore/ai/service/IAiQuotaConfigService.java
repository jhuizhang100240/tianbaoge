package com.tbgbook.bookstore.ai.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiQuotaConfig;

/**
 * AI 配额配置 Service。
 *
 * @author tbgbook
 */
public interface IAiQuotaConfigService extends IService<AiQuotaConfig>
{
    List<AiQuotaConfig> selectAiQuotaConfigList(AiQuotaConfig query);

    int addQuotaConfig(AiQuotaConfig config);

    int updateQuotaConfig(AiQuotaConfig config);

    int removeQuotaConfigs(Long[] configIds);
}
