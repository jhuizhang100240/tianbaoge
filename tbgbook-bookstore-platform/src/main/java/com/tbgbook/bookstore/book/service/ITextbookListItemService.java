package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.TextbookListItem;

/**
 * 教材清单明细 服务层。
 *
 * @author tbgbook
 */
public interface ITextbookListItemService extends IService<TextbookListItem>
{
    /**
     * 查询教材清单明细列表。
     *
     * @param textbookListItem 查询条件
     * @return 教材清单明细集合
     */
    List<TextbookListItem> selectTextbookListItemList(TextbookListItem textbookListItem);

    /**
     * 查询教材清单明细详情。
     *
     * @param itemId 明细ID
     * @return 教材清单明细；不存在时抛 404
     */
    TextbookListItem selectTextbookListItemById(Long itemId);

    /**
     * 新增教材清单明细。
     *
     * <p>服务端接管：按 {@code bookId} 回填 {@code isbn}/{@code title}/{@code price}，
     * 计算 {@code remainQty} 与 {@code status}，{@code pickedQty} 不接受传入。
     * 同一清单内不允许出现同一本书（409）。落库后重算主表统计。
     *
     * @param textbookListItem 教材清单明细
     * @return 结果
     */
    int addTextbookListItem(TextbookListItem textbookListItem);

    /**
     * 修改教材清单明细。
     *
     * <p>与新增同样的服务端接管规则；{@code listId} 不允许改（要换清单请删了重建）。
     * 更新后重算主表统计。
     *
     * @param textbookListItem 教材清单明细
     * @return 结果
     */
    int updateTextbookListItem(TextbookListItem textbookListItem);

    /**
     * 删除教材清单明细。
     *
     * <p>删除后重算主表统计 —— 否则主表的品种数/预订数会停在旧值。
     *
     * @param itemIds 需要删除的明细ID数组
     * @return 结果
     */
    int removeTextbookListItems(Long[] itemIds);
}
