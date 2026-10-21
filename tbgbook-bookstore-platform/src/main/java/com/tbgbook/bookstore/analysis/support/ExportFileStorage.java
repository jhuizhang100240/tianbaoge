package com.tbgbook.bookstore.analysis.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.oss.AliyunOSSOperator;

import lombok.RequiredArgsConstructor;

/**
 * 导出文件存储（本地 / OSS 双模式）。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class ExportFileStorage
{
    private static final Logger log = LoggerFactory.getLogger(ExportFileStorage.class);

    private static final String LOCAL_PREFIX = "local://export/";

    private final AliyunOSSOperator ossOperator;

    @Value("${tbgbook.analysis.export.storage-root:}")
    private String storageRoot;

    public String store(Path tempFile)
    {
        if (StringUtils.hasText(storageRoot))
        {
            return storeLocal(tempFile);
        }
        try
        {
            String fileName = "export-" + UUID.randomUUID() + ".xlsx";
            return ossOperator.upload(Files.readAllBytes(tempFile), fileName);
        }
        catch (Exception e)
        {
            log.error("export upload to oss failed", e);
            throw BizErrors.serverError("导出文件存储失败，请稍后重试");
        }
    }

    public Resource read(String fileUrl)
    {
        if (!StringUtils.hasText(fileUrl))
        {
            throw BizErrors.ruleViolated("导出文件尚未生成");
        }
        try
        {
            if (fileUrl.startsWith(LOCAL_PREFIX))
            {
                Path path = localPath(fileUrl);
                if (!Files.exists(path))
                {
                    throw BizErrors.gone("导出文件已过期或不存在，请重新导出");
                }
                return new FileSystemResource(path);
            }
            return new ByteArrayResource(ossOperator.download(fileUrl));
        }
        catch (com.tbgbook.common.exception.ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("read export file failed: {}", fileUrl, e);
            throw BizErrors.serverError("导出文件读取失败，请稍后重试");
        }
    }

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
            log.warn("delete export file failed: {}", fileUrl, e);
        }
    }

    private String storeLocal(Path tempFile)
    {
        Path root = storageRootPath();
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        Path target = root.resolve(dir).resolve(UUID.randomUUID() + ".xlsx").normalize();
        try
        {
            Files.createDirectories(target.getParent());
            Files.copy(tempFile, target);
            return LOCAL_PREFIX + dir + "/" + target.getFileName();
        }
        catch (IOException e)
        {
            log.error("export write local storage failed, root={}", storageRoot, e);
            throw BizErrors.serverError("导出文件存储失败，请稍后重试");
        }
    }

    private Path localPath(String fileUrl)
    {
        Path root = storageRootPath();
        Path target = root.resolve(fileUrl.substring(LOCAL_PREFIX.length())).normalize();
        if (!target.startsWith(root))
        {
            throw BizErrors.ruleViolated("非法的导出文件路径");
        }
        return target;
    }

    private Path storageRootPath()
    {
        if (!StringUtils.hasText(storageRoot))
        {
            throw BizErrors.ruleViolated("本地导出存储未配置");
        }
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }
}
