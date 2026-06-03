package com.merchant.review.service;

import com.merchant.review.dto.Result;
import com.merchant.review.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 博客评论服务接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /** 分页查询博客的一级评论（按时间倒序） */
    Result queryComments(Long blogId, Integer current);

    /** 添加评论（自动填充用户ID，更新博客评论数） */
    Result addComment(BlogComments comment);

    /** 删除评论（仅评论作者可删，更新博客评论数） */
    Result deleteComment(Long id);

    /** 分页查询某条评论的回复 */
    Result queryReplies(Long blogId, Long parentId, Integer current);
}
