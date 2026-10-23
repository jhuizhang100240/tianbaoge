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

    /**
     * [回收验收] 由回收合格明细生成二手书副本（阶段六步骤②，登记 A45）。
     *
     * <p>自动生成副本条码（BC+日期+流水）与 SKU（UB+8 位），落库为
     * {@code stockStatus=4下架}（待上架），{@code costPrice=最终回收价}；
     * 会员后续走 {@code /shelf} 定价上架。
     *
     * @param item     回收明细（提供 isbn / title / 判定成色 / 基准价）
     * @param recycleId     来源回收单ID
     * @param recycleItemId 来源回收明细ID
     * @param costPrice     回收成本（最终回收价）
     * @return 生成的副本ID
     */
    Long createFromRecycle(com.tbgbook.bookstore.usedbook.domain.RecycleItem item,
            Long recycleId, Long recycleItemId, java.math.BigDecimal costPrice);
}
