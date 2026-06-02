package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
        // 分页查询一级评论（parent_id = 0）
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
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

        comment.setUserId(userId);
        comment.setLiked(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        save(comment);

        // 更新博客评论数 +1
        blogService.update()
                .setSql("comments = comments + 1")
                .eq("id", comment.getBlogId())
                .update();

        log.debug("用户 {} 评论博客 {} content={}", userId, comment.getBlogId(), comment.getContent());
        return Result.ok(comment);
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        Long userId = UserHolder.getUser().getId();
        if (!comment.getUserId().equals(userId)) {
            return Result.fail("只能删除自己的评论");
        }

        removeById(id);

        // 更新博客评论数 -1
        blogService.update()
                .setSql("comments = comments - 1")
                .eq("id", comment.getBlogId())
                .update();

        log.debug("用户 {} 删除评论 {}", userId, id);
        return Result.ok();
    }

    @Override
    public Result queryReplies(Long blogId, Long parentId, Integer current) {
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("parent_id", parentId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
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
