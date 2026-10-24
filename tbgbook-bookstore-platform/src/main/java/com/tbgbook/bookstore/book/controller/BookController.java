package com.tbgbook.bookstore.book.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookLabelRequest;
import com.tbgbook.bookstore.book.domain.BookStatusRequest;
import com.tbgbook.bookstore.book.domain.IsbnLookupRequest;
import com.tbgbook.bookstore.book.domain.IsbnLookupResult;
import com.tbgbook.bookstore.book.service.IBookDetailService;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.book.service.IIsbnLookupService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.constant.HttpStatus;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 图书主档 Controller（页面 A-06 图书列表 / A-07 图书新增编辑）。
 *
 * <p>相对代码生成器产物的**人工裁剪点**（逐条对照 `docs/api-documentation.md` §图书）：
 * <ol>
 *   <li><b>删掉 export 端点</b> —— 文档里图书只有 5 个端点；导出走「异步导出任务 + 下载中心」
 *       （FR-4.6 规则 4），不在本资源上</li>
 *   <li><b>详情权限串由 {@code :query} 改为 {@code :list}</b></li>
 *   <li><b>删除走 Service 的 {@code removeBooks}</b>（带删除保护），不用 {@code removeByIds}</li>
 *   <li>补 {@code @Validated} / {@code @Valid}（NFR-M2）</li>
 *   <li><b>补 4 个动作端点</b>：{@code /isbn-lookup}、{@code /{bookId}/status}、
 *       {@code /{bookId}/detail}、{@code /options}、{@code /label/print} —— 契约里都有、
 *       初版实现漏了（2026-09-23 补齐，见 plan/backend-plan.md 进度日志）</li>
 * </ol>
 *
 * <p><b>成本价 {@code costPrice}</b> 由 Service 按 {@code book:cost:view} 权限置 null，
 * 本层不做判断 —— 权限逻辑集中在 Service，Controller 只做协议适配（后端 CLAUDE.md §四）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/book")
public class BookController extends BaseController
{
    private final IBookService bookService;

    private final IBookDetailService bookDetailService;

    private final IIsbnLookupService isbnLookupService;

    /**
     * ISBN 识别填充（FR-4.1.1 规则 1，A-07 录入时自动带出书名/作者/出版社/封面）。
     *
     * <p>权限串 {@code book:book:isbnLookup} 与契约一致（动作端点的权限 = 资源前缀 + 路径末段）。
     *
     * <p><b>两种正常结局</b>：① 外部书库命中 → 200 + 书目字段；② 未命中或调用失败 →
     * 依然 200，但 {@code infoStatus=2}，前端提示「信息待补全」并允许手工录入 ——
     * **调用失败不阻断建档**是契约明写的。
     *
     * <p><b>一种冲突结局</b>：该 ISBN 本地已有主记录 → **409**，响应 {@code data} 里带
     * 已存在图书的 {@code bookId}，前端据此直接跳转而不是新建（避免撞 {@code uk_isbn}）。
     */
    @PreAuthorize("@ss.hasPermi('book:book:isbnLookup')")
    @PostMapping("/isbn-lookup")
    public AjaxResult isbnLookup(@Valid @RequestBody IsbnLookupRequest request)
    {
        IsbnLookupResult result = isbnLookupService.lookup(request.getIsbn());
        if (result.getExistingBookId() != null)
        {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookId", result.getExistingBookId());
            data.put("bookNo", result.getExistingBookNo());
            data.put("isbn", result.getIsbn());
            data.put("title", result.getTitle());
            AjaxResult conflict = AjaxResult.error(HttpStatus.CONFLICT,
                    "该 ISBN 已存在图书建档（" + result.getExistingBookNo() + "），无需重复建档");
            conflict.put("data", data);
            return conflict;
        }
        return success(result);
    }

