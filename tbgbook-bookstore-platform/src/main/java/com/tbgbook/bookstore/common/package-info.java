/**
 * 业务公共支撑包 —— 只放**跨域复用**的东西，放业务实体请去各自的域包。
 *
 * <p>结构：
 * <ul>
 *   <li>{@code constant.BizErrorCode} —— 业务错误码常量（13 个，对齐接口文档）</li>
 *   <li>{@code exception.BizErrors} —— 业务异常静态工厂，码与文案一起给出</li>
 *   <li>{@code support.ReferenceGuard} —— 引用完整性校验统一封装（四类校验）</li>
 * </ul>
 *
 * <p><b>边界</b>：本包属于 {@code tbgbook-bookstore-platform}，只对业务可用。
 * 按 {@code plan/待确认项.md} G8 的裁决，业务错误码**不放** {@code tbgbook-common} ——
 * 平台侧引不到业务错误码，边界更清晰。
 *
 * <p><b>放东西前先自问</b>：只有一个域用得到的，留在那个域里；两个以上域复用的，才放这里。
 * 不要让它长成第二个「common 垃圾场」。
 *
 * @author tbgbook
 */
package com.tbgbook.bookstore.common;
