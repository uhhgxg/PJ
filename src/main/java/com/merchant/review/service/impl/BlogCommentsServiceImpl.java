package com.merchant.review.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.BlogComments;
import com.merchant.review.entity.User;
import com.merchant.review.mapper.BlogCommentsMapper;
import com.merchant.review.service.IBlogCommentsService;
import com.merchant.review.service.IBlogService;
import com.merchant.review.service.IUserService;
import com.merchant.review.utils.SystemConstants;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 博客评论服务实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;
    @Resource
    private IBlogService blogService;

    @Override
    public Result queryComments(Long blogId, Integer current) {
        log.info("【查评论】查询博客一级评论，博客ID：{}，第{}页", blogId, current);
        // 分页查询一级评论（parent_id = 0）
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
        log.info("【查评论】查到 {} 条一级评论", records.size());
        if (!records.isEmpty()) {
            populateUserInfo(records);
            populateReplyCount(records);
        }
        return Result.ok(page);
    }

    @Override
    @Transactional
    public Result addComment(BlogComments comment) {
        Long userId = UserHolder.getUser().getId();
        log.info("【添加评论】用户 {} 评论博客 {}，内容：{}", userId, comment.getBlogId(), comment.getContent());

        comment.setUserId(userId);
        comment.setLiked(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        save(comment);
        log.info("【添加评论】评论保存成功，评论ID：{}", comment.getId());

        // 更新博客评论数 +1
        blogService.update()
                .setSql("comments = comments + 1")
                .eq("id", comment.getBlogId())
                .update();
        log.info("【添加评论】博客 {} 评论数已更新", comment.getBlogId());

        return Result.ok(comment);
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        log.info("【删除评论】开始删除评论，评论ID：{}", id);
        BlogComments comment = getById(id);
        if (comment == null) {
            log.warn("【删除评论】评论不存在，ID：{}", id);
            return Result.fail("评论不存在");
        }

        Long userId = UserHolder.getUser().getId();
        if (!comment.getUserId().equals(userId)) {
            log.warn("【删除评论】无权限，用户 {} 不是评论 {} 的作者", userId, id);
            return Result.fail("只能删除自己的评论");
        }

        removeById(id);
        log.info("【删除评论】评论已删除，ID：{}", id);

        // 更新博客评论数 -1
        blogService.update()
                .setSql("comments = comments - 1")
                .eq("id", comment.getBlogId())
                .update();
        log.info("【删除评论】博客 {} 评论数已更新", comment.getBlogId());

        return Result.ok();
    }

    @Override
    public Result queryReplies(Long blogId, Long parentId, Integer current) {
        log.info("【查回复】查询评论回复，博客ID：{}，父评论ID：{}，第{}页", blogId, parentId, current);
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", parentId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
        log.info("【查回复】查到 {} 条回复", records.size());
        if (!records.isEmpty()) {
            populateUserInfo(records);
        }
        return Result.ok(page);
    }

    // ======================== 私有方法 ========================

    /** 填充评论作者的用户头像和昵称 */
    private void populateUserInfo(List<BlogComments> records) {
        Set<Long> userIds = records.stream().map(BlogComments::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) return;

        Map<Long, User> userMap = userService.listByIds(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (BlogComments comment : records) {
            User user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setIcon(user.getIcon());
                comment.setUserName(user.getNickName());
            }
        }
    }

    /** 填充一级评论的回复数量 */
    private void populateReplyCount(List<BlogComments> records) {
        for (BlogComments comment : records) {
            Long count = query().eq("parent_id", comment.getId()).count();
            comment.setReplyCount(count.intValue());
        }
    }
}
