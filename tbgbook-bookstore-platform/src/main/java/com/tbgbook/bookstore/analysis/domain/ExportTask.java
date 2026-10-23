package com.tbgbook.bookstore.analysis.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 异步导出任务（A-70 下载中心）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_export_task")
public class ExportTask
{
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    private Long storeId;

    private String taskName;

    private String exportType;

    private String filterSummary;

    private Integer totalRows;

    private Integer progress;

    /** 1排队中 2生成中 3已完成 4已失败 5已过期 */
    private Integer status;

    private String fileUrl;

    private Long fileSize;

    private LocalDateTime expireTime;

    /** 1含手机号/学号，需二次授权 */
    private Integer isSensitive;

    private String authBy;

    private LocalDateTime authTime;

    private Integer notifyType;

    private String failReason;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
