package com.tbgbook.bookstore.book.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书主档对象 biz_book
 *
 * <p><b>三条由服务端负责、客户端传了也会被忽略的字段</b>（接口文档「图书」业务约束）：
 * <ul>
 *   <li>{@code bookNo} —— 由服务端按 {@code BK}+8 位流水生成</li>
 *   <li>{@code costPrice} —— 移动加权平均成本，由入库/盘点/调价自动重算，**不接受手工传入**</li>
 *   <li>{@code saleCount} —— 累计销量，由销售流程累加</li>
 * </ul>
 * 因此这三项**不在请求体字段清单里**；本实体保留它们是为了读接口能返回。
 *
 * <p><b>成本可见性（FR-4.6 规则 5）</b>：{@code costPrice} 只在携带
 * {@code book:cost:view} 权限时返回值，否则置 {@code null} —— **不是报错**，
 * 前端据此隐藏列。见 {@code BookServiceImpl#maskCost}。
 *
 * <p><b>四类库存</b>（需求 §3.2 库存规则 1）不在本表，聚合自 {@code biz_stock}：
 * {@code qtyAvailable} / {@code qtyLocked} / {@code qtyBorrowed} / {@code qtyDamaged}
 * 为瞬态字段，列表查询时由 XML 子查询算出（原型 A-06 的「四类库存合计」列）。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：① 删掉从列注释误解析出的
 * {@code readConverterExp}（如 {@code B=K+8位流水}、{@code 冗=余，列表展示免,J=OIN}）；
 * ② 补校验注解；③ 补 4 个库存瞬态字段。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_book")
public class Book extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 图书ID */
    @TableId(value = "book_id", type = IdType.AUTO)
    private Long bookId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 图书编号（BK+8位流水）。**服务端生成**，不接受客户端传入 */
    @TableField("book_no")
    private String bookNo;

    /** ISBN。`uk_isbn` 唯一 —— 同 ISBN 仅允许一条主记录，多副本通过库存数量管理 */
    @NotBlank(message = "ISBN 不能为空")
    @Size(max = 20, message = "ISBN 不能超过 20 个字符")
    @TableField("isbn")
    private String isbn;

    /** 书名 */
    @NotBlank(message = "书名不能为空")
    @Size(max = 200, message = "书名不能超过 200 个字符")
    @TableField("title")
    private String title;

    /** 副标题 */
    @Size(max = 200, message = "副标题不能超过 200 个字符")
    @TableField("subtitle")
    private String subtitle;

    /** 作者 */
    @Size(max = 200, message = "作者不能超过 200 个字符")
    @TableField("author")
    private String author;

    /** 译者 */
    @Size(max = 200, message = "译者不能超过 200 个字符")
    @TableField("translator")
    private String translator;

    /** 出版社ID */
    @TableField("publisher_id")
    private Long publisherId;

    /** 出版社名称（冗余，列表展示免 JOIN；由服务端按 publisherId 回填） */
    @Size(max = 100, message = "出版社名称不能超过 100 个字符")
    @TableField("publisher_name")
    private String publisherName;

    /** 出版日期（接口文档声明为 string(date) → yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("publish_date")
    private LocalDate publishDate;

    /** 版次 */
    @Size(max = 50, message = "版次不能超过 50 个字符")
    @TableField("edition")
    private String edition;

    /** 装帧 */
    @Size(max = 30, message = "装帧不能超过 30 个字符")
    @TableField("binding")
    private String binding;

    /** 页数 */
    @Min(value = 0, message = "页数不能为负")
    @TableField("page_count")
    private Long pageCount;

    /** 分类ID */
    @TableField("category_id")
    private Long categoryId;

    /** 图书类型（1普通 2教材） */
    @Min(value = 1, message = "图书类型取值 1（普通）或 2（教材）")
    @Max(value = 2, message = "图书类型取值 1（普通）或 2（教材）")
    @TableField("book_type")
    private Integer bookType;

    /** 定价。**金额一律 DECIMAL，严禁 float/double**（需求 §6.3） */
    @DecimalMin(value = "0", message = "定价不能为负")
    @TableField("price")
    private BigDecimal price;

    /** 会员价（0=不单独设置，按等级折扣计算） */
    @DecimalMin(value = "0", message = "会员价不能为负")
    @TableField("member_price")
    private BigDecimal memberPrice;

    /** 成本价（移动加权平均）。**服务端计算**；仅在携带 book:cost:view 时返回 */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /** 封面地址 */
    @Size(max = 500, message = "封面地址不能超过 500 个字符")
    @TableField("cover_url")
    private String coverUrl;

    /** 内容简介 */
    @TableField("summary")
    private String summary;

    /** 简介来源（1出版社原始 2AI生成已审核） */
    @Min(value = 1, message = "简介来源取值 1 或 2")
    @Max(value = 2, message = "简介来源取值 1 或 2")
    @TableField("summary_source")
    private Integer summarySource;

    /** 建档信息状态（1完整 2待补全）。服务端按字段完整度维护 */
    @Min(value = 1, message = "建档信息状态取值 1 或 2")
    @Max(value = 2, message = "建档信息状态取值 1 或 2")
    @TableField("info_status")
    private Integer infoStatus;

    /** 图书状态（1在售 2下架 3缺货 4停售） */
    @Min(value = 1, message = "图书状态取值 1~4")
    @Max(value = 4, message = "图书状态取值 1~4")
    @TableField("status")
    private Integer status;

    /** 上架状态（1已上架 0未上架） */
    @Min(value = 0, message = "上架状态取值 0 或 1")
    @Max(value = 1, message = "上架状态取值 0 或 1")
    @TableField("publish_status")
    private Integer publishStatus;

    /** 累计销量（冗余，榜单排序用）。服务端累加 */
    @TableField("sale_count")
    private Long saleCount;

    /** 删除标志（0存在 2删除） */
    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    // ------------------------------------------------------------------
    // 瞬态字段：聚合自 biz_stock，不落本表
    // ------------------------------------------------------------------

    /** 可售库存（聚合自 biz_stock.qty_available） */
    @TableField(exist = false)
    private Long qtyAvailable;

    /** 锁定库存（聚合自 biz_stock.qty_locked） */
    @TableField(exist = false)
    private Long qtyLocked;

    /** 借出库存（聚合自 biz_stock.qty_borrowed） */
    @TableField(exist = false)
    private Long qtyBorrowed;

    /** 残损库存（聚合自 biz_stock.qty_damaged） */
    @TableField(exist = false)
    private Long qtyDamaged;
}
