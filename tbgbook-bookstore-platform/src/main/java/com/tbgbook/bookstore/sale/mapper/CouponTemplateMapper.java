package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;

/**
 * 优惠券模板 Mapper 接口。
 *
 * @author tbgbook
 */
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate>
{
    /**
     * 查询优惠券模板列表。
     *
     * @param template 查询条件
     * @return 模板集合
     */
    List<CouponTemplate> selectCouponTemplateList(CouponTemplate template);

    /**
     * 取 {@code biz_coupon_template} 的下一个自增主键（用于预生成 templateNo）。
     *
     * <p>手法同 {@code StockInMapper#selectNextInId}：{@code template_no} 是
     * {@code NOT NULL} + 唯一索引，插入前必须先给出值。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextTemplateId();
}
