package com.merchant.review.controller;

import com.merchant.review.dto.Result;
import com.merchant.review.service.IReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 用户端评价 Controller
 * <p>提供用户提交评价、查看评价等功能。</p>
 */
@Slf4j
@Tag(name = "用户评价")
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private IReviewService reviewService;

    @Operation(summary = "提交评价")
    @PostMapping
    public Result createReview(@RequestBody Map<String, Object> body) {
        Long shopId = body.get("shopId") == null ? null : Long.valueOf(body.get("shopId").toString());
        Integer rating = body.get("rating") == null ? null : Integer.valueOf(body.get("rating").toString());
        String content = body.get("content") == null ? "" : body.get("content").toString();
        log.info("【用户端】提交评价 shopId={} rating={}", shopId, rating);
        return reviewService.createReview(shopId, rating, content);
    }

    @Operation(summary = "查询某店铺的所有评价")
    @GetMapping("/shop/{shopId}")
    public Result getShopReviews(
            @PathVariable("shopId") Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        log.info("【用户端】查询店铺评价 shopId={} page={}", shopId, current);
        return reviewService.queryShopReviews(shopId, null, current, pageSize);
    }
}
