package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.RecommendConfig;
import com.tbgbook.bookstore.ai.domain.RecommendPin;

/**
 * 推荐位 Service（配置 / 置顶 / 预览与降级兜底）。
 *
 * @author tbgbook
 */
public interface IRecommendService extends IService<RecommendConfig>
{
    List<RecommendConfig> selectRecommendConfigList(RecommendConfig query);

    int addRecommendConfig(RecommendConfig config);

    int updateRecommendConfig(RecommendConfig config);

    int removeRecommendConfigs(Long[] configIds);

    List<RecommendPin> selectRecommendPinList(RecommendPin query);

    int addRecommendPin(RecommendPin pin);

    int updateRecommendPin(RecommendPin pin);

    int removeRecommendPins(Long[] pinIds);

    /**
     * 整体替换置顶列表（下架/停售图书整体拒绝；顺序重复 422）。
     */
    Map<String, Object> replacePins(Long configId, List<RecommendPin> pins);

    /**
     * 预览：算法/人工通道混合；AI 停用时按 fallback_strategy 兜底并置 fallbackUsed=true。
     */
    Map<String, Object> preview(Long configId);
}
