package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;

/**
 * 优惠券模板 Service 接口（页面 A-37，FR-4.3.8）。
 *
 * @author tbgbook
 */
public interface ICouponTemplateService extends IService<CouponTemplate>
{
    /**
     * 查询优惠券模板列表。
     *
     * @param template 查询条件
     * @return 模板集合
     */
    List<CouponTemplate> selectCouponTemplateList(CouponTemplate template);

    /**
     * 查询优惠券模板详情。
     *
     * @param templateId 模板ID
     * @return 模板；不存在时抛 404
     */
    CouponTemplate selectCouponTemplateById(Long templateId);

    /**
     * 新增优惠券模板（状态恒为 1草稿，{@code templateNo} 服务端生成，统计四列归零）。
     *
     * @param template 模板
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 券名称或类型未填；
     *         422 门槛小于面额 / 有效期参数不全 / 指定范围却未给范围值
     */
    int addCouponTemplate(CouponTemplate template);

    /**
     * 修改优惠券模板（编号、状态与统计四列都不接受写入）。
     *
     * @param template 模板
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；422 校验不通过
     */
    int updateCouponTemplate(CouponTemplate template);

    /**
     * 删除优惠券模板（**逻辑删除**；已被券实例引用时禁止删除）。
     *
     * <p>与分类/出版社同一条规则：被引用时只能**停用**（{@code status=3}），不能删 ——
     * 删了会让已发出的券失去来源说明。
     *
     * @param templateIds 模板主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 已被券实例引用
     */
    int removeCouponTemplates(Long[] templateIds);
}
