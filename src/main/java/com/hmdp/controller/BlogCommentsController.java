package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 博客评论前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 分页查询博客的一级评论
     *
     * @param blogId  博客 ID
     * @param current 页码，默认 1
     */
    @GetMapping("/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryComments(blogId, current);
    }

    /**
     * 分页查询某条评论的回复
     *
     * @param blogId   博客 ID
     * @param parentId 父评论 ID
     * @param current  页码，默认 1
     */
    @GetMapping("/{blogId}/replies/{parentId}")
    public Result queryReplies(@PathVariable("blogId") Long blogId,
                               @PathVariable("parentId") Long parentId,
                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryReplies(blogId, parentId, current);
    }

    /**
     * 添加评论
     *
     * @param comment 评论体（需包含 blogId, content，可选 parentId, answerId）
     */
    @PostMapping
    public Result addComment(@RequestBody BlogComments comment) {
        return blogCommentsService.addComment(comment);
    }

    /**
     * 删除评论（仅作者可操作）
     *
     * @param id 评论 ID
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }
}
