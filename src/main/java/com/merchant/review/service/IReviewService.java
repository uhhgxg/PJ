package com.merchant.review.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.merchant.review.dto.Result;
import com.merchant.review.entity.Review;

public interface IReviewService extends IService<Review> {

    /**
     * 分页查询某店铺的评价列表，可按情感筛选
     */
    Result queryShopReviews(Long shopId, String sentiment, Integer current, Integer pageSize);

    /**
     * AI分析单条评价（调用AI后回写 sentiment/aiScore/aiTags/aiSuggestion）
     */
    Result analyzeReview(Long reviewId);

    /**
     * 商家发布回复（保存回复文本，标记已回复）
     */
    Result publishReply(Long reviewId, String replyText);

    /**
     * 创建新评价（用户端）
     */
    Result createReview(Long shopId, Integer rating, String content);
}
