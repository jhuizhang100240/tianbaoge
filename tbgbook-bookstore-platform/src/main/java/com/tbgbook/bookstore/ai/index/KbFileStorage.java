package com.tbgbook.bookstore.ai.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.ai.config.AiProperties;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;
import com.tbgbook.oss.AliyunOSSOperator;

import lombok.RequiredArgsConstructor;

/**
 * 知识库原始文档存储（双模式）。
 *
 * <p>生产/测试走 OSS（{@link AliyunOSSOperator}，凭据走环境变量）；
 * 开发环境可在 {@code tbgbook.ai.kb.storage-root} 配置本地目录（本机 OSS 凭据常为空，
 * 落本地避免把环境凭据问题变成功能阻塞）。URL 前缀 {@code local://kb/} 用于区分。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class KbFileStorage
{
    private static final Logger log = LoggerFactory.getLogger(KbFileStorage.class);

    private static final String LOCAL_PREFIX = "local://kb/";

    private final AiProperties properties;

    private final AliyunOSSOperator ossOperator;

    /**
     * 存储原始文档，返回可回读的 URL。
     */
    public String store(byte[] content, String originalFilename)
    {
        String root = properties.getKb().getStorageRoot();
        if (StringUtils.hasText(root))
        {
            return storeLocal(root, content, originalFilename);
        }
        try
        {
            return ossOperator.upload(content, originalFilename);
        }
        catch (Exception e)
        {
            log.error("kb upload to oss failed", e);
            throw BizErrors.serverError("文档存储失败，请稍后重试");
        }
    }

    /**
     * 删除原始文档（尽力而为：失败只记 warn，不阻断业务删除）。
     */
    public void delete(String fileUrl)
    {
        if (!StringUtils.hasText(fileUrl))
        {
            return;
        }
        try
        {
            if (fileUrl.startsWith(LOCAL_PREFIX))
            {
                Files.deleteIfExists(localPath(fileUrl));
            }
            else
            {
                ossOperator.deleteByUrl(fileUrl);
            }
        }
        catch (Exception e)
        {
            log.warn("delete kb file failed: {}", fileUrl, e);
        }
    }

    /**
     * 回读原始文档（重建索引用）。
     */
    public byte[] read(String fileUrl)
    {
        if (!StringUtils.hasText(fileUrl))
        {
            throw BizErrors.ruleViolated("原始文件地址缺失，无法重建索引");
        }
        try
        {
            if (fileUrl.startsWith(LOCAL_PREFIX))
            {
                return Files.readAllBytes(localPath(fileUrl));
            }
            return ossOperator.download(fileUrl);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("read kb file failed: {}", fileUrl, e);
            throw BizErrors.serverError("原始文件读取失败，无法重建索引");
        }
    }

    /**
     * 解析本地存储 URL 对应的真实路径，并强制约束在 storage-root 之内（防路径穿越）。
     */
    private Path localPath(String fileUrl)
    {
        String root = properties.getKb().getStorageRoot();
        if (!StringUtils.hasText(root))
        {
            throw BizErrors.ruleViolated("本地文档存储未配置，无法访问原始文件");
        }
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        Path target = rootPath.resolve(fileUrl.substring(LOCAL_PREFIX.length())).normalize();
        if (!target.startsWith(rootPath))
        {
            throw BizErrors.ruleViolated("非法的文档存储路径");
        }
        return target;
    }

    private String storeLocal(String root, byte[] content, String originalFilename)
    {
        String extension = extension(originalFilename);
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = Paths.get(root, dir, fileName);
        try
        {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        }
        catch (IOException e)
        {
            log.error("kb upload to local storage failed, root={}", root, e);
            throw BizErrors.serverError("文档存储失败，请稍后重试");
        }
        return LOCAL_PREFIX + dir + "/" + fileName;
    }

    private String extension(String filename)
    {
        if (filename == null)
        {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase();
    }
}
