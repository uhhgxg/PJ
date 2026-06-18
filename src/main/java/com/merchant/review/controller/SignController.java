package com.merchant.review.controller;


import com.merchant.review.dto.Result;
import com.merchant.review.service.ISignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "签到")
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
        log.info("【请求】用户签到");
        return signService.sign();
    }

    /**
     * 获取本月签到记录
     */
    @GetMapping
    public Result getSignRecords() {
        log.info("【请求】查询本月签到记录");
        return signService.getSignRecords();
    }
}
