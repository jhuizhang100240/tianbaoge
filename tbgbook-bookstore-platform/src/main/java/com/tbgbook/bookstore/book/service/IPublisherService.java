package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.Publisher;

/**
 * 出版社 Service 接口。
 *
 * <p>⚠️ {@code IService} 的包路径是 {@code com.baomidou.mybatisplus.spring.service}，
 * **不是**老教程里的 {@code com.baomidou.mybatisplus.extension.service}
 * —— MP 3.5.9+ 已把它拆到 {@code mybatis-plus-spring} artifact（后端 CLAUDE.md §五）。
 *
 * @author tbgbook
 */
public interface IPublisherService extends IService<Publisher>
{
    /**
     * 查询出版社列表（含该社图书品种数）。
     *
     * @param publisher 查询条件
     * @return 出版社集合
     */
    List<Publisher> selectPublisherList(Publisher publisher);

    /**
     * 批量删除出版社（含**删除保护**）。
     *
     * <p>被 {@code biz_book.publisher_id} 引用的出版社禁止删除
     * （接口文档「出版社」业务约束；需求 §4.1 规则 4 的同型规则）。
     *
     * @param publisherIds 出版社主键数组
     * @return 实际删除行数
     */
    int removePublishers(Long[] publisherIds);
}
