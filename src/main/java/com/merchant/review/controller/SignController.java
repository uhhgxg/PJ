package com.merchant.review.controller;

import com.merchant.review.dto.Result;
import com.merchant.review.service.ISignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 签到控制器
 * <p>
 * 基于 Redis BitMap 实现，支持签到与月度统计。
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /**
     * 签到
     */
    @PostMapping
    public Result sign() {
        return signService.sign();
    }

    /**
     * 获取本月签到记录
     *
     * @return 签到日期列表、连续签到天数、总签到天数
     */
    @GetMapping
    public Result getSignRecords() {
        return signService.getSignRecords();
    }
}
