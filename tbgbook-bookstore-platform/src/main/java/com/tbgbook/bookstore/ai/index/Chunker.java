package com.tbgbook.bookstore.ai.index;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 文本切片（AI 域设计稿 §5.1 钉死的规则）。
 *
 * <p>规则：字符窗口 + 句末标点回退；下一片以上一片结尾 {@code chunkOverlap} 个字符开头
 * （重叠计入片长，故每片 ≤ {@code chunkSize}）；末尾新增内容不足 overlap 时并入上一片
 * （去掉重复前缀后，该片 ≤ {@code chunkSize + chunkOverlap}，为片长唯一例外）。
 *
 * <p>纯函数：无状态、不碰数据库，便于单点验证。
 *
 * @author tbgbook
 */
@Component
public class Chunker
{
    /** 句末标点（含换行）：切片尽量在此回退，避免把句子劈开 */
    private static final String BOUNDARY_CHARS = "。！？；.!?;\n";

    public List<String> chunk(String text, int chunkSize, int chunkOverlap)
    {
        if (chunkSize <= chunkOverlap || chunkOverlap < 0)
        {
            throw BizErrors.ruleViolated("切片长度必须大于重叠长度");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty())
        {
            return new ArrayList<>();
        }
        if (normalized.length() <= chunkSize)
        {
            List<String> single = new ArrayList<>(1);
            single.add(normalized);
            return single;
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = normalized.length();
        while (start < length)
        {
            int end = Math.min(start + chunkSize, length);
            if (end < length)
            {
                int boundary = lastBoundary(normalized, start + 1, end);
                // 回退后仍需比重叠更长，否则下一片起点不前进（死循环）
                if (boundary > start && boundary - start > chunkOverlap)
                {
                    end = boundary;
                }
            }
            chunks.add(normalized.substring(start, end));
            if (end >= length)
            {
                break;
            }
            start = end - chunkOverlap;
        }

        // 末片新增内容不足 overlap → 并入上一片（去掉末片开头的重复前缀）
        if (chunks.size() > 1)
        {
            String last = chunks.get(chunks.size() - 1);
            if (last.length() < chunkOverlap * 2)
            {
                String previous = chunks.get(chunks.size() - 2);
                String remainder = last.length() > chunkOverlap ? last.substring(chunkOverlap) : "";
                chunks.set(chunks.size() - 2, previous + remainder);
                chunks.remove(chunks.size() - 1);
            }
        }
        return chunks;
    }

    private int lastBoundary(String text, int from, int to)
    {
        for (int i = to - 1; i >= from; i--)
        {
            if (BOUNDARY_CHARS.indexOf(text.charAt(i)) >= 0)
            {
                return i + 1;
            }
        }
        return -1;
    }
}
