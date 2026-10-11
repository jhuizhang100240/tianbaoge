package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberLevel;

/**
 * 会员等级 Mapper 接口。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，此处只声明 XML 覆盖不了的列表查询。
 *
 * @author tbgbook
 */
public interface MemberLevelMapper extends BaseMapper<MemberLevel>
{
    /**
     * 查询会员等级列表。
     *
     * @param level 查询条件
     * @return 等级集合
     */
    List<MemberLevel> selectMemberLevelList(MemberLevel level);
}
