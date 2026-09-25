package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.CouponGrantBatch;

/**
 * 优惠券发放批次 Mapper 接口。
 *
 * @author tbgbook
 */
public interface CouponGrantBatchMapper extends BaseMapper<CouponGrantBatch>
{
    /**
     * 查询优惠券发放批次列表。
     *
     * @param batch 查询条件
     * @return 批次集合
     */
    List<CouponGrantBatch> selectCouponGrantBatchList(CouponGrantBatch batch);

    /**
     * 取 {@code biz_coupon_grant_batch} 的下一个自增主键（用于预生成 batchNo）。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextBatchId();

    /**
     * 按**标签**圈选会员（{@code targetType=1}）。
     *
     * <p>⚠️ <b>跨域读</b>：{@code biz_member_tag_rel} 属会员域（阶段五）。
     * 这里只读不写 —— 发放批次本就要按人群圈选，而会员域的实现要等到阶段五，
     * 表与种子数据都已就绪，故直接读表（与库存预警扫描读 {@code biz_stock_flow} 同一处置）。
     *
     * @param tagIds 标签ID数组
     * @return 会员ID集合（去重）
     */
    List<Long> selectMemberIdsByTagIds(@Param("tagIds") List<Long> tagIds);

    /**
     * 按**手工名单**校验会员（{@code targetType=3}）。
     *
     * <p>⚠️ <b>必须校验存在性</b>：名单是人工填的，写错一个 ID 不该「照样发一张券给不存在的会员」——
     * 那会造出一条 member_id 指向空号的券，收银时谁也解释不清。
     * 本方法把名单过滤成「真实存在且状态正常」的会员；过滤后为空即触发 422 目标人群为空。
     * （**本轮实测踩到**：未校验时给 {@code targetValue="99999999"} 执行发放返回 200，
     * 真的插了一条 member_id=99999999 的券。）
     *
     * @param memberIds 名单里的会员ID
     * @return 真实存在且 {@code status=1正常} 的会员ID
     */
    List<Long> selectExistingMemberIds(@Param("memberIds") List<Long> memberIds);

    /**
     * 按**等级**圈选会员（{@code targetType=2}）。
     *
     * <p>只取 {@code status=1正常} 的会员：冻结(2) / 黑名单(3) / 已注销(4) 不该收到券。
     *
     * @param levelIds 等级ID数组
     * @return 会员ID集合
     */
    List<Long> selectMemberIdsByLevelIds(@Param("levelIds") List<Long> levelIds);
}
