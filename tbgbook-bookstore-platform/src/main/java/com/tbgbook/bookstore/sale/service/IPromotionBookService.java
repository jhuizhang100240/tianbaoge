package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.PromotionBook;

/**
 * 促销活动商品 Service 接口（A-34 活动商品配置）。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属活动必须是「非进行中」</b>
 * （草稿 / 已结束 / 已停用）。进行中的活动正在影响售价，中途加减商品等于
 * 营业中途改价 —— 与 {@code IPromotionService} 的「非进行中才可改」同一条规则，
 * 且必须**在明细这一侧也拦一道**：只靠活动侧的校验挡不住「直接对商品行发 PUT」。
 *
 * @author tbgbook
 */
public interface IPromotionBookService extends IService<PromotionBook>
{
    /**
     * 查询促销活动商品列表（含 JOIN {@code biz_book} 的 isbn / title）。
     *
     * @param item 查询条件
     * @return 商品关系集合
     */
    List<PromotionBook> selectPromotionBookList(PromotionBook item);

    /**
     * 查询促销活动商品详情。
     *
     * @param relId 关系ID
     * @return 商品关系；不存在时抛 404
     */
    PromotionBook selectPromotionBookById(Long relId);

    /**
     * 新增促销活动商品。
     *
     * <p>{@code bookId} 在**新增**路径上必填（由 service 按操作判定）。
     *
     * @param item 商品关系
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 未指定图书；
     *         404 活动或图书不存在；409 该图书已在本活动内；
     *         422 活动进行中 / 图书已下架或停售
     */
    int addPromotionBook(PromotionBook item);

    /**
     * 修改促销活动商品（主键在请求体）。
     *
     * @param item 商品关系；{@code bookId} 可省略以沿用原值
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 409 该图书已在本活动内；
     *         422 活动进行中 / 图书已下架或停售
     */
    int updatePromotionBook(PromotionBook item);

    /**
     * 删除促销活动商品（**物理删除**，本表无 {@code del_flag}）。
     *
     * @param relIds 关系主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 活动进行中
     */
    int removePromotionBooks(Long[] relIds);
}
