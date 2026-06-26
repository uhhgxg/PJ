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
 * 商家端评价管理 Controller
 *
 * <p>提供商家对其门店评价的查看、AI分析、回复发布等功能。</p>
 */
@Slf4j
@Tag(name = "商家端评价管理")
@RestController
@RequestMapping("/merchant/reviews")
public class MerchantReviewController {

    @Resource
    private IReviewService reviewService;

    @Operation(summary = "查看本店评价列表（按情感筛选、分页）")
    @GetMapping("/{shopId}")
    public Result listReviews(
            @PathVariable("shopId") Long shopId,
            @RequestParam(value = "sentiment", required = false) String sentiment,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        log.info("【商家端】查询评价列表 shopId={} sentiment={} page={}", shopId, sentiment, current);
        return reviewService.queryShopReviews(shopId, sentiment, current, pageSize);
    }

    @Operation(summary = "统计评价情感分布")
    @GetMapping("/{shopId}/stats")
    public Result stats(@PathVariable("shopId") Long shopId) {
        return reviewService.queryShopReviews(shopId, null, 1, 1);
    }

    @Operation(summary = "AI分析单条评价")
    @PostMapping("/{id}/analyze")
    public Result analyzeReview(@PathVariable("id") Long id) {
        log.info("【商家端】AI分析评价 reviewId={}", id);
        return reviewService.analyzeReview(id);
    }

    @Operation(summary = "发布评价回复")
    @PutMapping("/{id}/reply")
    public Result replyReview(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String replyText = body.get("reply");
        log.info("【商家端】发布评价回复 reviewId={}", id);
        return reviewService.publishReply(id, replyText);
    }
}
