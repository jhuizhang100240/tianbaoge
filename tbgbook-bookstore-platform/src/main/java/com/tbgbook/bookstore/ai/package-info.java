/**
 * AI 域
 *
 * 分层约定：domain / mapper / service / service.impl / controller
 * 持久层约定：单表 CRUD 用 MyBatis-Plus，复杂查询写 XML；
 *            列表分页统一使用 startPage() + getDataTable()（PageHelper）。
 */
package com.tbgbook.bookstore.ai;
