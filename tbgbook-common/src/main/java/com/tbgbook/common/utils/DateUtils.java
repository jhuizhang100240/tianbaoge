package com.tbgbook.common.utils;

import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.apache.commons.lang3.time.DateFormatUtils;

/**
 * 时间工具类
 * 
 * @author ruoyi
 */
@SuppressWarnings("deprecation")
public class DateUtils extends org.apache.commons.lang3.time.DateUtils
{
    public static String YYYY = "yyyy";

    public static String YYYY_MM = "yyyy-MM";

    public static String YYYY_MM_DD = "yyyy-MM-dd";

    public static String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    public static String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    private static String[] parsePatterns = {
            "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM", 
            "yyyy/MM/dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM",
            "yyyy.MM.dd", "yyyy.MM.dd HH:mm:ss", "yyyy.MM.dd HH:mm", "yyyy.MM"};

    /**
     * 获取当前Date型日期
     * 
     * @return Date() 当前日期
     */
    public static Date getNowDate()
    {
        return new Date();
    }

    /**
     * 获取当前日期, 默认格式为yyyy-MM-dd
     * 
     * @return String
     */
    public static String getDate()
    {
        return dateTimeNow(YYYY_MM_DD);
    }

    public static final String getTime()
    {
        return dateTimeNow(YYYY_MM_DD_HH_MM_SS);
    }

    public static final String dateTimeNow()
    {
        return dateTimeNow(YYYYMMDDHHMMSS);
    }

    public static final String dateTimeNow(final String format)
    {
        return parseDateToStr(format, new Date());
    }

    public static final String dateTime(final Date date)
    {
        return parseDateToStr(YYYY_MM_DD, date);
    }

    public static final String parseDateToStr(final String format, final Date date)
    {
        return new SimpleDateFormat(format).format(date);
    }

