package com.tbgbook.bookstore.book.isbn;

import java.util.Optional;

import com.tbgbook.bookstore.book.domain.IsbnInfo;

/**
 * 外部书库适配器（**可插拔**，需求 §7.2 部署要点 1：接口先行）。
 *
 * <p>调用方只依赖本接口，不依赖任何供应商的响应形状。换源（如极速数据）时只加一个
 * {@code @Component} 实现并把配置里的 {@code isbn-lookup.provider} 改过去，调用方零改动。
 *
 * @author tbgbook
 */
public interface IsbnProvider
{
    /**
     * 供应商标识，写进响应体的 {@code source} 字段，也用于配置选择实现。
     *
     * @return 形如 {@code neodb}
     */
    String source();

    /**
     * 按 ISBN 查询书目。
     *
     * <p><b>约定</b>：
     * <ul>
     *   <li>未命中（库里没这本书、或返回的都是模糊匹配）→ {@link Optional#empty()}，
     *       <b>不是异常</b></li>
     *   <li>网络超时、非 2xx、响应解析失败 → <b>抛异常</b>。
     *       降级决策（记为「信息待补全」并放行）留给调用方 —— 供应商实现里猜不到
     *       业务上该怎么处理，也不该吞掉异常让问题不可观测</li>
     * </ul>
     *
     * @param isbn 已归一化的 ISBN（调用方保证格式合法）
     * @return 命中则返回书目信息
     */
    Optional<IsbnInfo> lookup(String isbn);
}
