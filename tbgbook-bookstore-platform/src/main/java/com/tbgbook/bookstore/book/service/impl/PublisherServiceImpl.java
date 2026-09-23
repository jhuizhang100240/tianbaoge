package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Publisher;
import com.tbgbook.bookstore.book.mapper.PublisherMapper;
import com.tbgbook.bookstore.book.service.IPublisherService;
import com.tbgbook.bookstore.common.support.ReferenceGuard;

import lombok.RequiredArgsConstructor;

/**
 * 出版社 Service 业务层处理。
 *
 * <p>本类是**项目样板**：演示了四件必须做对的事 ——
 * <ol>
 *   <li>事务边界在 Service 的公开方法上（不在 Controller，见后端 CLAUDE.md §四）</li>
 *   <li>删除保护走 {@link ReferenceGuard} 统一封装，不自己写 count SQL</li>
 *   <li>审计四字段由 {@code MyMetaObjectHandler} 自动填充，**此处不手工 set**
 *       （后端 CLAUDE.md §五）</li>
 *   <li>依赖注入用 {@code @RequiredArgsConstructor} + final，**不用 @Autowired 字段注入**</li>
 * </ol>
 *
 * <p><b>⚠️ 残留竞态（已知取舍）</b>：本项目 84 张业务表零物理外键
 * （{@code docs/db/README.md} §6.1）。「先查引用、再删除」与「并发写入一条引用该出版社的图书」
 * 之间存在时间窗，InnoDB 默认 REPEATABLE READ 下 count 读的是快照，挡不住这个窗口。
 * 出版社是低频维护的基础数据，实际风险极低；**但对高频并发的基础数据（如库位）不能照抄这个假设**，
 * 需要改用 {@code SELECT ... FOR UPDATE} 或应用层锁。这是零外键决策的代价，不是实现疏漏。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class PublisherServiceImpl extends ServiceImpl<PublisherMapper, Publisher> implements IPublisherService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<Publisher> selectPublisherList(Publisher publisher)
    {
        return baseMapper.selectPublisherList(publisher);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removePublishers(Long[] publisherIds)
    {
        List<Long> idList = (publisherIds == null) ? Collections.emptyList() : Arrays.asList(publisherIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "出版社");

        // ② 删除保护：被图书引用的出版社禁止删除（被引用时抛 422，带引用数量）
        for (Serializable id : ids)
        {
            referenceGuard.requireNoReference(
                    "biz_book", "publisher_id", id, "del_flag",
                    "图书", "出版社", "改为停用");
        }

        // 逻辑删除（@TableLogic：del_flag 0→2），不是物理删除
        return baseMapper.deleteByIds(ids);
    }
}