    public static final Date dateTime(final String format, final String ts)
    {
        try
        {
            return new SimpleDateFormat(format).parse(ts);
        }
        catch (ParseException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 日期路径 即年/月/日 如2018/08/08
     */
    public static final String datePath()
    {
        Date now = new Date();
        return DateFormatUtils.format(now, "yyyy/MM/dd");
    }

    /**
     * 日期路径 即年/月/日 如20180808
     */
    public static final String dateTime()
    {
        Date now = new Date();
        return DateFormatUtils.format(now, "yyyyMMdd");
    }

    /**
     * 日期型字符串转化为日期 格式
     */
    public static Date parseDate(Object str)
    {
        if (str == null)
        {
            return null;
        }
        try
        {
            return parseDate(str.toString(), parsePatterns);
        }
        catch (ParseException e)
        {
            return null;
        }
    }

    /**
     * 获取服务器启动时间
     */
    public static Date getServerStartDate()
    {
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        return new Date(time);
    }

    /**
     * 计算相差天数
     */
    public static int differentDaysByMillisecond(Date date1, Date date2)
    {
        return Math.abs((int) ((date2.getTime() - date1.getTime()) / (1000 * 3600 * 24)));
    }

    /**
     * 计算时间差
     *
     * @param endDate 最后时间
     * @param startTime 开始时间
     * @return 时间差（天/小时/分钟）
     */
    public static String timeDistance(Date endDate, Date startTime)
    {
        long nd = 1000 * 24 * 60 * 60;
        long nh = 1000 * 60 * 60;
        long nm = 1000 * 60;
        // long ns = 1000;
        // 获得两个时间的毫秒时间差异
        long diff = endDate.getTime() - startTime.getTime();
        // 计算差多少天
        long day = diff / nd;
        // 计算差多少小时
        long hour = diff % nd / nh;
        // 计算差多少分钟
        long min = diff % nd % nh / nm;
        // 计算差多少秒//输出结果
        // long sec = diff % nd % nh % nm / ns;
        return day + "天" + hour + "小时" + min + "分钟";
    }

    /**
     * 增加 LocalDateTime ==> Date
     */
    public static Date toDate(LocalDateTime temporalAccessor)
    {
        ZonedDateTime zdt = temporalAccessor.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    /**
     * 增加 LocalDate ==> Date
     */
    public static Date toDate(LocalDate temporalAccessor)
    {
        LocalDateTime localDateTime = LocalDateTime.of(temporalAccessor, LocalTime.of(0, 0, 0));
        ZonedDateTime zdt = localDateTime.atZone(ZoneId.systemDefault());
        return Date.from(zdt.toInstant());
    }

    // ==================== LocalDateTime / LocalDate（2026-09-22 新增） ====================
    // 项目自 2026-09-22 起实体统一用 java.time 类型，以下为其配套工具。
    // 上方 java.util.Date 的 API 全部保留 —— Quartz 的 CronExpression、POI、commons-lang3
    // 仍只接受 java.util.Date，删掉会连带改坏平台侧与第三方交互的边界。

    /**
     * 获取当前 LocalDateTime
     */
    public static LocalDateTime getNowLocalDateTime()
    {
        return LocalDateTime.now();
    }

    /**
     * 获取当前 LocalDate
     */
    public static LocalDate getNowLocalDate()
    {
        return LocalDate.now();
    }

    /**
     * LocalDateTime 格式化。与 {@link #parseDateToStr(String, Date)} 同名重载，便于按目标类型直接调用
     */
    public static String parseDateToStr(final String format, final LocalDateTime dateTime)
    {
        if (dateTime == null)
        {
            return null;
        }
        return DateTimeFormatter.ofPattern(format).format(dateTime);
    }

    /**
     * LocalDate 格式化
     */
    public static String parseDateToStr(final String format, final LocalDate date)
    {
        if (date == null)
        {
            return null;
        }
        return DateTimeFormatter.ofPattern(format).format(date);
    }

    /**
     * 按指定格式把字符串解析为 LocalDateTime（**严格**模式：必须整串匹配，不做顺延）
     *
     * @param format 日期格式
     * @param ts     待解析字符串
     * @return 解析失败返回 null
     */
    public static LocalDateTime parseLocalDateTime(final String format, final String ts)
    {
        if (StringUtils.isEmpty(ts))
        {
            return null;
        }
        try
        {
            return LocalDateTime.parse(ts.trim(), DateTimeFormatter.ofPattern(format));
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    /**
     * 日期型字符串转为 LocalDateTime，逐个尝试 {@link #parsePatterns} 中的格式
     * <p>
     * 纯日期串（如 {@code 2026-09-22}）按当日零点处理。
     *
     * @return 全部格式都不匹配时返回 null
     */
    public static LocalDateTime parseLocalDateTime(Object str)
    {
        String text = str == null ? null : str.toString().trim();
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        // 先按纯日期处理（补零点），再试带时间的格式
        for (String dateOnly : new String[] { YYYY_MM_DD, "yyyy/MM/dd", "yyyy.MM.dd" })
        {
            try
            {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern(dateOnly)).atStartOfDay();
            }
            catch (DateTimeParseException ignored)
            {
                // 继续试下一种
            }
        }
        for (String pattern : parsePatterns)
        {
            try
            {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
            }
            catch (DateTimeParseException ignored)
            {
                // 继续试下一种
            }
        }
        return null;
    }

    /**
     * 日期型字符串转为 LocalDate，逐个尝试格式
     *
     * @return 全部格式都不匹配时返回 null
     */
    public static LocalDate parseLocalDate(Object str)
    {
        LocalDateTime dateTime = parseLocalDateTime(str);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    /**
     * Date ==> LocalDateTime（按 JVM 默认时区）
     */
    public static LocalDateTime toLocalDateTime(Date date)
    {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * Date ==> LocalDate（按 JVM 默认时区）
     */
    public static LocalDate toLocalDate(Date date)
    {
        LocalDateTime dateTime = toLocalDateTime(date);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    /**
     * 计算相差天数（按**日历天**，取绝对值）
     * <p>
     * 与 {@link #differentDaysByMillisecond(LocalDateTime, LocalDateTime)} 的区别：本方法数的是
     * 跨了几个自然日，后者按「毫秒差整除 24 小时」算。业务上的「滞销 N 天」「账期 N 天」用本方法更自然。
     */
    public static int differentDays(LocalDateTime dateTime1, LocalDateTime dateTime2)
    {
        if (dateTime1 == null || dateTime2 == null)
        {
            return 0;
        }
        return Math.abs((int) ChronoUnit.DAYS.between(dateTime1.toLocalDate(), dateTime2.toLocalDate()));
    }

    /**
     * 计算相差天数（按**毫秒差整除 24 小时**，取绝对值）
     * <p>
     * 与 {@link #differentDaysByMillisecond(Date, Date)} 同口径 —— 供 {@code Date} → {@code LocalDateTime}
     * 的改造**保持原行为**使用（换口径会让阈值边界上的判定结果变化，属未声明的行为变更）。
     */
    public static int differentDaysByMillisecond(LocalDateTime dateTime1, LocalDateTime dateTime2)
    {
        if (dateTime1 == null || dateTime2 == null)
        {
            return 0;
        }
        long millis = Duration.between(dateTime1, dateTime2).toMillis();
        return Math.abs((int) (millis / (1000 * 3600 * 24)));
    }
}
