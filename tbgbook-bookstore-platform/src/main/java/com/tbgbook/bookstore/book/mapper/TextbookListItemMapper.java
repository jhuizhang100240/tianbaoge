package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.TextbookList;
import com.tbgbook.bookstore.book.domain.TextbookListItem;

/**
 * 教材清单明细 Mapper 接口。
 *
 * @author tbgbook
 */
public interface TextbookListItemMapper extends BaseMapper<TextbookListItem>
{
    /**
     * 查询教材清单明细列表。
     *
     * @param textbookListItem 查询条件
     * @return 教材清单明细集合
     */
    List<TextbookListItem> selectTextbookListItemList(TextbookListItem textbookListItem);

    /**
     * 聚合一份清单的统计列，供回写主表用。
     *
     * <p>返回类型借用 {@link TextbookList} —— 四个聚合列的别名刻意与主表的属性名同名
     * （{@code bookKindCount} / {@code reserveQty} / {@code arrivedQty} / {@code pickedQty}），
     * MyBatis 按列标签直接映射，省一个只用一次的 DTO。
     *
     * @param listId 清单ID
     * @return 聚合结果；清单无明细时各列均为 0（SQL 里用 ifnull 兜底），不会返回 null
     */
    TextbookList selectStatistics(@Param("listId") Long listId);

    /**
     * 统计清单内**未关联到有效图书**的明细行数，供「发布」前置校验用。
     *
     * <p>判据是 {@code book_id} 在 {@code biz_book} 里查不到（含 {@code del_flag='2'} 的已删书）——
     * 接口文档：发布时校验每行教材均已建档。
     *
     * @param listId 清单ID
     * @return 未关联行数
     */
    Long countUnlinkedItems(@Param("listId") Long listId);

    /**
     * 统计同一清单内是否已有同一本书（用于禁止重复添加）。
     *
     * <p>表上**没有** {@code (list_id, book_id)} 唯一索引（只有两个普通索引），
     * 所以重复只能由服务端挡住；这里查一次再决定是否返回 409。
     *
     * @param listId      清单ID
     * @param bookId      图书ID
     * @param excludeItemId 排除的明细ID（修改时排除自身），新增传 {@code null}
     * @return 已存在的行数
     */
    Long countSameBook(@Param("listId") Long listId,
                       @Param("bookId") Long bookId,
                       @Param("excludeItemId") Long excludeItemId);

    /**
     * 按清单ID删除全部明细（主表级联删时用）。
     *
     * @param listId 清单ID
     * @return 删除行数
     */
    int deleteByListId(@Param("listId") Long listId);
}
