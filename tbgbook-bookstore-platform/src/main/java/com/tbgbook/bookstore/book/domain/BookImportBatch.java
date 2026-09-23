package com.tbgbook.bookstore.book.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书批量导入批次对象 biz_book_import_batch
 *
 * <p><b>两步式导入的批次头</b>（FR-4.1.6 / 页面 A-10）：
 * {@code POST /book/import/parse} 解析 Excel 并写入批次与明细行（状态 → 2校验完成），
 * 人工确认后 {@code POST /book/import/{batchId}/execute} 落库（状态 3导入中 → 4已完成）。
 *
 * <p><b>无 {@code del_flag}</b> —— 本表不属于「基础数据表」，删除为物理删除
 * （根 CLAUDE.md §6.2：仅基础数据表再加 {@code del_flag}）。
 * 因此**不挂 {@code @TableLogic}**，也就不能沿用 {@code Book} 那套「删除=改标记」的写法。
 *
 * <p><b>服务端独占字段</b>（接口文档的 {@code skip} 清单）：{@code batchNo} /
 * {@code totalRows} / {@code validRows} / {@code warnRows} / {@code errorRows} /
 * {@code successRows} / {@code failRows} / {@code status} / {@code progress} /
 * {@code failFileUrl}。客户端传了也会被 {@code BookImportBatchServiceImpl} 置空。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_book_import_batch")
public class BookImportBatch extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 状态与策略取值（接口文档「图书批量导入批次」字段字典）
    // 与 status 列注释一一对应，不是运营可调阈值，故不落 sys_config（NFR-M4 针对的是可调阈值）
    // ------------------------------------------------------------------

    /** 状态：解析中 */
    public static final int STATUS_PARSING = 1;

    /** 状态：校验完成（可执行导入） */
    public static final int STATUS_VALIDATED = 2;

    /** 状态：导入中 */
    public static final int STATUS_IMPORTING = 3;

    /** 状态：已完成 */
    public static final int STATUS_DONE = 4;

    /** 状态：失败 */
    public static final int STATUS_FAILED = 5;

    /** 重复 ISBN 策略：跳过 */
    public static final int STRATEGY_SKIP = 1;

    /** 重复 ISBN 策略：覆盖更新 */
    public static final int STRATEGY_OVERWRITE = 2;

    /** 批次ID */
    @TableId(value = "batch_id", type = IdType.AUTO)
    private Long batchId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 批次号（{@code IMP}+时间戳+序列，**服务端生成**） */
    @TableField("batch_no")
    private String batchNo;

    /** 文件名（原始文件名，服务端只取文件名不取路径 —— NFR-S13） */
    @Size(max = 255, message = "文件名不能超过 255 个字符")
    @TableField("file_name")
    private String fileName;

    /** 文件地址（服务端上传后的相对路径，**服务端生成**） */
    @TableField("file_url")
    private String fileUrl;

    /** 总行数（服务端按解析结果写入） */
    @TableField("total_rows")
    private Integer totalRows;

    /** 可导入行数 */
    @TableField("valid_rows")
    private Integer validRows;

    /** 警告行数 */
    @TableField("warn_rows")
    private Integer warnRows;

    /** 错误行数 */
    @TableField("error_rows")
    private Integer errorRows;

    /** 实际成功行数（执行阶段写入） */
    @TableField("success_rows")
    private Integer successRows;

    /** 实际失败行数（执行阶段写入） */
    @TableField("fail_rows")
    private Integer failRows;

    /** 重复ISBN策略（1跳过 2覆盖更新） */
    @Min(value = 1, message = "重复ISBN策略取值 1（跳过）或 2（覆盖更新）")
    @Max(value = 2, message = "重复ISBN策略取值 1（跳过）或 2（覆盖更新）")
    @TableField("import_strategy")
    private Integer importStrategy;

    /** 状态（1解析中 2校验完成 3导入中 4已完成 5失败） */
    @TableField("status")
    private Integer status;

    /** 进度百分比（0~100，执行阶段逐批刷新，供详情轮询） */
    @TableField("progress")
    private Integer progress;

    /** 失败明细文件地址（执行完成后生成） */
    @TableField("fail_file_url")
    private String failFileUrl;
}
