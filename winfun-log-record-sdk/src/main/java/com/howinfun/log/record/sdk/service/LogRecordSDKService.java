package com.howinfun.log.record.sdk.service;

import com.howinfun.log.record.sdk.entity.LogRecord;
import com.howinfun.log.record.sdk.pojo.ApiResult;

import java.util.List;

/**
 *
 * LogRecord Service
 * @author winfun
 * @date 2021/2/25 2:04 下午
 **/
public interface LogRecordSDKService {

    /***
     * 增加日志记录
     * @author winfun
     * @param logRecord logRecord
     * @return {@link ApiResult <Integer> }
     **/
    ApiResult<Integer> insertLogRecord(LogRecord logRecord);

    /**
     * 查询操作日志记录列表
     * @param businessName
     * @return
     */
    ApiResult<List<LogRecord>> queryLogRecord(String businessName);
}
