package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.TextbookList;

/**
 * 教材清单 服务层。
 *
 * @author tbgbook
 */
public interface ITextbookListService extends IService<TextbookList>
{
    /**
     * 查询教材清单列表。
     *
     * @param textbookList 查询条件
     * @return 教材清单集合
     */
    List<TextbookList> selectTextbookList(TextbookList textbookList);

    /**
     * 查询教材清单详情。
     *
     * @param listId 清单ID
     * @return 教材清单；不存在时抛 404
     */
    TextbookList selectTextbookListById(Long listId);

    /**
     * 新增教材清单。
     *
     * <p>服务端接管：生成 {@code listNo}、把状态固定为草稿(1)、统计列清零、
     * 忽略客户端传入的 {@code publishTime}。见实现类注释。
     *
     * @param textbookList 教材清单
     * @return 结果
     */
    int addTextbookList(TextbookList textbookList);

    /**
     * 修改教材清单。
     *
     * <p>只允许改可编辑字段；{@code listNo} / 统计列 / {@code publishTime} 一律忽略。
     * 请求体里的 {@code status} **走与发布/结束相同的流转校验**（单向、不可跳级、不可回退），
     * 避免绕过发布的「清单非空 / 明细均已建档」前置校验。
     *
     * @param textbookList 教材清单
     * @return 结果
     */
    int updateTextbookList(TextbookList textbookList);

    /**
     * 删除教材清单。
     *
     * <p><b>只有草稿(1)可删</b>：清单一旦发布过（已发布/已结束），会员端可能已有预订、
     * 到货与取书数据也会喂给 FR-4.6.8 教材季分析，删掉即丢历史。明细随主表级联删除。
     *
     * @param listIds 需要删除的清单ID数组
     * @return 结果
     */
    int removeTextbookLists(Long[] listIds);

    /**
     * 发布教材清单（契约里已定义的动作端点，权限 {@code book:textbook:publish}）。
     *
     * <p>前置校验：清单非空、每行明细都已关联有效图书、当前必须是草稿。
     * 成功则状态置已发布(2)并写入 {@code publishTime}，此后会员端可见并接受预订。
     *
     * @param listId 清单ID
     * @return 结果
     */
    int publish(Long listId);

    /**
     * 结束教材清单（契约里已定义的动作端点，权限 {@code book:textbook:finish}）。
     *
     * <p>前置：当前必须是已发布(2)。成功则状态置已结束(3)、停止接受新预订；
     * {@code endTime} 为空时补当前时刻，已有值则保留（不覆盖运营填的计划结束时间）。
     *
     * @param listId 清单ID
     * @return 结果
     */
    int finish(Long listId);

    /**
     * 按明细重算主表的统计列（品种数 / 预订数 / 到货数 / 已取数）。
     *
     * <p>由 {@code TextbookListItemServiceImpl} 在每次明细增删改后调用 ——
     * 统计列由明细聚合，不接受客户端传入，所以唯一的维护点在这里。
     *
     * @param listId 清单ID
     */
    void refreshStatistics(Long listId);
}
