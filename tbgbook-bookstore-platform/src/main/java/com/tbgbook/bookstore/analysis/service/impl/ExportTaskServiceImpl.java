package com.tbgbook.bookstore.analysis.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.analysis.domain.ExportTask;
import com.tbgbook.bookstore.analysis.domain.request.CreateExportRequest;
import com.tbgbook.bookstore.analysis.domain.request.CustomReportRequest;
import com.tbgbook.bookstore.analysis.mapper.AnalysisExportMapper;
import com.tbgbook.bookstore.analysis.mapper.AnalysisExportTaskMapper;
import com.tbgbook.bookstore.analysis.service.IExportTaskService;
import com.tbgbook.bookstore.analysis.support.CustomReportSupport;
import com.tbgbook.bookstore.analysis.support.ExportFileStorage;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.write.metadata.WriteSheet;

/**
 * 异步导出任务 Service 实现。
 *
 * @author tbgbook
 */
@Service
public class ExportTaskServiceImpl extends ServiceImpl<AnalysisExportTaskMapper, ExportTask>
        implements IExportTaskService
{
    private static final Logger log = LoggerFactory.getLogger(ExportTaskServiceImpl.class);

    private static final long STORE_ID = 1L;

    private static final int MAX_RUNNING = 3;

    private static final int MAX_RANGE_DAYS = 366;

    private static final Set<String> EXPORT_TYPES = Set.of(
            "order", "inventory", "member", "stockcheckDiff", "borrow", "custom");

    private static final String COST_PERM = "dashboard:cost:view";

    private final AnalysisExportMapper analysisExportMapper;

    private final ExportFileStorage exportFileStorage;

    private final ObjectMapper objectMapper;

    private final ISysConfigService configService;

    private final ThreadPoolTaskExecutor executor;

    public ExportTaskServiceImpl(AnalysisExportMapper analysisExportMapper,
            ExportFileStorage exportFileStorage, ObjectMapper objectMapper,
            ISysConfigService configService,
            @Qualifier("analysisExportExecutor") ThreadPoolTaskExecutor executor)
    {
        this.analysisExportMapper = analysisExportMapper;
        this.exportFileStorage = exportFileStorage;
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.executor = executor;
    }

    @Override
    public ExportTask create(CreateExportRequest request)
    {
        String type = request.getExportType();
        if (!EXPORT_TYPES.contains(type))
        {
            throw BizErrors.badRequest("导出类型取值 order/inventory/member/stockcheckDiff/borrow/custom");
        }
        boolean costFields = Boolean.TRUE.equals(request.getCostFields());
        if (costFields && !SecurityUtils.hasPermi(COST_PERM))
        {
            throw BizErrors.forbidden("请求含成本字段但无查看权限");
        }
        String operator = SecurityUtils.getUsername();
        long running = count(new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getStoreId, STORE_ID)
                .in(ExportTask::getStatus, 1, 2)
                .eq(ExportTask::getCreateBy, operator));
        if (running >= MAX_RUNNING)
        {
            throw BizErrors.tooManyRequests("已有过多进行中的导出任务，请稍后再试");
        }

        Map<String, Object> filters = request.getFilters() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFilters());
        LocalDate[] range = resolveRange(filters);
        filters.put("startDate", range[0].toString());
        filters.put("endDate", range[1].toString());
        filters.put("_costFields", costFields);
        String summary;
        try
        {
            summary = objectMapper.writeValueAsString(filters);
        }
        catch (Exception e)
        {
            throw BizErrors.serverError("导出筛选条件序列化失败");
        }
        if (summary.length() > 500)
        {
            throw BizErrors.ruleViolated("筛选条件过长，请精简后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        ExportTask task = new ExportTask();
        task.setStoreId(STORE_ID);
        task.setTaskName(StringUtils.hasText(request.getTaskName())
                ? request.getTaskName() : "导出-" + type + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        task.setExportType(type);
        task.setFilterSummary(summary);
        task.setTotalRows(0);
        task.setProgress(0);
        task.setStatus(1);
        task.setFileUrl("");
        task.setFileSize(0L);
        task.setExpireTime(now.plusDays(intConfig("biz.export.file.expire.days", 7)));
        task.setIsSensitive("member".equals(type) ? 1 : 0);
        task.setAuthBy("");
        task.setNotifyType(1);
        task.setFailReason("");
        task.setCreateBy(operator);
        task.setCreateTime(now);
        baseMapper.insert(task);
        submitIfPending(task.getTaskId());
        return task;
    }

    @Override
    public Map<String, Object> createCustom(CustomReportRequest request, long estimatedRows)
    {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("dimensions", request.getDimensions());
        filters.put("metrics", request.getMetrics());
        filters.put("startDate", request.getStartDate().toString());
        filters.put("endDate", request.getEndDate().toString());
        CreateExportRequest create = new CreateExportRequest();
        create.setExportType("custom");
        create.setTaskName("自定义报表-" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        create.setFilters(filters);
        create.setCostFields(request.getMetrics().contains("gross_profit"));
        ExportTask task = create(create);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("estimatedRows", estimatedRows);
        return data;
    }

    @Override
    public List<ExportTask> selectExportList(ExportTask query)
    {
        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExportTask::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(StringUtils.hasText(query.getExportType()), ExportTask::getExportType, query.getExportType());
            wrapper.eq(query.getStatus() != null, ExportTask::getStatus, query.getStatus());
            wrapper.eq(query.getIsSensitive() != null, ExportTask::getIsSensitive, query.getIsSensitive());
            wrapper.eq(StringUtils.hasText(query.getCreateBy()), ExportTask::getCreateBy, query.getCreateBy());
        }
        wrapper.orderByDesc(ExportTask::getTaskId);
        List<ExportTask> list = list(wrapper);
        list.forEach(task -> task.setFileUrl(null));
        return list;
    }

    @Override
    public ExportTask getTask(Long taskId)
    {
        ExportTask task = getById(taskId);
        if (task == null)
        {
            throw BizErrors.notFound("导出任务", taskId);
        }
        refreshExpired(task);
        return task;
    }

    @Override
    public ExportTask authorize(Long taskId, String reason)
    {
        ExportTask task = getTask(taskId);
        if (!Integer.valueOf(1).equals(task.getIsSensitive()))
        {
            throw BizErrors.ruleViolated("该任务不含敏感字段，无需二次授权");
        }
        if (!Integer.valueOf(3).equals(task.getStatus()))
        {
            throw BizErrors.conflict("该任务尚未生成完成");
        }
        task.setAuthBy(SecurityUtils.getUsername());
        task.setAuthTime(LocalDateTime.now());
        updateById(task);
        return task;
    }

    @Override
    public Resource download(Long taskId)
    {
        ExportTask task = getTask(taskId);
        if (Integer.valueOf(5).equals(task.getStatus()))
        {
            throw BizErrors.gone("文件已过期（超过 " + intConfig("biz.export.file.expire.days", 7) + " 天），请重新导出");
        }
        if (!Integer.valueOf(3).equals(task.getStatus()))
        {
            throw BizErrors.conflict("任务尚未完成");
        }
        if (Integer.valueOf(1).equals(task.getIsSensitive()) && !StringUtils.hasText(task.getAuthBy()))
        {
            throw BizErrors.forbidden("含敏感字段的任务未完成二次授权");
        }
        return exportFileStorage.read(task.getFileUrl());
    }

    /**
     * 领取待处理任务（status 1→2）并提交线程池。
     *
     * <p>阶段四 `POST /sales/order/export` 只插入排队任务、不负责生成文件；本调度器统一领取，
     * 同时覆盖应用重启后遗留的排队任务。
     */
    @Scheduled(fixedDelay = 30000L, initialDelay = 20000L)
    public void dispatchPending()
    {
        List<ExportTask> pending = list(new LambdaQueryWrapper<ExportTask>()
                .eq(ExportTask::getStoreId, STORE_ID)
                .eq(ExportTask::getStatus, 1)
                .orderByAsc(ExportTask::getTaskId)
                .last("limit 5"));
        for (ExportTask task : pending)
        {
            submitIfPending(task.getTaskId());
        }
    }

    private void submitIfPending(Long taskId)
    {
        int claimed = baseMapper.update(null, new LambdaUpdateWrapper<ExportTask>()
                .eq(ExportTask::getTaskId, taskId)
                .eq(ExportTask::getStatus, 1)
                .set(ExportTask::getStatus, 2)
                .set(ExportTask::getProgress, 5));
        if (claimed == 0)
        {
            return;
        }
        try
        {
            executor.execute(() -> runTask(taskId));
        }
        catch (TaskRejectedException e)
        {
            updateStatus(taskId, 4, 0, "导出队列已满，请稍后重试", null);
        }
    }

    private void runTask(Long taskId)
    {
        ExportTask task = getById(taskId);
        if (task == null)
        {
            return;
        }
        updateProgress(taskId, 2, 5);
        Path temp = null;
        try
        {
            Dataset dataset = buildDataset(task);
            updateProgress(taskId, 2, 40);
            temp = Files.createTempFile("tbg-export-", ".xlsx");
            writeExcel(temp, dataset.headers(), dataset.rows());
            updateProgress(taskId, 2, 80);
            String fileUrl = exportFileStorage.store(temp);
            updateStatus(taskId, 3, 100, "", fileUrl);
            update(new LambdaUpdateWrapper<ExportTask>()
                    .eq(ExportTask::getTaskId, taskId)
                    .set(ExportTask::getTotalRows, dataset.rows().size())
                    .set(ExportTask::getFileSize, Files.size(temp))
                    .set(ExportTask::getFinishTime, LocalDateTime.now()));
        }
        catch (Exception e)
        {
            log.error("export task failed, taskId={}", taskId, e);
            updateStatus(taskId, 4, 0, truncate(e.getMessage()), null);
        }
        finally
        {
            if (temp != null)
            {
                try
                {
                    Files.deleteIfExists(temp);
                }
                catch (IOException ignored)
                {
                }
            }
        }
    }

    private Dataset buildDataset(ExportTask task) throws Exception
    {
        Map<String, Object> filters = StringUtils.hasText(task.getFilterSummary())
                ? objectMapper.readValue(task.getFilterSummary(),
                        new TypeReference<LinkedHashMap<String, Object>>() {})
                : new LinkedHashMap<>();
        String type = "订单".equals(task.getExportType()) ? "order" : task.getExportType();
        LocalDate[] range = resolveRange(filters);
        LocalDate start = range[0];
        LocalDate end = range[1];
        boolean costFields = Boolean.TRUE.equals(filters.get("_costFields"));
        List<List<String>> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        switch (type)
        {
            case "order" -> {
                headers.addAll(headersOf("订单号", "下单时间", "会员", "订单类型", "商品合计",
                        "优惠合计", "实付", "状态", "收银员"));
                if (costFields)
                {
                    headers.add(List.of("毛利"));
                }
                for (Map<String, Object> row : analysisExportMapper.exportOrders(start, end))
                {
                    List<Object> line = new ArrayList<>(List.of(
                            str(row, "orderNo"), str(row, "createTime"), str(row, "memberName"),
                            str(row, "orderType"), str(row, "totalAmount"), str(row, "discountTotal"),
                            str(row, "payAmount"), str(row, "status"), str(row, "cashierBy")));
                    if (costFields)
                    {
                        line.add(str(row, "grossProfit"));
                    }
                    rows.add(line);
                }
            }
            case "inventory" -> {
                headers.addAll(headersOf("图书编号", "ISBN", "书名", "分类", "可售", "锁定", "借出", "残损"));
                if (costFields)
                {
                    headers.add(List.of("库存总金额"));
                }
                Long categoryId = toLong(filters.get("categoryId"));
                for (Map<String, Object> row : analysisExportMapper.exportInventory(categoryId))
                {
                    List<Object> line = new ArrayList<>(List.of(
                            str(row, "bookNo"), str(row, "isbn"), str(row, "title"),
                            str(row, "categoryName"), str(row, "qtyAvailable"), str(row, "qtyLocked"),
                            str(row, "qtyBorrowed"), str(row, "qtyDamaged")));
                    if (costFields)
                    {
                        line.add(str(row, "totalAmount"));
                    }
                    rows.add(line);
                }
            }
            case "member" -> {
                headers.addAll(headersOf("会员卡号", "昵称", "手机号", "学号", "等级", "积分",
                        "储值本金", "储值赠送", "押金", "状态", "注册时间"));
                for (Map<String, Object> row : analysisExportMapper.exportMembers())
                {
                    rows.add(List.of(str(row, "memberNo"), str(row, "nickname"), str(row, "phone"),
                            str(row, "studentNo"), str(row, "levelName"), str(row, "points"),
                            str(row, "balancePrincipal"), str(row, "balanceGift"), str(row, "depositBalance"),
                            str(row, "status"), str(row, "createTime")));
                }
            }
            case "borrow" -> {
                headers.addAll(headersOf("借阅单号", "会员卡号", "会员", "ISBN", "书名", "借出时间",
                        "应还时间", "归还时间", "状态", "罚金", "办理人"));
                for (Map<String, Object> row : analysisExportMapper.exportBorrows(start, end))
                {
                    rows.add(List.of(str(row, "borrowNo"), str(row, "memberNo"), str(row, "memberName"),
                            str(row, "isbn"), str(row, "title"), str(row, "borrowTime"),
                            str(row, "dueTime"), str(row, "returnTime"), str(row, "status"),
                            str(row, "fineAmount"), str(row, "operatorBy")));
                }
            }
            case "stockcheckDiff" -> {
                headers.addAll(headersOf("盘点单号", "盘点类型", "ISBN", "书名", "账面数", "实盘数",
                        "差异数", "差异金额", "差异类型", "原因", "盘点时间"));
                for (Map<String, Object> row : analysisExportMapper.exportStockcheckDiff(start, end))
                {
                    rows.add(List.of(str(row, "checkNo"), str(row, "checkType"), str(row, "isbn"),
                            str(row, "title"), str(row, "bookQty"), str(row, "actualQty"),
                            str(row, "diffQty"), str(row, "diffAmount"), str(row, "diffType"),
                            str(row, "reason"), str(row, "checkTime")));
                }
            }
            case "custom" -> {
                List<String> dimensions = toStringList(filters.get("dimensions"));
                List<String> metrics = toStringList(filters.get("metrics"));
                headers.addAll(headersOf("维度"));
                for (String metric : metrics)
                {
                    headers.add(List.of(CustomReportSupport.metricLabel(metric)));
                }
                String dimension = dimensions.isEmpty() ? "date" : dimensions.get(0);
                for (Map<String, Object> row : analysisExportMapper.customReportRows(start, end, dimension, 200000))
                {
                    List<Object> line = new ArrayList<>();
                    line.add(str(row, "dimensionName"));
                    line.addAll(CustomReportSupport.row(row, metrics).subList(1, metrics.size() + 1));
                    rows.add(line);
                }
            }
            default -> throw BizErrors.badRequest("不支持的导出类型");
        }
        return new Dataset(headers, rows);
    }

    private void writeExcel(Path temp, List<List<String>> headers, List<List<Object>> rows) throws IOException
    {
        try (ExcelWriter writer = FastExcel.write(temp.toFile()).head(headers).build())
        {
            WriteSheet sheet = FastExcel.writerSheet(0, "数据").build();
            writer.write(rows, sheet);
            writer.finish();
        }
    }

    private void updateProgress(Long taskId, int status, int progress)
    {
        update(new LambdaUpdateWrapper<ExportTask>()
                .eq(ExportTask::getTaskId, taskId)
                .set(ExportTask::getStatus, status)
                .set(ExportTask::getProgress, progress));
    }

    private void updateStatus(Long taskId, int status, int progress, String failReason, String fileUrl)
    {
        LambdaUpdateWrapper<ExportTask> wrapper = new LambdaUpdateWrapper<ExportTask>()
                .eq(ExportTask::getTaskId, taskId)
                .set(ExportTask::getStatus, status)
                .set(ExportTask::getProgress, progress)
                .set(ExportTask::getFailReason, failReason == null ? "" : failReason);
        if (fileUrl != null)
        {
            wrapper.set(ExportTask::getFileUrl, fileUrl);
        }
        update(wrapper);
    }

    private void refreshExpired(ExportTask task)
    {
        if (Integer.valueOf(3).equals(task.getStatus()) && task.getExpireTime() != null
                && LocalDateTime.now().isAfter(task.getExpireTime()))
        {
            exportFileStorage.delete(task.getFileUrl());
            update(new LambdaUpdateWrapper<ExportTask>()
                    .eq(ExportTask::getTaskId, task.getTaskId())
                    .set(ExportTask::getStatus, 5)
                    .set(ExportTask::getFileUrl, ""));
            task.setStatus(5);
            task.setFileUrl("");
        }
    }

    private LocalDate[] resolveRange(Map<String, Object> filters)
    {
        LocalDate end = parseDate(filters.get("endDate"), LocalDate.now());
        LocalDate start = parseDate(filters.get("startDate"), end.minusDays(29));
        if (end.isBefore(start))
        {
            throw BizErrors.badRequest("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS)
        {
            throw BizErrors.ruleViolated("导出时间跨度超过 1 年（需分批）");
        }
        return new LocalDate[] { start, end };
    }

    private LocalDate parseDate(Object value, LocalDate defaultValue)
    {
        if (value == null || String.valueOf(value).isBlank())
        {
            return defaultValue;
        }
        try
        {
            return LocalDate.parse(String.valueOf(value));
        }
        catch (Exception e)
        {
            throw BizErrors.badRequest("日期格式必须为 yyyy-MM-dd");
        }
    }

    private List<List<String>> headersOf(String... headers)
    {
        return java.util.Arrays.stream(headers).map(List::of).toList();
    }

    private String str(Map<String, Object> row, String key)
    {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Long toLong(Object value)
    {
        if (value == null || String.valueOf(value).isBlank())
        {
            return null;
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw BizErrors.badRequest("categoryId 必须为数字");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value)
    {
        if (value instanceof List<?> list)
        {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String truncate(String text)
    {
        if (text == null)
        {
            return "导出失败";
        }
        return text.length() <= 480 ? text : text.substring(0, 480);
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private record Dataset(List<List<String>> headers, List<List<Object>> rows)
    {
    }
}
