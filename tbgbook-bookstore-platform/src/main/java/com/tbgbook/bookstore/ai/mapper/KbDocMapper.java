package com.tbgbook.bookstore.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.ai.domain.KbDoc;

/**
 * 知识库文档 Mapper（单表 CRUD 由 MP 提供，列表在 Service 用条件构造器）。
 *
 * @author tbgbook
 */
public interface KbDocMapper extends BaseMapper<KbDoc>
{
}
