package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.TextbookReserve;

/** 教材预订 Mapper。 */
public interface TextbookReserveMapper extends BaseMapper<TextbookReserve>
{
    List<TextbookReserve> selectTextbookReserveList(TextbookReserve reserve);

    /** 取下一个自增主键（用于预生成 reserveNo；`reserve_no` 是 NOT NULL + 唯一索引）。 */
    Long selectNextReserveId();

    /**
     * 某清单明细上**仍然有效**的预订数量合计（未取消的都算）。
     *
     * <p>用于挡住「预订量超过清单明细的到货数」：预订时货可能还没到
     * （{@code arrived_qty=0}），故这里只在明细有到货数时才校验。
     *
     * @param listItemId 清单明细ID
     * @param excludeReserveId 排除的预订ID（修改时排除自身），新增传 {@code null}
     * @return 预订数量合计；无预订返回 0
     */
    int sumActiveQtyByListItem(@Param("listItemId") Long listItemId,
            @Param("excludeReserveId") Long excludeReserveId);
}
