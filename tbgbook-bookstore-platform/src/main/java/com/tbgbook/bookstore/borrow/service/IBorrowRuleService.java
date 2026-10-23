package com.tbgbook.bookstore.borrow.service;

import java.util.List;

import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.LevelQuotaView;

/**
 * 借阅全局规则 Service 接口（阶段五步骤④，页面 A-57，FR-4.5.1）。
 *
 * <p>单行配置（一门店一条）；等级相关额度在 {@code biz_member_level}，
 * 由 {@link #levelQuota(Long)} 合并返回。
 *
 * @author tbgbook
 */
public interface IBorrowRuleService
{
    /**
     * 查询借阅规则列表。
     *
     * @param rule 查询条件
     * @return 规则集合
     */
    List<BorrowRule> selectBorrowRuleList(BorrowRule rule);

    /**
     * 查询借阅规则详情。
     *
     * @param ruleId 规则ID
     * @return 规则；不存在时抛 404
     */
    BorrowRule selectBorrowRuleById(Long ruleId);

    /**
     * 新增借阅规则（一门店仅一条，重复 409）。
     *
     * @param rule 规则
     * @return 影响行数
     */
    int addBorrowRule(BorrowRule rule);

    /**
     * 修改借阅规则（按 storeId 更新已有行，**不是插入新行**）。
     *
     * @param rule 规则
     * @return 影响行数
     */
    int updateBorrowRule(BorrowRule rule);

    /**
     * 删除借阅规则 —— **恒 422**：全局规则必须保留一行，改配置走 PUT。
     *
     * @param ruleIds 规则主键数组
     * @return 不会返回（抛 422）
     */
    int removeBorrowRules(Long[] ruleIds);

    /**
     * 等级借阅额度对照（等级额度 + 全局规则 + 相关 sys_config 键值）。
     *
     * @param levelId 限定等级（可空 = 全部等级）
     * @return 对照视图
     */
    LevelQuotaView levelQuota(Long levelId);

    /**
     * 取当前生效的全局规则（列表首行；缺失时抛 404 提示先配置）。
     *
     * @return 全局规则
     */
    BorrowRule requireActiveRule();
}
