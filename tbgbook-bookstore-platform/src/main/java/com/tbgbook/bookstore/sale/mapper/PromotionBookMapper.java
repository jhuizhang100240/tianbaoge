package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.PromotionBook;

/**
 * 促销活动商品 Mapper 接口。
 *
 * @author tbgbook
 */
public interface PromotionBookMapper extends BaseMapper<PromotionBook>
{
    /**
     * 查询促销活动商品列表（含 JOIN {@code biz_book} 的 isbn / title 展示字段）。
     *
     * @param item 查询条件
     * @return 商品关系集合
     */
    List<PromotionBook> selectPromotionBookList(PromotionBook item);

    /**
     * 按活动主键删除其全部商品行（主从级联删除的「先删明细」一步）。
     *
     * <p>本表**没有 {@code del_flag}**，是物理删除 —— 关系行只有「在 / 不在」两种状态。
     *
     * @param promotionIds 活动主键数组
     * @return 删除行数
     */
    int deleteByPromotionIds(@Param("promotionIds") Long[] promotionIds);

    /**
     * 统计某活动下的商品行数（**启用前校验「活动商品为空」用**）。
     *
     * @param promotionId 活动ID
     * @return 商品行数
     */
    int countByPromotionId(@Param("promotionId") Long promotionId);
}
