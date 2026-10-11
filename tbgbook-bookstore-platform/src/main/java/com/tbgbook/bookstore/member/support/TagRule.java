package com.tbgbook.bookstore.member.support;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 会员规则标签的表达式解析器（阶段五步骤③b，**自定口径，登记 A38**）。
 *
 * <p><b>语法</b>：{@code 指标 运算符 数值}（忽略大小写与空格），如 {@code consume30d>=3}、
 * {@code consume30d_amount>=500}、{@code points>1000}、{@code register_days>=90}。
 *
 * <p><b>指标白名单</b>（固定集合，增加指标需改代码）：
 * <ul>
 *   <li>{@code consume30d} —— 近 30 天已完成且非红冲的订单数</li>
 *   <li>{@code consume30d_amount} —— 近 30 天已完成订单实付之和（含红冲负值，净额）</li>
 *   <li>{@code consume_total} —— 累计消费次数（会员冗余汇总）</li>
 *   <li>{@code consume_total_amount} —— 累计消费金额（会员冗余汇总）</li>
 *   <li>{@code points} —— 当前可用积分</li>
 *   <li>{@code growth} —— 成长值</li>
 *   <li>{@code register_days} —— 注册天数（按应用侧时间计算）</li>
 * </ul>
 * 运算符：{@code >=} {@code <=} {@code >} {@code <} {@code =} {@code !=}（{@code ==} 归一化为 {@code =}）。
 * 数值为非负数字（金额可带小数）。解析失败抛 422「规则表达式语法错误」。
 *
 * @author tbgbook
 */
public final class TagRule
{
    /** 指标白名单（key = 表达式里书写的指标名） */
    public enum Metric
    {
        CONSUME_30D("consume30d"),
        CONSUME_30D_AMOUNT("consume30d_amount"),
        CONSUME_TOTAL("consume_total"),
        CONSUME_TOTAL_AMOUNT("consume_total_amount"),
        POINTS("points"),
        GROWTH("growth"),
        REGISTER_DAYS("register_days");

        private final String key;

        Metric(String key)
        {
            this.key = key;
        }

        public String key()
        {
            return key;
        }

        public static Metric from(String key)
        {
            for (Metric metric : values())
            {
                if (metric.key.equals(key))
                {
                    return metric;
                }
            }
            return null;
        }

        public static String allKeys()
        {
            StringBuilder sb = new StringBuilder();
            for (Metric metric : values())
            {
                if (sb.length() > 0)
                {
                    sb.append(" / ");
                }
                sb.append(metric.key);
            }
            return sb.toString();
        }
    }

    /** 比较运算符 */
    public enum Op
    {
        GE(">="),
        LE("<="),
        GT(">"),
        LT("<"),
        EQ("="),
        NE("!=");

        private final String symbol;

        Op(String symbol)
        {
            this.symbol = symbol;
        }

        public static Op from(String raw)
        {
            String s = "==".equals(raw) ? "=" : raw;
            for (Op op : values())
            {
                if (op.symbol.equals(s))
                {
                    return op;
                }
            }
            return null;
        }

        /** 用 compareTo 的结果判定（actual 与 rule value 比较） */
        public boolean hit(int cmp)
        {
            switch (this)
            {
                case GE:
                    return cmp >= 0;
                case LE:
                    return cmp <= 0;
                case GT:
                    return cmp > 0;
                case LT:
                    return cmp < 0;
                case EQ:
                    return cmp == 0;
                case NE:
                    return cmp != 0;
                default:
                    return false;
            }
        }
    }

    private static final Pattern PATTERN = Pattern.compile(
            "^([a-zA-Z0-9_]+)\\s*(>=|<=|==|!=|>|<|=)\\s*(\\d+(?:\\.\\d+)?)$");

    private final Metric metric;

    private final Op op;

    private final BigDecimal value;

    private TagRule(Metric metric, Op op, BigDecimal value)
    {
        this.metric = metric;
        this.op = op;
        this.value = value;
    }

    /**
     * 解析规则表达式；语法错误抛 422。
     */
    public static TagRule parse(String expression)
    {
        if (expression == null || expression.isBlank())
        {
            throw BizErrors.ruleViolated("规则表达式语法错误：表达式不能为空");
        }
        Matcher matcher = PATTERN.matcher(expression.trim());
        if (!matcher.matches())
        {
            throw BizErrors.ruleViolated(
                    "规则表达式语法错误：应为「指标 运算符 数值」，如 consume30d>=3");
        }
        Metric metric = Metric.from(matcher.group(1).toLowerCase(Locale.ROOT));
        if (metric == null)
        {
            throw BizErrors.ruleViolated("规则表达式语法错误：不支持的指标「" + matcher.group(1)
                    + "」（支持 " + Metric.allKeys() + "）");
        }
        Op op = Op.from(matcher.group(2));
        if (op == null)
        {
            throw BizErrors.ruleViolated("规则表达式语法错误：不支持的运算符「" + matcher.group(2) + "」");
        }
        return new TagRule(metric, op, new BigDecimal(matcher.group(3)));
    }

    /** 指标值判定（actual 为 null 按 0 处理） */
    public boolean test(BigDecimal actual)
    {
        BigDecimal left = (actual == null) ? BigDecimal.ZERO : actual;
        return op.hit(left.compareTo(value));
    }

    /** 指标 key（供 XML {@code <choose>} 选择取数表达式） */
    public String metricKey()
    {
        return metric.key();
    }

    public String expression()
    {
        return metric.key() + op.symbol + value.toPlainString();
    }
}
