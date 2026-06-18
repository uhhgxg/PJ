package com.merchant.review.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.Blog;
import com.merchant.review.service.IBlogService;
import com.merchant.review.utils.SystemConstants;
import com.merchant.review.utils.UserHolder;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@Tag(name = "博客")
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        log.info("【请求】发布博客，标题：{}", blog.getTitle());
        Result result = blogService.saveBlog(blog);
        log.info("【响应】发布博客结果：{}", result);
        return result;
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        log.info("【请求】点赞博客，博客ID：{}", id);
        Result result = blogService.likeBlog(id);
        log.info("【响应】点赞结果：{}", result);
        return result;
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        log.info("【请求】查询我的博客，用户ID：{}，当前页：{}", user.getId(), current);
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        log.info("【响应】我的博客共 {} 条", records.size());
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("【请求】查询热门博客，当前页：{}", current);
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        log.info("【请求】查询博客详情，ID：{}", id);
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        log.info("【请求】查询博客点赞列表，博客ID：{}", id);
        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        log.info("【请求】查询用户博客，用户ID：{}，当前页：{}", id, current);
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        log.info("【响应】用户 {} 的博客共 {} 条", id, records.size());
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        log.info("【请求】查询关注者Feed流，lastId：{}，offset：{}", max, offset);
        return blogService.queryBlogOfFollow(max, offset);
    }

    @GetMapping("/top5")
    public Result queryTop5Liked() {
        log.info("【请求】查询点赞TOP5博客");
        return blogService.queryTop5Liked();
    }
}
