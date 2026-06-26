package com.merchant.review.controller;

import com.merchant.review.dto.Result;
import com.merchant.review.service.ai.ReviewAiService;
import com.merchant.review.service.ai.ReviewAnalysisResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 评价智能化 Controller
 * <p>提供：评价情感分类 + 问题标签 + 差评整改建议 + 自动回复话术</p>
 * <p>注意：该接口为通用AI分析接口，商家端的评价管理请走 /merchant/reviews/**</p>
 */
@Slf4j
@Tag(name = "评价AI")
@RestController
@RequestMapping("/api/ai/review")
public class ReviewAiController {

    @Resource
    private ReviewAiService reviewAiService;

    @Operation(summary = "评价分析（情感+标签+差评整改+回复话术）")
    @PostMapping("/analyze")
    public Result analyze(@RequestBody Map<String, Object> body) {
        Object shopIdObj = body.get("shopId");
        Object contentObj = body.get("content");
        Long shopId = shopIdObj == null ? null : Long.valueOf(shopIdObj.toString());
        String content = contentObj == null ? "" : contentObj.toString();
        log.info("【请求】评价分析 shopId={} contentLen={}", shopId, content.length());
        ReviewAnalysisResult r = reviewAiService.analyze(shopId, content);
        return Result.ok(r);
    }
}
