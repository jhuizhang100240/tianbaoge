package com.tbgbook.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AliyunOSSOperator {
    @Autowired
    private AliyunOSSProperties ossProperties;

    public String upload(byte[] content, String originalFilename) throws Exception {
        String endpoint = ossProperties.getEndpoint();
        String bucketName = ossProperties.getBucketName();
        // 从环境变量中获取访问凭证。运行本代码示例之前，请确保已设置环境变量OSS_ACCESS_KEY_ID和OSS_ACCESS_KEY_SECRET。
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();

        // 填写Object完整路径，例如202406/1.png。Object完整路径中不能包含Bucket名称。
        // 获取当前系统日期的字符串,格式为 yyyy/MM
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // 根据原始文件名originalFilename, 生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID().toString() + originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dir + "/" + newFileName;

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, credentialsProvider);

        // 文件上传
        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(content));
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        return endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName;
    }

    /**
     * 按 {@link #upload} 返回的 URL 删除对象（知识库文档删除时清理存储残留）。
     *
     * <p>URL 形如 {@code https://<bucket>.<endpoint-host>/<objectName>}。
     * 对象不存在时 OSS 删除同样返回成功（幂等）；调用方对删除失败一般只记警告。
     *
     * @param fileUrl 上传接口返回的完整 URL；为空时直接返回
     */
    public void deleteByUrl(String fileUrl) throws Exception {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }
        URI uri = URI.create(fileUrl);
        String host = uri.getHost();
        if (host == null || uri.getPath() == null) {
            return;
        }
        String bucketName = host.split("\\.")[0];
        String objectName = uri.getPath().replaceFirst("^/", "");
        OSS ossClient = new OSSClientBuilder().build(ossProperties.getEndpoint(),
                CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider());
        try {
            ossClient.deleteObject(bucketName, objectName);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 按上传返回的 URL 下载对象（重建索引时回读原始文档）。
     *
     * @param fileUrl 上传接口返回的完整 URL
     * @return 对象字节
     */
    public byte[] download(String fileUrl) throws Exception {
        URI uri = URI.create(fileUrl);
        String host = uri.getHost();
        if (host == null || uri.getPath() == null) {
            throw new IllegalArgumentException("invalid oss url: " + fileUrl);
        }
        String bucketName = host.split("\\.")[0];
        String objectName = uri.getPath().replaceFirst("^/", "");
        OSS ossClient = new OSSClientBuilder().build(ossProperties.getEndpoint(),
                CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider());
        try (OSSObject object = ossClient.getObject(bucketName, objectName);
                InputStream in = object.getObjectContent()) {
            return in.readAllBytes();
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
