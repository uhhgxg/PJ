package com.hmdp.service;

import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
import com.hmdp.dto.Result;

public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    /** 点赞/取消点赞笔记 */
    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);

    /** 查询点赞排行榜前 5 笔记（含笔记详情、发布者信息、当前用户点赞状态） */
    Result queryTop5Liked();

    /** 将 DB 点赞数同步到 Redis ZSet */
    void syncBlogLikesToRedis();
}
