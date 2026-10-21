package com.tbgbook.bookstore.analysis.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.analysis.domain.ReportPushLog;
import com.tbgbook.bookstore.analysis.domain.ReportSubscribe;

/**
 * 报表订阅 Service（FR-4.6.11）。
 */
public interface IReportSubscribeService extends IService<ReportSubscribe>
{
    List<ReportSubscribe> selectSubscribeList(ReportSubscribe query);

    ReportSubscribe getSubscribe(Long subscribeId);

    int addSubscribe(ReportSubscribe subscribe);

    int updateSubscribe(ReportSubscribe subscribe);

    int removeSubscribes(Long[] subscribeIds);

    Map<String, Object> testPush(Long subscribeId);

    List<ReportPushLog> selectPushLogList(ReportPushLog query);

    ReportPushLog getPushLog(Long logId);
}