    /**
     * 图书下拉选项（各模块「选书」控件的轻量数据源）。
     *
     * <p>⚠️ <b>字面量路径与 {@code /{bookId}} 同段位</b>：Spring 的字面量模式优先于路径变量，
     * 所以 {@code GET /book/book/options} 命中本方法而不是 {@code getInfo(bookId="options")}
     * （{@code bookId} 是 {@code Long}，命中后者还会因类型转换失败报 400）。
     * 与 {@code StockController} 的 {@code /scan} 是同一个坑，处理方式也一样：
     * **两条映射放在同一个控制器里**，让优先级只有单一出处。
     *
     * <p>权限串用 {@code book:book:list}（契约如此），不为下拉单开前缀。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/options")
    public AjaxResult options(@RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit)
    {
        return success(bookService.selectBookOptions(keyword, limit));
    }

    /**
     * 图书全景详情（A-08）：主档 + 教材扩展 + 各库位库存 + 价格版本 + 变更日志，一次返回。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/{bookId}/detail")
    public AjaxResult detail(@PathVariable("bookId") Long bookId)
    {
        return success(bookDetailService.selectBookDetail(bookId));
    }

    /**
     * 图书状态变更（FR-4.1.3）：在售 / 下架 / 缺货 / 停售四态互转。
     *
     * <p><b>{@code reason} 为什么不落库</b>：{@code biz_book_change_log} 只有
     * field_name / before_value / after_value，**没有原因列**，结构化存不下。
     * 因此状态流转本身由 Service 写进变更日志（A-06 的「变更日志」弹窗能看到），
     * 而「为什么改」由本方法上的 {@code @Log} 记入 {@code sys_oper_log.oper_param}
     * （A-83 操作日志可查）—— 两者合起来才是完整的审计线索。
     * 这是**已知取舍**，不是漏做：要把它变成结构化字段需要加列，属 schema 变更。
     */
    @PreAuthorize("@ss.hasPermi('book:book:status')")
    @Log(title = "图书状态", businessType = BusinessType.UPDATE)
    @PostMapping("/{bookId}/status")
    public AjaxResult changeStatus(@PathVariable("bookId") Long bookId,
            @Valid @RequestBody BookStatusRequest request)
    {
        return toAjax(bookService.changeStatus(bookId, request.getStatus(), request.getReason()));
    }

    /**
     * 生成标签打印数据（FR-4.1.7 / A-11）。
     *
     * <p><b>不加 {@code @Log}</b>：本端点只读不写（生成数据后由前端渲染打印），
     * 与 {@code isbnLookup} 同理 —— 读操作不进操作日志，否则日志会被查询淹没。
     */
    @PreAuthorize("@ss.hasPermi('book:label:print')")
    @PostMapping("/label/print")
    public AjaxResult labelPrint(@Valid @RequestBody BookLabelRequest request)
    {
        return success(bookService.buildLabels(request));
    }

    /**
     * 查询图书列表。
     *
     * <p>响应含四类库存聚合（{@code qtyAvailable} / {@code qtyLocked} /
     * {@code qtyBorrowed} / {@code qtyDamaged}），供原型 A-06 的「四类库存合计」列使用。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/list")
    public TableDataInfo list(Book book)
    {
        startPage();
        List<Book> list = bookService.selectBookList(book);
        return getDataTable(list);
    }

    /**
     * 获取图书详细信息。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/{bookId}")
    public AjaxResult getInfo(@PathVariable("bookId") Long bookId)
    {
        return success(bookService.selectBookById(bookId));
    }

    /**
     * 新增图书。
     *
     * <p>ISBN 重复返回 409；{@code bookNo} / {@code costPrice} / {@code saleCount}
     * 由服务端生成，客户端传入会被忽略。
     */
    @PreAuthorize("@ss.hasPermi('book:book:add')")
    @Log(title = "图书", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Book book)
    {
        return toAjax(bookService.addBook(book));
    }

    /**
     * 修改图书。
     *
     * <p>价格变化与关键字段变化会在同事务内写入价格历史与变更日志。
     */
    @PreAuthorize("@ss.hasPermi('book:book:edit')")
    @Log(title = "图书", businessType = BusinessType.UPDATE, isSensitive = true, bizType = "price")
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Book book)
    {
        return toAjax(bookService.updateBook(book));
    }

    /**
     * 删除图书。
     *
     * <p>已有库存或流水的图书返回 422，提示改为下架。
     */
    @PreAuthorize("@ss.hasPermi('book:book:remove')")
    @Log(title = "图书", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookIds}")
    public AjaxResult remove(@PathVariable Long[] bookIds)
    {
        return toAjax(bookService.removeBooks(bookIds));
    }
}
