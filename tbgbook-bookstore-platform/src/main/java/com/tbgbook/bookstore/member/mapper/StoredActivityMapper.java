package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.StoredActivity;

/**
 * 储值充值活动 Mapper 接口（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface StoredActivityMapper extends BaseMapper<StoredActivity>
{
    /**
     * 查询充值活动列表。
     *
     * @param activity 查询条件
     * @return 活动集合
     */
    List<StoredActivity> selectStoredActivityList(StoredActivity activity);
}
