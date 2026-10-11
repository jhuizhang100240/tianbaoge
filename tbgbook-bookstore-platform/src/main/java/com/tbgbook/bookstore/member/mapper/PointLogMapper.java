package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.PointLog;

/**
 * 积分流水 Mapper 接口（只增不删，仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface PointLogMapper extends BaseMapper<PointLog>
{
    /**
     * 查询积分流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<PointLog> selectPointLogList(PointLog log);
}
