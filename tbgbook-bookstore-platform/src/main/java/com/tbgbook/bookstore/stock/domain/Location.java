package com.tbgbook.bookstore.stock.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库位对象 biz_location（页面 A-16 库位管理）。
 *
 * <p>{@code locationCode} 形如 {@code A-3-2-1}，对应页面「A 区 3 排 2 层」——
 * 编码里的三段与 {@code zone} / {@code shelfNo} / {@code layerNo} 是**同一信息的两种表达**，
 * 由运营自行保持一致（服务端不反解编码，因为历史数据里编码格式不统一）。
 *
 * <p>{@code uk_store_code} 唯一：同一门店内库位编码不可重复，重复建档返回 409。
 *
 * <p><b>删除保护</b>：被 {@code biz_stock.location_id} 引用的库位禁止删除（422），
 * 理由与「分类/出版社被图书引用」同 —— 删掉会让库存行指向不存在的库位，
 * 而库存流水是只增不删的，历史流水会因此失去主语。正确做法是停用（{@code status=1}）。
 *
 * <p>本表有 {@code del_flag} 与审计五字段，故继承 {@code BaseEntity}。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_location")
public class Location extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 库位ID */
    @TableId(value = "location_id", type = IdType.AUTO)
    private Long locationId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 库位编码（如 A-3-2-1） */
    @NotBlank(message = "库位编码不能为空")
    @Size(max = 30, message = "库位编码不能超过 30 个字符")
    @TableField("location_code")
    private String locationCode;

    /** 库位名称 */
    @Size(max = 100, message = "库位名称不能超过 100 个字符")
    @TableField("location_name")
    private String locationName;

    /** 所在区域（如 教材区） */
    @Size(max = 50, message = "所在区域不能超过 50 个字符")
    @TableField("zone")
    private String zone;

    /** 排号 */
    @Size(max = 20, message = "排号不能超过 20 个字符")
    @TableField("shelf_no")
    private String shelfNo;

    /** 层号 */
    @Size(max = 20, message = "层号不能超过 20 个字符")
    @TableField("layer_no")
    private String layerNo;

    /** 显示顺序 */
    @Min(value = 0, message = "显示顺序不能为负")
    @TableField("order_num")
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0正常 / 1停用")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除） */
    @TableLogic(value = "0", delval = "2")
    @TableField("del_flag")
    private String delFlag;
}
