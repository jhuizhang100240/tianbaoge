package com.tbgbook.bookstore.book.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookLabelRequest;
import com.tbgbook.bookstore.book.domain.BookLabelResult;
import com.tbgbook.bookstore.book.domain.BookOption;

/**
 * 图书主档 Service 接口。
 *
 * @author tbgbook
 */
public interface IBookService extends IService<Book>
{
    /**
     * 查询图书列表（含四类库存聚合；成本价按权限裁剪）。
     *
     * @param book 查询条件
     * @return 图书集合
     */
    List<Book> selectBookList(Book book);

    /**
     * 查询图书详情（成本价按权限裁剪）。
     *
     * @param bookId 图书ID
     * @return 图书；不存在时抛 404
     */
    Book selectBookById(Long bookId);

    /**
     * 新增图书。
     *
     * <p>服务端负责：生成 {@code bookNo}、回填 {@code publisherName}、
     * 计算 {@code infoStatus}、校验 ISBN 唯一（重复返回 409）。
     * {@code costPrice} / {@code saleCount} 不接受客户端传入。
     *
     * @param book 图书
     * @return 影响行数
     */
    int addBook(Book book);

    /**
     * 修改图书。
     *
     * <p>价格字段变化时写 {@code biz_book_price_history}（FR-4.1 规则 5）；
     * 关键字段变化时写 {@code biz_book_change_log}（FR-4.1.9）。两者与主表更新同事务。
     *
     * @param book 图书
     * @return 影响行数
     */
    int updateBook(Book book);

    /**
     * 变更图书状态（FR-4.1.3，{@code POST /book/book/{bookId}/status}）。
     *
     * <p>在售 / 下架 / 缺货 / 停售四态互转，并**写一条变更日志**（状态是
     * {@code TRACKED_FIELDS} 之一，与 PUT 编辑走同一套留痕逻辑，不另起一套）。
     *
     * <p><b>幂等</b>：目标状态与当前状态相同时直接返回成功，不写库、不留痕
     * —— 重复点击「下架」不该产生第二条日志，也不该因「0 行受影响」被误判成并发冲突。
     *
     * <p><b>只改 {@code status}，不动 {@code publish_status}</b>：契约给本端点定义的
     * 请求体只有 {@code status} 与 {@code reason}。{@code publish_status}（1已上架 0未上架）
     * 是另一个字段，由编辑端点维护；两者关系未在文档中定义，不在这里擅自联动。
     *
     * @param bookId 图书ID
     * @param status 目标状态（1在售 2下架 3缺货 4停售）
     * @param reason 变更原因（契约字段）。⚠️ {@code biz_book_change_log} 无原因列，
     *               该值由控制器 {@code @Log} 记入 {@code sys_oper_log.oper_param}
     * @return 影响行数（幂等命中时返回 1）
     */
    int changeStatus(Long bookId, Integer status, String reason);

    /**
     * 图书下拉选项（{@code GET /book/book/options}）。
     *
     * @param keyword 书名 / ISBN / 图书编号模糊关键词；null 或空表示不过滤
     * @param limit   返回上限；null 或 &lt;1 取默认 20，超过 50 夹到 50
     * @return 选项集合
     */
    List<BookOption> selectBookOptions(String keyword, Integer limit);

    /**
     * 生成标签打印数据（FR-4.1.7，{@code POST /book/book/label/print}）。
     *
     * <p>只生成数据，渲染与调起打印在前端（契约明写）。按 {@code copies} 把每本书的标签
     * **展开成多张**，顺序与入参 {@code bookIds} 一致。
     *
     * @param request 打印请求
     * @return 标签列表 + 生效参数（{@code labelSize} / {@code copies} / {@code includePrice}）
     * @throws com.tbgbook.common.exception.ServiceException 422 列表为空 / 超单批上限 /
     *         份数越界 / ID 非法；404 列表中存在不存在的图书
     */
    BookLabelResult buildLabels(BookLabelRequest request);

    /**
     * 批量删除图书（含**删除保护**）。
     *
     * <p>已有库存或库存流水的图书不允许删除 —— 历史单据与流水会因此失去主语，
     * 正确做法是**下架**（FR-4.1.3）。
     *
     * @param bookIds 图书主键数组
     * @return 实际删除行数
     */
    int removeBooks(Long[] bookIds);
}
