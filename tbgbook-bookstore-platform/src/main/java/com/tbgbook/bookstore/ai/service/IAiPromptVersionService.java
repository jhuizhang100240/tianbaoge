package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiPromptVersion;
import com.tbgbook.bookstore.ai.domain.VersionPublishRequest;
import com.tbgbook.bookstore.ai.domain.VersionRollbackRequest;

/**
 * AI 提示词版本 Service（含发布/灰度和回滚动作）。
 *
 * @author tbgbook
 */
public interface IAiPromptVersionService extends IService<AiPromptVersion>
{
    List<AiPromptVersion> selectAiPromptVersionList(AiPromptVersion query);

    int addVersion(AiPromptVersion version);

    int updateVersion(AiPromptVersion version);

    int removeVersions(Long[] versionIds);

    /**
     * 发布：不带 grayType 为全量（切 currentVersion 指针）；带 grayType 为灰度
     * （currentVersion 保持指向全量版本，命中灰度的请求才用该版本）。
     */
    Map<String, Object> publish(Long versionId, VersionPublishRequest request);

    /**
     * 回滚到指定历史版本；原当前版本置 4 已回滚。
     */
    Map<String, Object> rollback(Long versionId, VersionRollbackRequest request);
}
