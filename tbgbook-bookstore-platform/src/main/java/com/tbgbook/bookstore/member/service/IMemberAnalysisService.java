package com.tbgbook.bookstore.member.service;

import com.tbgbook.bookstore.member.domain.aggregate.MemberPanoramaView;
import com.tbgbook.bookstore.member.domain.aggregate.MemberPortraitView;
import com.tbgbook.bookstore.member.domain.aggregate.TimelinePage;

/**
 * 会员全景 / 时间轴 / 画像 Service 接口（阶段五步骤③c-3，页面 A-45，FR-4.4.8 / FR-4.4.9）。
 *
 * <p>三块内容：① 全景聚合（一次请求拿齐档案 / 等级 / 储值 / 积分 / 券 / 标签 / 统计）；
 * ② 统一时间轴（订单 + 借阅 + 积分 + 储值，倒序分页）；③ 画像（频次 / 品类偏好 /
 * 客单价分布 / 活跃时段 / RFM）。成本口径指标受 `member:cost:view` 保护。
 *
 * <p>指标口径见实现类（登记 A41）。
 *
 * @author tbgbook
 */
public interface IMemberAnalysisService
{
    /**
     * 会员全景详情。
     *
     * @param memberId 会员ID（不存在时抛 404）
     * @return 全景视图
     */
    MemberPanoramaView panorama(Long memberId);

    /**
     * 会员统一时间轴（倒序分页）。
     *
     * @param memberId 会员ID
     * @param types    类型过滤（逗号分隔 order/borrow/point/stored；空 = 全部）
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数（1~100）
     * @return 分页结果
     */
    TimelinePage timeline(Long memberId, String types, int pageNum, int pageSize);

    /**
     * 会员画像（近 12 个月窗口；RFM 为同店样本内 1~5 分位）。
     *
     * @param memberId 会员ID
     * @return 画像视图
     */
    MemberPortraitView portrait(Long memberId);
}
