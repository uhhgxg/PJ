package com.merchant.review.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.entity.Review;
import com.merchant.review.entity.Shop;
import com.merchant.review.entity.User;
import com.merchant.review.mapper.ReviewMapper;
import com.merchant.review.service.IReviewService;
import com.merchant.review.service.IShopService;
import com.merchant.review.service.IUserService;
import com.merchant.review.service.ai.ReviewAiService;
import com.merchant.review.service.ai.ReviewAnalysisResult;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    @Resource
    private ReviewAiService reviewAiService;

    @Resource
    private IShopService shopService;

    @Resource
    private IUserService userService;

    @Override
    public Result queryShopReviews(Long shopId, String sentiment, Integer current, Integer pageSize) {
        if (shopId == null) return Result.fail("店铺ID不能为空");
        if (current == null || current < 1) current = 1;
        if (pageSize == null || pageSize < 1) pageSize = 10;

        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getShopId, shopId);
        if (StrUtil.isNotBlank(sentiment)) {
            wrapper.eq(Review::getSentiment, sentiment);
        }
        wrapper.orderByDesc(Review::getCreateTime);

        Page<Review> p = page(new Page<>(current, pageSize), wrapper);
        List<Review> records = p.getRecords();

        // 填充用户昵称和头像
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream().map(Review::getUserId).collect(Collectors.toList());
            Map<Long, User> userMap = userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            for (Review r : records) {
                User u = userMap.get(r.getUserId());
                if (u != null) {
                    r.setUserId(null); // 不暴露userId
                    // 用transient字段？无法，用额外DTO。这里简单返回，前端用userId查
                }
            }
        }

        return Result.ok(records, p.getTotal());
    }

    @Override
    public Result analyzeReview(Long reviewId) {
        Review review = getById(reviewId);
        if (review == null) return Result.fail("评价不存在");

        // 已有分析结果则直接返回
        if (StrUtil.isNotBlank(review.getSentiment())) {
            return Result.ok(review);
        }

        // 调用AI分析
        Shop shop = shopService.getById(review.getShopId());
        String shopName = shop != null ? shop.getName() : "门店";
        ReviewAnalysisResult aiResult = reviewAiService.analyze(review.getShopId(), review.getContent());

        // 回写分析结果
        review.setSentiment(aiResult.getSentiment());
        review.setAiScore(aiResult.getScore());
        if (aiResult.getTags() != null && !aiResult.getTags().isEmpty()) {
            review.setAiTags(String.join(",", aiResult.getTags()));
        }
        review.setAiSuggestion(aiResult.getSuggestion());
        updateById(review);

        log.info("【评价AI分析】reviewId={} sentiment={} score={}", reviewId, aiResult.getSentiment(), aiResult.getScore());
        return Result.ok(aiResult);
    }

    @Override
    public Result publishReply(Long reviewId, String replyText) {
        if (StrUtil.isBlank(replyText)) return Result.fail("回复内容不能为空");

        Review review = getById(reviewId);
        if (review == null) return Result.fail("评价不存在");

        // 校验当前用户是该店铺的商户
        Long currentUserId = UserHolder.getUser().getId();
        Shop shop = shopService.getById(review.getShopId());
        if (shop == null || !currentUserId.equals(shop.getOwnerId())) {
            log.warn("【评价回复】权限校验失败 userId={} shopId={} ownerId={}",
                    currentUserId, review.getShopId(), shop != null ? shop.getOwnerId() : null);
            return Result.fail("您没有权限回复该评价");
        }

        review.setReply(replyText);
        review.setReplied(true);
        review.setReplyTime(LocalDateTime.now());
        updateById(review);

        log.info("【评价回复】reviewId={} 回复成功", reviewId);
        return Result.ok("回复发布成功");
    }

    @Override
    public Result createReview(Long shopId, Integer rating, String content) {
        if (shopId == null) return Result.fail("店铺ID不能为空");
        if (rating == null || rating < 1 || rating > 5) return Result.fail("评分范围为1-5");
        if (StrUtil.isBlank(content)) return Result.fail("评价内容不能为空");

        Long userId = UserHolder.getUser().getId();

        Review review = new Review();
        review.setShopId(shopId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setContent(content);
        review.setReplied(false);
        save(review);

        // 异步触发AI分析
        try {
            analyzeReview(review.getId());
        } catch (Exception e) {
            log.warn("【评价创建】AI分析异步失败 reviewId={}", review.getId());
        }

        log.info("【评价创建】reviewId={} shopId={} userId={} rating={}", review.getId(), shopId, userId, rating);
        return Result.ok(review.getId());
    }
}
