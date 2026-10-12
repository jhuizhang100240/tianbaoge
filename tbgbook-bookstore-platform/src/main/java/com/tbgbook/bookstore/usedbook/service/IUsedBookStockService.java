package com.tbgbook.bookstore.usedbook.service;

import java.util.List;

import com.tbgbook.bookstore.usedbook.domain.OffShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.ShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.ShelfResult;
import com.tbgbook.bookstore.usedbook.domain.UsedBook;

/**
 * 二手书库存 Service 接口（阶段六第一批，页面 A-42，FR-4.2.3）。
 *
 * <p>二手书按册管理、独立库存池与独立 SKU，禁止与正价书库存混算。
 * 本资源的行由回收验收自动生成（第二批），{@code POST} 仅用于存量手工建档补录。
 *
 * @author tbgbook
 */
public interface IUsedBookStockService
{
    /**
     * 查询二手书库存列表。
     *
     * @param usedBook 查询条件
     * @return 二手书集合
     */
    List<UsedBook> selectUsedBookList(UsedBook usedBook);

    /**
     * 查询二手书库存详情。
     *
     * @param usedBookId 副本ID
     * @return 二手书；不存在时抛 404
     */
    UsedBook selectUsedBookById(Long usedBookId);

    /**
     * 手工补录二手书副本（自动生成副本条码与 SKU，落库为「下架待上架」）。
     *
     * @param usedBook 二手书
     * @return 影响行数
     */
    int addUsedBook(UsedBook usedBook);

    /**
     * 修改二手书（库存状态 / 上架时间等由动作端点维护，不接受直接写入）。
     *
     * @param usedBook 二手书
     * @return 影响行数
     */
    int updateUsedBook(UsedBook usedBook);

    /**
     * 删除二手书副本（**仅未上架且未被订单引用**的可删；锁定 / 已售一律 422）。
     *
     * @param usedBookIds 副本主键数组
     * @return 影响行数
     */
    int removeUsedBooks(Long[] usedBookIds);

    /**
     * 上架定价（校验售价不高于原价；置 1在售 + 上架时间）。
     *
     * @param usedBookId 副本ID
     * @param request    上架请求
     * @return 上架结果
     */
    ShelfResult shelf(Long usedBookId, ShelfRequest request);

    /**
     * 下架（置 4下架；已售出不可下架）。
     *
     * @param usedBookId 副本ID
     * @param request    下架请求（原因必填）
     * @return 影响行数
     */
    int offShelf(Long usedBookId, OffShelfRequest request);
}
