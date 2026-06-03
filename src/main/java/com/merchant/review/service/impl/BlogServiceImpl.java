package com.merchant.review.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.ScrollResult;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.Blog;
import com.merchant.review.entity.Follow;
import com.merchant.review.entity.User;
import com.merchant.review.mapper.BlogMapper;
import com.merchant.review.service.IBlogService;
import com.merchant.review.service.IFollowService;
import com.merchant.review.service.IUserService;
import com.merchant.review.utils.RedisConstants;
import com.merchant.review.utils.SystemConstants;
import com.merchant.review.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.merchant.review.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.merchant.review.utils.RedisConstants.FEED_KEY;

@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    private static final DefaultRedisScript<Long> BLOG_RANK_SCRIPT;
    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("blog_rank.lua"));
        script.setResultType(Long.class);
        BLOG_RANK_SCRIPT = script;
    }

    // ======================== 热门博客 / 查询 ========================

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    // ======================== 发布 + Feed 流 ========================

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败!");
        }
        // 推送笔记id给所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    // ======================== 点赞相关 ========================

    @Override
    public Result likeBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        String likedSetKey = RedisConstants.BLOG_LIKED_KEY + id;
        String rankKey     = RedisConstants.BLOG_RANK_LIKES_KEY;

        Long result = stringRedisTemplate.execute(
                BLOG_RANK_SCRIPT,
                Arrays.asList(likedSetKey, rankKey),
                userId.toString(),
                String.valueOf(id)
        );

        if (result == null) {
            log.error("点赞 Lua 脚本执行异常 blogId={} userId={}", id, userId);
            return Result.fail("操作失败，请重试");
        }

        boolean isLiked = result > 0;
        try {
            if (isLiked) {
                update().setSql("liked = liked + 1").eq("id", id).update();
            } else {
                update().setSql("liked = liked - 1").eq("id", id)
                        .gt("liked", 0)
                        .update();
            }
        } catch (Exception e) {
            log.error("DB 点赞数更新失败（定时同步可修复） blogId={} isLiked={}", id, isLiked, e);
        }

        return Result.ok(isLiked);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryTop5Liked() {
        Set<ZSetOperations.TypedTuple<String>> top5 = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(RedisConstants.BLOG_RANK_LIKES_KEY, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }
        List<Blog> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : top5) {
            String blogIdStr = tuple.getValue();
            if (blogIdStr == null) continue;
            Long blogId = Long.valueOf(blogIdStr);
            int likeCount = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
            Blog blog = getById(blogId);
            if (blog == null) {
                log.warn("Redis ZSet 中笔记 {} 在 DB 中不存在，跳过", blogId);
                continue;
            }
            blog.setLiked(likeCount);
            User publisher = userService.getById(blog.getUserId());
            if (publisher != null) {
                blog.setName(publisher.getNickName());
                blog.setIcon(publisher.getIcon());
            }
            fillIsLike(blog);
            result.add(blog);
        }
        return Result.ok(result);
    }

    private void fillIsLike(Blog blog) {
        try {
            UserDTO user = UserHolder.getUser();
            if (user != null) {
                String likedKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
                Boolean isLiked = stringRedisTemplate.opsForSet().isMember(likedKey, user.getId().toString());
                blog.setIsLike(Boolean.TRUE.equals(isLiked));
            }
        } catch (Exception e) {
            log.warn("查询点赞状态失败 blogId={}", blog.getId(), e);
            blog.setIsLike(false);
        }
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncBlogLikesToRedis() {
        log.info("========== 开始同步博客点赞数据到 Redis ZSet ==========");
        try {
            List<Blog> blogs = query().select("id", "liked").list();
            String rankKey = RedisConstants.BLOG_RANK_LIKES_KEY;
            for (Blog blog : blogs) {
                if (blog.getLiked() != null && blog.getLiked() > 0) {
                    stringRedisTemplate.opsForZSet()
                            .add(rankKey, blog.getId().toString(), blog.getLiked());
                } else {
                    stringRedisTemplate.opsForZSet()
                            .remove(rankKey, blog.getId().toString());
                }
            }
            log.info("========== 点赞数据同步完成，共处理 {} 条笔记 ==========", blogs.size());
        } catch (Exception e) {
            log.error("同步点赞数据到 Redis 失败", e);
        }
    }

    // ======================== 辅助方法 ========================

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return;
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
