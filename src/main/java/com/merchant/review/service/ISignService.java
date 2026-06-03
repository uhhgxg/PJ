package com.merchant.review.service;

import com.merchant.review.dto.Result;

/**
 * 签到服务接口
 * <p>
 * 基于 Redis BitMap 实现高效月度签到追踪。
 * BitMap 以用户ID+年月为 key，每日 1 bit，月度聚合查询使用 BITFIELD 命令。
 */
public interface ISignService {

    /** 用户签到 */
    Result sign();

    /** 获取本月签到记录（签到日期列表、连续签到天数、总签到天数） */
    Result getSignRecords();
}
