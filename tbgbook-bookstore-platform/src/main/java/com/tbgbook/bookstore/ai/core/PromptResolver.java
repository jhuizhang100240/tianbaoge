package com.tbgbook.bookstore.ai.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.ai.domain.AiPromptTemplate;
import com.tbgbook.bookstore.ai.domain.AiPromptVersion;
import com.tbgbook.bookstore.ai.mapper.AiPromptTemplateMapper;
import com.tbgbook.bookstore.ai.mapper.AiPromptVersionMapper;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.service.IMemberService;

import lombok.RequiredArgsConstructor;

/**
 * 提示词解析器（AI 域设计稿 §5.5）：模板版本选择（含灰度）+ {@code {{变量}}} 渲染 + 内置兜底。
 *
 * <p>灰度口径：模板 {@code status=2} 时，命中灰度（按比例 hash 或按会员等级群）用
 * {@code status=2} 的版本，未命中使用 {@code current_version}（全量版本）；发布全量时
 * 指针才切到新版本。模板缺失/渲染失败一律回落到内置兜底模板，答案链路不中断。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class PromptResolver
{
    private static final Logger log = LoggerFactory.getLogger(PromptResolver.class);

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}");

    private static final Map<Integer, String> BUILTIN_TEMPLATES = builtinTemplates();

    private final AiPromptTemplateMapper templateMapper;

    private final AiPromptVersionMapper versionMapper;

    private final IMemberService memberService;

    /**
     * 解析场景的生效提示词（含灰度判定）。
     */
    public PromptResolution resolve(int scene, Long memberId)
    {
        AiPromptTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getScene, scene)
                .in(AiPromptTemplate::getStatus, 2, 3)
                .orderByDesc(AiPromptTemplate::getTemplateId)
                .last("limit 1"));
        if (template == null)
        {
            return builtin(scene);
        }
        AiPromptVersion version = null;
        if (Integer.valueOf(2).equals(template.getStatus()) && grayHit(template, memberId))
        {
            version = versionMapper.selectOne(new LambdaQueryWrapper<AiPromptVersion>()
                    .eq(AiPromptVersion::getTemplateId, template.getTemplateId())
                    .eq(AiPromptVersion::getStatus, 2)
                    .orderByDesc(AiPromptVersion::getVersionId)
                    .last("limit 1"));
        }
        if (version == null && StringUtils.hasText(template.getCurrentVersion()))
        {
            version = versionMapper.selectOne(new LambdaQueryWrapper<AiPromptVersion>()
                    .eq(AiPromptVersion::getTemplateId, template.getTemplateId())
                    .eq(AiPromptVersion::getVersionNo, template.getCurrentVersion())
                    .last("limit 1"));
        }
        if (version == null || !StringUtils.hasText(version.getContent()))
        {
            log.warn("prompt template {} has no usable version, fallback to builtin", template.getTemplateId());
            return builtin(scene);
        }
        return new PromptResolution(template.getTemplateId(), version.getVersionId(),
                version.getVersionNo(), version.getContent());
    }

    /**
     * 渲染 {@code {{变量}}}；未提供的占位符替换为空串（避免模型看到模板语法）。
     */
    public String render(String content, Map<String, Object> variables)
    {
        if (content == null)
        {
            return "";
        }
        String result = content;
        if (variables != null)
        {
            for (Map.Entry<String, Object> entry : variables.entrySet())
            {
                Object value = entry.getValue();
                result = result.replace("{{" + entry.getKey() + "}}",
                        value == null ? "" : String.valueOf(value));
            }
        }
        return VAR_PATTERN.matcher(result).replaceAll("");
    }

    /**
     * 提取模板中的变量名（保存/发布校验用）。
     */
    public Set<String> extractVariables(String content)
    {
        Set<String> variables = new TreeSet<>();
        if (!StringUtils.hasText(content))
        {
            return variables;
        }
        Matcher matcher = VAR_PATTERN.matcher(content);
        while (matcher.find())
        {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private boolean grayHit(AiPromptTemplate template, Long memberId)
    {
        if (memberId == null || memberId <= 0)
        {
            return false;
        }
        if (Integer.valueOf(2).equals(template.getGrayType()))
        {
            if (!StringUtils.hasText(template.getGrayValue()))
            {
                return false;
            }
            Member member = memberService.getById(memberId);
            if (member == null || member.getLevelId() == null)
            {
                return false;
            }
            String levelId = String.valueOf(member.getLevelId());
            return Arrays.stream(template.getGrayValue().split(","))
                    .map(String::trim).anyMatch(levelId::equals);
        }
        int ratio = template.getGrayRatio() == null ? 0 : template.getGrayRatio();
        return Math.floorMod(Long.hashCode(memberId), 100) < ratio;
    }

    private PromptResolution builtin(int scene)
    {
        String content = BUILTIN_TEMPLATES.getOrDefault(scene, BUILTIN_TEMPLATES.get(1));
        return new PromptResolution(null, null, "builtin", content);
    }

    private static Map<Integer, String> builtinTemplates()
    {
        Map<Integer, String> templates = new LinkedHashMap<>();
        templates.put(1, "参考资料：\n{{references}}\n\n历史对话：\n{{history}}\n\n"
                + "实时业务数据（含时间戳）：\n{{realtime_data}}\n\n会员问题：{{question}}\n"
                + "请仅依据参考资料回答；没有依据时明确说明无法回答，不要编造。");
        templates.put(2, "参考图书与推荐规则：\n{{references}}\n\n会员偏好：\n{{question}}\n"
                + "请给出推荐理由，避免编造库存与价格。");
        templates.put(3, "图书信息：\n{{book_info}}\n参考资料：\n{{references}}\n"
                + "生成类型：{{gen_type}}\n风格要求：{{style}}\n请生成对外文案，仅依据给定信息。");
        templates.put(4, "经营数据：\n{{metrics}}\n历史对话：\n{{history}}\n"
                + "问题：{{question}}\n请基于给定数据回答，不臆测数据。");
        templates.put(5, "经营统计：\n{{metrics}}\n选品规则：\n{{rules}}\n"
                + "请给出补货/淘汰建议并说明依据。");
        return templates;
    }
}
