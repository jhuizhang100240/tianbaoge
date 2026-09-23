package com.tbgbook.bookstore.book.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * ISBN 识别填充的请求体（{@code POST /book/book/isbn-lookup}）。
 *
 * <p>只收一个 ISBN；**格式校验放在 Service 里**（先归一化再判形状），
 * 因为带连字符的写法是合法输入，注解层的 {@code @Pattern} 会把
 * {@code 978-7-115-42802-8} 误判为非法（见 {@code IsbnUtils#normalize}）。
 *
 * @author tbgbook
 */
@Data
public class IsbnLookupRequest
{
    /** ISBN，可含连字符与空格 */
    @NotBlank(message = "ISBN 不能为空")
    @Size(max = 32, message = "ISBN 不能超过 32 个字符")
    private String isbn;
}
