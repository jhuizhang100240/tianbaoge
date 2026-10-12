package com.tbgbook.bookstore.ai.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.domain.KbUploadRequest;
import com.tbgbook.bookstore.ai.domain.KbUploadResult;

/**
 * 知识库文档 Service。
 *
 * @author tbgbook
 */
public interface IKbDocService extends IService<KbDoc>
{
    /**
     * 列表查询（PageHelper 分页由 Controller 的 startPage() 驱动）。
     */
    List<KbDoc> selectKbDocList(KbDoc query);

    /**
     * 上传：校验 → OSS 存储 → 同步解析+切片 → 落库（切片行 vectorStatus=0，待索引扫描）。
     */
    KbUploadResult upload(KbUploadRequest request);

    /**
     * 新增（管理端手工建档；不做解析）。
     */
    int addKbDoc(KbDoc kbDoc);

    /**
     * 修改（含停用/启用的 Qdrant 可见性同步）。
     */
    int updateKbDoc(KbDoc kbDoc);

    /**
     * 删除（先删 Qdrant 点，再逻辑删文档 + 物理删切片；索引中 409）。
     */
    int removeKbDocs(Long[] docIds);
}
