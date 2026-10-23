package com.tbgbook.bookstore.ai.domain;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

/**
 * 知识库上传请求（multipart/form-data 绑定）。
 *
 * <p>字段与接口契约一致：file/docName/docType 必填；chunkSize/chunkOverlap 可选（缺省取
 * {@code biz.ai.kb.chunk.size}/{@code overlap}）。
 *
 * @author tbgbook
 */
@Data
public class KbUploadRequest
{
    private MultipartFile file;

    private String docName;

    private Integer docType;

    private String version;

    private String effectiveScope;

    private Integer chunkSize;

    private Integer chunkOverlap;
}
