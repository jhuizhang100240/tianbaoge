package com.tbgbook.bookstore.common.support;

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 引用完整性校验工具。
 *
 * <p><b>为什么必须有这个类</b>：84 张业务表**零物理外键**（{@code docs/db/README.md} §6.1，
 * 2026-09-17 拍板）。误删父行不会被数据库拦截，因此引用完整性**一律在 service 层校验**，
 * 且要求**统一封装、不允许各模块各写一套**。这就是那套封装。
 *
 * <p>四类校验与对应方法：
 * <table border="1">
 *   <caption>校验分类</caption>
 *   <tr><th>类别</th><th>用途</th><th>方法</th></tr>
 *   <tr><td>① 引用存在性</td><td>写单据前校验父行存在且未软删</td>
 *       <td>{@link #requireExists} / {@link #requireExistsIfPresent}</td></tr>
 *   <tr><td>② 删除保护</td><td>删基础数据前查引用</td>
 *       <td>{@link #countReference} / {@link #requireNoReference}</td></tr>
 *   <tr><td>③ 主从级联</td><td>删单据头时明细随头同事务删除</td>
 *       <td>{@link #cascadeRemove}</td></tr>
 *   <tr><td>④ 状态与并发</td><td>状态机流转、乐观锁未命中</td>
 *       <td>{@link #requireState} / {@link #requireAffected}</td></tr>
 * </table>
 *
 * <p><b>删除校验是每个业务模块的必做项，不是可选项。</b>
 *
 * <p><b>SQL 安全性</b>：{@link #countReference} 的表名/列名来自代码常量而非外部输入，
 * 且额外做标识符白名单校验（{@link #IDENTIFIER}）；值一律走 {@code ?} 占位符绑定，
 * 全程不拼字符串，符合根 {@code CLAUDE.md} §6.3「SQL 参数化」。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class ReferenceGuard
{
    /** 允许的表名/列名形态：小写字母开头，仅含小写字母、数字、下划线。杜绝拼接注入。 */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final JdbcTemplate jdbcTemplate;

    // ==================================================================
    // ① 引用存在性
    // ==================================================================

    /**
     * 校验父行存在且未软删，返回该行。
     *
     * <p>实体带 {@code @TableLogic} 时，{@code selectById} 会自动过滤已删除行，
     * 因此「已软删」与「不存在」在这里都表现为 404 —— 这正是要的语义。
     *
     * @param mapper 父表的 Mapper
     * @param id     父行主键
     * @param label  父资源中文名，如「图书」
     * @param <T>    实体类型
     * @return 父行实体（供调用方继续取其字段，避免重复查询）
     * @throws com.tbgbook.common.exception.ServiceException 404
     */
    public <T> T requireExists(BaseMapper<T> mapper, Serializable id, String label)
    {
        if (id == null)
        {
            throw BizErrors.badRequest(label + "未指定");
        }
        T row = mapper.selectById(id);
        if (row == null)
        {
            throw BizErrors.notFound(label, id);
        }
        return row;
    }

    /**
     * 校验可选的父行引用：为空时返回 {@code null}，非空时必须存在。
     *
     * <p>用于可空外键场景（如订单的 {@code memberId}、明细的 {@code usedBookId}）。
     * <b>{@code 0} 按「未指定」处理</b> —— 本项目约定「散客」等场景用 0 而非 null
     * （接口文档 {@code /sales/cashier/checkout} 的 {@code memberId}：0=散客）。
     *
     * @param mapper 父表的 Mapper
     * @param id     父行主键，null 或 0 视为未指定
     * @param label  父资源中文名
     * @param <T>    实体类型
     * @return 父行实体；未指定时为 {@code null}
     * @throws com.tbgbook.common.exception.ServiceException 404
     */
    public <T> T requireExistsIfPresent(BaseMapper<T> mapper, Serializable id, String label)
    {
        if (id == null)
        {
            return null;
        }
        if (id instanceof Number && ((Number) id).longValue() == 0L)
        {
            return null;
        }
        return requireExists(mapper, id, label);
    }

    // ==================================================================
    // ② 删除保护
    // ==================================================================

    /**
     * 统计引用行数（删除保护的核心查询）。
     *
     * @param refTable         引用方表名，如 {@code biz_book}（代码常量，不接受外部输入）
     * @param refColumn        引用列名，如 {@code category_id}
     * @param refValue         被引用的主键值
     * @param softDeleteColumn 引用表的逻辑删除列名（本项目为 {@code del_flag}）；
     *                         **流水表没有该列，传 {@code null}**（7 张流水表清单见根 {@code CLAUDE.md} §6.2）
     * @return 引用行数；{@code refValue} 为 null 时返回 0
     */
    public long countReference(String refTable, String refColumn, Object refValue, String softDeleteColumn)
    {
        if (refValue == null)
        {
            return 0L;
        }
        String table = requireIdentifier(refTable, "表名");
        String column = requireIdentifier(refColumn, "列名");

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
                .append(" WHERE ").append(column).append(" = ?");
        if (softDeleteColumn != null)
        {
            sql.append(" AND ").append(requireIdentifier(softDeleteColumn, "逻辑删除列名")).append(" = '0'");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, refValue);
        return count == null ? 0L : count;
    }

    /**
     * 删除保护：被引用时抛 422，未被引用则静默通过。
     *
     * <p>需求点名的三处「被引用时禁止删除」是分类（A-09）、部门（A-80）、字典（A-81）；
     * 其余基础数据（出版社、成色档、库位、消息模板等）按同一模式处理。
     *
     * @param refTable         引用方表名
     * @param refColumn        引用列名
     * @param refValue         被引用的主键值
     * @param softDeleteColumn 引用表的逻辑删除列名，无则传 {@code null}
     * @param refLabel         引用方中文名，如「图书」
     * @param selfLabel        被删对象中文名，如「分类」
     * @param fallback         替代建议，如「改为停用」；无建议传 {@code null}
     * @throws com.tbgbook.common.exception.ServiceException 422
     */
    public void requireNoReference(String refTable, String refColumn, Object refValue,
            String softDeleteColumn, String refLabel, String selfLabel, String fallback)
    {
        long count = countReference(refTable, refColumn, refValue, softDeleteColumn);
        if (count > 0)
        {
            throw BizErrors.referenced(selfLabel, refLabel, count, fallback);
        }
    }

    // ==================================================================
    // ③ 主从级联
    // ==================================================================

    /**
     * 主从级联删除：**先删明细再删头**，且必须在同一事务内。
     *
     * <p>顺序不可颠倒 —— 先删头则明细变孤儿行，且随后按头主键删明细会因条件失效而漏删
     * （{@code docs/db/README.md} §6.1 第 3 类）。本项目**零外键**，数据库不会替你拦住这个错误。
     *
     * <p><b>调用方必须在 {@code @Transactional} 的 service 公开方法内调用</b>
     * （事务边界在 Service，见后端 {@code CLAUDE.md} §四）。本方法不做事务控制，
     * 只保证顺序与「头必须真的被删掉」。
     *
     * <pre>{@code
     * referenceGuard.cascadeRemove(applyId,
     *         id -> lossApplyItemMapper.delete(Wrappers.<BizLossApplyItem>lambdaQuery()
     *                 .eq(BizLossApplyItem::getApplyId, id)),
     *         id -> lossApplyMapper.deleteById(id));
     * }</pre>
     *
     * @param headerId       单据头主键
     * @param removeChildren 删除明细的动作，入参为 {@code headerId}
     * @param removeHeader   删除单据头的动作，入参为 {@code headerId}
     */
    public void cascadeRemove(Serializable headerId,
            Consumer<Serializable> removeChildren,
            Consumer<Serializable> removeHeader)
    {
        if (headerId == null)
        {
            throw BizErrors.badRequest("未指定要删除的单据");
        }
        removeChildren.accept(headerId);
        removeHeader.accept(headerId);
    }

    /**
     * 批量删除前校验主键集合非空。
     *
     * <p>RuoYi 的 {@code removeByIds(Collection)} 传空集合时不报错也不删任何行，
     * 调用方会误以为成功。这里显式拦一道。
     *
     * @param ids   主键集合
     * @param label 资源中文名
     * @return 去空后的集合
     * @throws com.tbgbook.common.exception.ServiceException 400
     */
    public Collection<? extends Serializable> requireIds(Collection<? extends Serializable> ids, String label)
    {
        if (ids == null || ids.isEmpty())
        {
            throw BizErrors.badRequest("未指定要删除的" + label);
        }
        return ids;
    }

    // ==================================================================
    // ④ 状态与并发
    // ==================================================================

    /**
     * 状态机流转合法性。
     *
     * @param valid   条件是否成立
     * @param message 不成立时的提示，建议用
     *                {@link BizErrors#illegalTransition(String, String, String)} 生成
     * @throws com.tbgbook.common.exception.ServiceException 422
     */
    public void requireState(boolean valid, String message)
    {
        if (!valid)
        {
            throw BizErrors.ruleViolated(message);
        }
    }

    /**
     * 校验当前状态等于期望状态。
     *
     * @param actual       当前状态值（可为 null）
     * @param expected     期望状态值
     * @param label        单据中文名，如「采购入库单」
     * @param expectedName 期望状态中文名，如「草稿」
     * @throws com.tbgbook.common.exception.ServiceException 422
     */
    public void requireState(Object actual, Object expected, String label, String expectedName)
    {
        if (actual == null || !actual.equals(expected))
        {
            throw BizErrors.ruleViolated(
                    label + "当前状态不允许此操作，需先处于「" + expectedName + "」");
        }
    }

    /**
     * 校验数据库写操作真的命中了行。
     *
     * <p><b>这是最容易被漏掉的一道校验。</b>带 {@code @Version} 的行被他人改过后，
     * {@code updateById} 会返回 0 而不抛异常 —— 不检查就会静默覆盖他人改动
     * （后端 {@code CLAUDE.md} §五 明确点名）。
     *
     * @param affected 影响行数（{@code updateById} 的返回值）
     * @param label    资源中文名
     * @throws com.tbgbook.common.exception.ServiceException 409
     */
    public void requireAffected(int affected, String label)
    {
        if (affected <= 0)
        {
            throw BizErrors.concurrentModified(label);
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 标识符白名单校验：不通过直接抛 500（属编码错误，不是用户错误） */
    private String requireIdentifier(String name, String what)
    {
        if (name == null || !IDENTIFIER.matcher(name).matches())
        {
            // 表名/列名来自代码，走到这里说明写错了常量，不应归为业务错误
            throw BizErrors.serverError("非法的" + what + "：" + name);
        }
        return name;
    }
}
