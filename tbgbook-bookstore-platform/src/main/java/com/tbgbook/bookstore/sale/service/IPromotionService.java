package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.Promotion;
import com.tbgbook.bookstore.sale.domain.PromotionStatusResult;

/**
 * 促销活动 Service 接口（页面 A-32~A-34，FR-4.3.7）。
 *
 * <p><b>状态只能由 {@link #changeStatus} 流转</b>：那里有「活动商品为空」与
 * 「时间区间冲突告警」两道校验，若增删改能直接写 {@code status} 它们就形同虚设
 * （契约把 {@code status} 列在 `skip` 里正是这个意思）。
 *
 * <p><b>可改 / 可删的状态被收窄</b>：进行中的活动正在影响售价，改动它等于在营业中途改价 ——
 * 必须先停用再改（见 {@link #updatePromotion} / {@link #removePromotions}）。
 * 这与「单据只有草稿可改」是同一条思路。
 *
 * @author tbgbook
 */
public interface IPromotionService extends IService<Promotion>
{
    /**
     * 查询促销活动列表。
     *
     * @param promotion 查询条件
     * @return 活动集合
     */
    List<Promotion> selectPromotionList(Promotion promotion);

    /**
     * 查询促销活动详情。
     *
     * @param promotionId 活动ID
     * @return 活动；不存在时抛 404
     */
    Promotion selectPromotionById(Long promotionId);

    /**
     * 新增促销活动（**状态恒为 1草稿**，{@code promotionNo} 服务端生成）。
     *
     * @param promotion 活动
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 活动名称或类型未填；
     *         422 指定范围的活动未给范围值
     */
    int addPromotion(Promotion promotion);

    /**
     * 修改促销活动。
     *
     * <p><b>仅 1草稿 / 4已停用 可改</b>：进行中的活动正在影响售价，改动它等于营业中途改价；
     * 已结束的是历史记录。要改先停用。
     *
     * @param promotion 活动
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 状态不允许修改
     */
    int updatePromotion(Promotion promotion);

    /**
     * 删除促销活动（**主从级联**：活动商品行随之物理删除）。
     *
     * <p><b>进行中的活动不可删</b>（先停用）；删除本身是**逻辑删除**（本表是基础数据表，
     * 有 {@code del_flag}），但活动商品关系行没有 {@code del_flag}、是物理删。
     *
     * @param promotionIds 活动主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含进行中的活动
     */
    int removePromotions(Long[] promotionIds);

    /**
     * 活动启停（状态流转）。
     *
     * <p><b>两道硬校验</b>：目标状态取值合法、流转合法（**已结束是终态**）；
     * 启用时（{@code status=2}）**活动商品不能为空** —— 一个没有商品的活动启用了也不会有任何效果。
     *
     * <p><b>冲突只告警不阻断</b>：与其它进行中活动的时间区间重叠写进
     * {@code warnings}（因为同一商品命中多个活动时按 {@code priority} 取一、不相加，
     * 重叠不构成错误）；启用时活动已过 {@code endTime} 同样只告警。
     *
     * @param promotionId 活动ID
     * @param status      目标状态（1草稿 2进行中 3已结束 4已停用）
     * @return 结果（含告警列表）
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；
     *         422 状态取值非法 / 流转非法 / 活动商品为空
     */
    PromotionStatusResult changeStatus(Long promotionId, Integer status);
}
