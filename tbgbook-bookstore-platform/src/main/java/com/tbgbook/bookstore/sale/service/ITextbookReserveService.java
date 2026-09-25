package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.PickupRequest;
import com.tbgbook.bookstore.sale.domain.PickupResult;
import com.tbgbook.bookstore.sale.domain.TextbookReserve;

/**
 * 教材预订 Service（A-30 教材核销，FR-4.3.10）。
 *
 * <p><b>预订即锁库存</b>：新增成功后 {@code qty_locked += qty}、{@code qty_available -= qty}，
 * 并写库存流水（{@code biz_type=12 预订锁定}）。**不收取定金**（§4.3 规则 7）——
 * 到店核销时按**实际取书数量**结算付款，多取补款、少取释放锁定库存。
 *
 * <p><b>分批取书</b>：{@link #pickup} 每次只释放本次数量对应的锁定，{@code lockedQty} 累加，
 * 累计达到 {@code qty} 时置 {@code status=4 已取完}；挂清单的预订同步回写清单明细的
 * {@code pickedQty} 与状态（那是清单「已取数」列的唯一维护点）。
 */
public interface ITextbookReserveService extends IService<TextbookReserve>
{
    List<TextbookReserve> selectTextbookReserveList(TextbookReserve reserve);

    TextbookReserve selectTextbookReserveById(Long reserveId);

    /**
     * 新增教材预订（**锁定库存**）。
     *
     * @param reserve 预订（须带 {@code bookId} 与 {@code qty}；{@code listId}/{@code listItemId} 可空）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException
     *         404 图书/清单/清单明细不存在；422 数量不合法 / 清单明细与图书不符 / 预订量超过到货数 /
     *         可售库存不足
     */
    int addTextbookReserve(TextbookReserve reserve);

    /**
     * 修改教材预订（**仅 1待到货 可改**；数量变更同步调整锁定库存）。
     *
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；422 状态不允许修改
     */
    int updateTextbookReserve(TextbookReserve reserve);

    /**
     * 删除教材预订（**仅 1待到货 可删**，主从无明细）。
     *
     * <p>⚠️ 删除会**释放已锁定的库存** —— 否则库存被锁死且无单据可追溯。
     */
    int removeTextbookReserves(Long[] reserveIds);

    /**
     * 取书核销（支持分批）。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         422 本次数量超过剩余 / 取货码不匹配；409 已取完或已取消
     */
    PickupResult pickup(Long reserveId, PickupRequest request);

    /**
     * 取消预订并**释放全部未取部分的锁定库存**。
     *
     * @throws com.tbgbook.common.exception.ServiceException 422 已部分取书（不可取消）；409 已取完或已取消
     */
    int cancel(Long reserveId, String reason);
}
