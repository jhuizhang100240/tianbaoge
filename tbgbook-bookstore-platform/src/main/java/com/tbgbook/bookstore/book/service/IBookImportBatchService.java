package com.tbgbook.bookstore.book.service;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.BookImportBatch;
import com.tbgbook.bookstore.book.domain.excel.BookImportParseResult;

/**
 * 图书批量导入批次 Service 接口。
 *
 * <p><b>两步式导入</b>（FR-4.1.6 / 页面 A-10）：解析与执行分开，中间留人工确认环节。
 *
 * @author tbgbook
 */
public interface IBookImportBatchService extends IService<BookImportBatch>
{
    /**
     * 查询导入批次列表。
     *
     * @param batch 查询条件
     * @return 批次集合
     */
    List<BookImportBatch> selectImportBatchList(BookImportBatch batch);

    /**
     * 查询批次详情。
     *
     * @param batchId 批次ID
     * @return 批次；不存在时抛 404
     */
    BookImportBatch selectImportBatchById(Long batchId);

    /**
     * 新增批次（手工登记，不含明细行）。
     *
     * @param batch 批次
     * @return 影响行数
     */
    int addImportBatch(BookImportBatch batch);

    /**
     * 修改批次。
     *
     * @param batch 批次
     * @return 影响行数
     */
    int updateImportBatch(BookImportBatch batch);

    /**
     * 批量删除批次（**主从级联**：明细行随批次一起删）。
     *
     * @param batchIds 批次主键数组
     * @return 实际删除行数
     */
    int removeImportBatches(Long[] batchIds);

    /**
     * 第一步：上传并解析 Excel（**只解析校验，不写图书**）。
     *
     * <p>解析结果落 {@code biz_book_import_batch}（状态 → 2校验完成）与
     * {@code biz_book_import_row}，并把整行快照存进 {@code raw_data} ——
     * 执行阶段据此落库，不再二次读取文件。
     *
     * @param file            Excel 文件（xls/xlsx，≤ 10MB）
     * @param importStrategy  重复ISBN策略（1跳过 2覆盖更新），为空按 1
     * @return 批次汇总 + 逐行校验结果
     */
    BookImportParseResult parseExcel(MultipartFile file, Integer importStrategy);

    /**
     * 第二步：提交执行（**异步**）。
     *
     * <p>本方法只做两件事：① 用一条带状态条件的 UPDATE 原子占位（2校验完成 → 3导入中），
     * 并发重复提交时第二次拿到 0 行并抛 422；② 把实际导入丢给异步线程池。
     *
     * <p>返回的是**提交瞬间的快照**（status=3、progress=0、successRows=0），
     * 真实结果要靠轮询 {@link #selectImportBatchById} 获取 ——
     * 这是接口文档「执行过程异步，progress 可轮询批次详情获取」的直接体现。
     *
     * @param batchId      批次ID
     * @param skipWarnRows 是否跳过警告行
     * @return 提交瞬间的批次快照
     */
    BookImportBatch submitExecute(Long batchId, Boolean skipWarnRows);

    /**
     * 下载失败明细 Excel（文件流）。
     *
     * <p>内容**现算**：解析阶段判为错误的行 + 执行阶段失败的行，两者都在
     * {@code biz_book_import_row} 里（执行失败会把行状态刷成 3 并写入原因）。
     * 因此不依赖执行结束时落盘的那份快照文件 —— 那份只是 {@code failFileUrl} 的便利副本。
     *
     * @param batchId  批次ID
     * @param response HTTP 响应
     */
    void writeErrorFile(Long batchId, HttpServletResponse response);

    /**
     * 下载标准导入模板 Excel（文件流）。
     *
     * <p>两个 sheet：{@code 图书清单}（表头 + 示例行）、{@code 字段说明}。
     * 表头由 {@code BookImportExcelRow} 的 {@code @ExcelProperty} 声明，
     * 与解析器共用同一份定义，模板与解析规则不会漂移。
     *
     * @param response HTTP 响应
     */
    void writeTemplate(HttpServletResponse response);
}
