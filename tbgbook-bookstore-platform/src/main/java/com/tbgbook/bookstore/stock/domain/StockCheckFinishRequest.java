package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 结束盘点的请求体（`POST /inventory/stockcheck/{checkId}/finish`）。
 *
 * @author tbgbook
 */
@Data
public class StockCheckFinishRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 差异率超标时是否强制结束，默认 false。
     *
     * <p>契约注明「需店长权限」—— 权限已由 `inventory:stockcheck:finish` 承担，
     * 而该串**只授给了店长**（`07_role_seed.sql`：店员与库管员都没有 `:finish`），
     * 故不需要额外的权限判断。
     *
     * <p>⚠️ 但这也意味着**「是否强制」目前不区分角色**：能调 `finish` 的人就能传
     * `forceFinish=true` 绕过二次盘点。要真正区分，需要给「强制结束」单独一个权限串
     * （如 `inventory:stockcheck:force`）—— 契约里没有，故不擅自新增，
     * 已登记 `plan/待确认项.md`。
     */
    private Boolean forceFinish;
}
