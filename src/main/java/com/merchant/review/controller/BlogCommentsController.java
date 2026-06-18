package com.merchant.review.controller;

import com.merchant.review.dto.Result;
import com.merchant.review.entity.BlogComments;
import com.merchant.review.service.IBlogCommentsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Slf4j
@Tag(name = "博客评论")
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 分页查询博客的一级评论
     */
    @GetMapping("/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("【请求】查询博客评论，博客ID：{}，第{}页", blogId, current);
        return blogCommentsService.queryComments(blogId, current);
    }

    /**
     * 分页查询某条评论的回复
     */
    @GetMapping("/{blogId}/replies/{parentId}")
    public Result queryReplies(@PathVariable("blogId") Long blogId,
                               @PathVariable("parentId") Long parentId,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        log.info("【请求】查询评论回复，博客ID：{}，父评论ID：{}，第{}页", blogId, parentId, current);
        return blogCommentsService.queryReplies(blogId, parentId, current);
    }

    /**
     * 添加评论
     */
    @PostMapping
    public Result addComment(@RequestBody BlogComments comment) {
        log.info("【请求】添加评论，博客ID：{}，内容：{}", comment.getBlogId(), comment.getContent());
        return blogCommentsService.addComment(comment);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        log.info("【请求】删除评论，评论ID：{}", id);
        return blogCommentsService.deleteComment(id);
    }
}
