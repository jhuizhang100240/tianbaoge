package com.tbgbook.bookstore.ai.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.core.PromptResolver;
import com.tbgbook.bookstore.ai.domain.AiPromptTemplate;
import com.tbgbook.bookstore.ai.domain.AiPromptVersion;
import com.tbgbook.bookstore.ai.domain.VersionPublishRequest;
import com.tbgbook.bookstore.ai.domain.VersionRollbackRequest;
import com.tbgbook.bookstore.ai.mapper.AiPromptTemplateMapper;
import com.tbgbook.bookstore.ai.mapper.AiPromptVersionMapper;
import com.tbgbook.bookstore.ai.service.IAiPromptVersionService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * AI 提示词版本 Service 实现（发布/灰度/回滚状态机，见 spec §5.5 与 Task 8 的灰度口径裁决）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AiPromptVersionServiceImpl extends ServiceImpl<AiPromptVersionMapper, AiPromptVersion>
        implements IAiPromptVersionService
{
    private static final Pattern VERSION_NO = Pattern.compile("^v(\\d+)$");

    private final AiPromptTemplateMapper aiPromptTemplateMapper;

    private final PromptResolver promptResolver;

    @Override
    public List<AiPromptVersion> selectAiPromptVersionList(AiPromptVersion query)
    {
        LambdaQueryWrapper<AiPromptVersion> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getTemplateId() != null, AiPromptVersion::getTemplateId, query.getTemplateId());
            wrapper.eq(query.getStatus() != null, AiPromptVersion::getStatus, query.getStatus());
            wrapper.eq(StringUtils.hasText(query.getVersionNo()), AiPromptVersion::getVersionNo, query.getVersionNo());
        }
        wrapper.orderByDesc(AiPromptVersion::getVersionId);
        return list(wrapper);
    }

    @Override
    public int addVersion(AiPromptVersion version)
    {
        if (version == null || version.getTemplateId() == null)
        {
            throw BizErrors.badRequest("模板ID不能为空");
        }
        AiPromptTemplate template = aiPromptTemplateMapper.selectById(version.getTemplateId());
        if (template == null)
        {
            throw BizErrors.notFound("模板", version.getTemplateId());
        }
        if (!StringUtils.hasText(version.getContent()))
        {
            throw BizErrors.badRequest("模板内容不能为空");
        }
        validateVariables(version.getContent(), version.getVariables());
        String versionNo = StringUtils.hasText(version.getVersionNo())
                ? version.getVersionNo() : nextVersionNo(version.getTemplateId());
        long exists = count(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, version.getTemplateId())
                .eq(AiPromptVersion::getVersionNo, versionNo));
        if (exists > 0)
        {
            throw BizErrors.conflict("版本号已存在：" + versionNo);
        }
        version.setVersionId(null);
        version.setVersionNo(versionNo);
        version.setStatus(1);
        version.setPublishBy("");
        version.setPublishTime(null);
        return save(version) ? 1 : 0;
    }

    @Override
    public int updateVersion(AiPromptVersion version)
    {
        if (version == null || version.getVersionId() == null)
        {
            throw BizErrors.badRequest("版本ID不能为空");
        }
        AiPromptVersion existing = getById(version.getVersionId());
        if (existing == null)
        {
            throw BizErrors.notFound("版本", version.getVersionId());
        }
        // 已发布/灰度/回滚版本不可编辑：只能改草稿，防止绕过 publish/rollback 治理
        if (!Integer.valueOf(1).equals(existing.getStatus()))
        {
            throw BizErrors.ruleViolated("只能编辑草稿版本（当前状态 " + existing.getStatus() + "）");
        }
        String content = StringUtils.hasText(version.getContent()) ? version.getContent() : existing.getContent();
        List<String> variables = version.getVariables() != null ? version.getVariables() : existing.getVariables();
        validateVariables(content, variables);
        version.setVersionNo(null);
        version.setTemplateId(null);
        version.setStatus(null);
        version.setAbSatisfaction(null);
        version.setPublishBy(null);
        version.setPublishTime(null);
        return updateById(version) ? 1 : 0;
    }

    @Override
    public int removeVersions(Long[] versionIds)
    {
        if (versionIds == null || versionIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的版本");
        }
        int count = 0;
        for (Long versionId : versionIds)
        {
            AiPromptVersion version = getById(versionId);
            if (version == null)
            {
                throw BizErrors.notFound("版本", versionId);
            }
            if (!Integer.valueOf(1).equals(version.getStatus()))
            {
                throw BizErrors.ruleViolated("只能删除草稿版本（当前状态 " + version.getStatus() + "）");
            }
            removeById(versionId);
            count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(Long versionId, VersionPublishRequest request)
    {
        AiPromptVersion version = getById(versionId);
        if (version == null)
        {
            throw BizErrors.notFound("版本", versionId);
        }
        boolean gray = request != null && request.getGrayType() != null;
        // 全量发布允许「灰度版本转全量」（2→3）；已全量版本重复发布 / 已发布版本再次灰度 → 409
        if ((gray && !Integer.valueOf(1).equals(version.getStatus()))
                || (!gray && Integer.valueOf(3).equals(version.getStatus())))
        {
            throw BizErrors.conflict("该版本已发布");
        }
        AiPromptTemplate template = aiPromptTemplateMapper.selectById(version.getTemplateId());
        if (template == null)
        {
            throw BizErrors.notFound("模板", version.getTemplateId());
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();

        // 同一模板同时只允许一个灰度版本
        baseMapper.update(null, new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, version.getTemplateId())
                .eq(AiPromptVersion::getStatus, 2)
                .ne(AiPromptVersion::getVersionId, versionId)
                .set(AiPromptVersion::getStatus, 1));

        AiPromptVersion versionUpdate = new AiPromptVersion();
        versionUpdate.setVersionId(versionId);
        versionUpdate.setStatus(gray ? 2 : 3);
        versionUpdate.setPublishBy(operator);
        versionUpdate.setPublishTime(now);
        updateById(versionUpdate);

        AiPromptTemplate templateUpdate = new AiPromptTemplate();
        templateUpdate.setTemplateId(template.getTemplateId());
        if (gray)
        {
            validateGray(request.getGrayType(), request.getGrayRatio(), request.getGrayValue());
            templateUpdate.setStatus(2);
            templateUpdate.setGrayType(request.getGrayType());
            templateUpdate.setGrayRatio(request.getGrayType() == 1 ? request.getGrayRatio() : 0);
            templateUpdate.setGrayValue(request.getGrayType() == 2 ? request.getGrayValue() : "");
        }
        else
        {
            templateUpdate.setStatus(3);
            templateUpdate.setCurrentVersion(version.getVersionNo());
            templateUpdate.setGrayType(1);
            templateUpdate.setGrayRatio(0);
            templateUpdate.setGrayValue("");
        }
        aiPromptTemplateMapper.updateById(templateUpdate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateId", template.getTemplateId());
        data.put("versionId", versionId);
        data.put("currentVersion", gray ? template.getCurrentVersion() : version.getVersionNo());
        data.put("status", gray ? 2 : 3);
        data.put("grayRatio", gray && request.getGrayType() == 1 ? request.getGrayRatio() : 0);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollback(Long versionId, VersionRollbackRequest request)
    {
        if (request == null || !StringUtils.hasText(request.getReason()))
        {
            throw BizErrors.badRequest("回滚原因不能为空");
        }
        AiPromptVersion version = getById(versionId);
        if (version == null)
        {
            throw BizErrors.notFound("版本", versionId);
        }
        AiPromptTemplate template = aiPromptTemplateMapper.selectById(version.getTemplateId());
        if (template == null)
        {
            throw BizErrors.notFound("模板", version.getTemplateId());
        }
        if (version.getVersionNo().equals(template.getCurrentVersion()))
        {
            throw BizErrors.ruleViolated("该版本已是当前生效版本");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = currentUser();

        // 原当前版本置「已回滚」
        AiPromptVersion previous = getOne(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, version.getTemplateId())
                .eq(AiPromptVersion::getVersionNo, template.getCurrentVersion())
                .last("limit 1"), false);
        if (previous != null)
        {
            AiPromptVersion previousUpdate = new AiPromptVersion();
            previousUpdate.setVersionId(previous.getVersionId());
            previousUpdate.setStatus(4);
            updateById(previousUpdate);
        }
        // 同样清掉可能残留的灰度版本指针
        baseMapper.update(null, new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getTemplateId, version.getTemplateId())
                .eq(AiPromptVersion::getStatus, 2)
                .set(AiPromptVersion::getStatus, 1));

        AiPromptVersion versionUpdate = new AiPromptVersion();
        versionUpdate.setVersionId(versionId);
        versionUpdate.setStatus(3);
        versionUpdate.setPublishBy(operator);
        versionUpdate.setPublishTime(now);
        updateById(versionUpdate);

        AiPromptTemplate templateUpdate = new AiPromptTemplate();
        templateUpdate.setTemplateId(template.getTemplateId());
        templateUpdate.setCurrentVersion(version.getVersionNo());
        templateUpdate.setStatus(3);
        templateUpdate.setGrayType(1);
        templateUpdate.setGrayRatio(0);
        templateUpdate.setGrayValue("");
        aiPromptTemplateMapper.updateById(templateUpdate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateId", template.getTemplateId());
        data.put("versionId", versionId);
        data.put("currentVersion", version.getVersionNo());
        data.put("status", 3);
        return data;
    }

    // ------------------------------------------------------------------

    private void validateVariables(String content, List<String> variablesJson)
    {
        Set<String> declared = variablesJson == null ? new HashSet<>() : new HashSet<>(variablesJson);
        Set<String> used = promptResolver.extractVariables(content);
        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(declared);
        if (!missing.isEmpty())
        {
            throw BizErrors.ruleViolated("以下模板变量未在 variables 中声明：" + String.join(", ", missing));
        }
    }

    private void validateGray(Integer grayType, Integer grayRatio, String grayValue)
    {
        if (grayType == null || (grayType != 1 && grayType != 2))
        {
            throw BizErrors.badRequest("灰度方式取值 1按比例 / 2按会员群");
        }
        if (grayType == 1 && (grayRatio == null || grayRatio < 1 || grayRatio > 100))
        {
            throw BizErrors.ruleViolated("灰度比例不在 1~100 范围");
        }
        if (grayType == 2 && !StringUtils.hasText(grayValue))
        {
            throw BizErrors.badRequest("按会员群灰度时，会员等级列表不能为空");
        }
    }

    private String nextVersionNo(Long templateId)
    {
        List<AiPromptVersion> versions = list(new LambdaQueryWrapper<AiPromptVersion>()
                .select(AiPromptVersion::getVersionNo)
                .eq(AiPromptVersion::getTemplateId, templateId));
        int max = 0;
        for (AiPromptVersion version : versions)
        {
            Matcher matcher = version.getVersionNo() == null ? null : VERSION_NO.matcher(version.getVersionNo());
            if (matcher != null && matcher.matches())
            {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return "v" + (max + 1);
    }

    private String currentUser()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return username == null ? "system" : username;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
