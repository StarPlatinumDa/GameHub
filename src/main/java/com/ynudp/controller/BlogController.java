package com.ynudp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ynudp.dto.Result;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.Blog;
import com.ynudp.service.BlogService;
import com.ynudp.utils.SystemConstants;
import com.ynudp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogService blogService;


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        log.info("保存博文");
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        log.info("点赞博文,{}",id);
        return blogService.likeBLog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.success(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result getBlogById(@PathVariable Long id){
        log.info("查看博文,{}",id);
        return blogService.getBlogById(id);
    }

    /**
     * 查询博文点赞的TopN用户集合
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable Long id) {
        log.info("查询博文点赞的TopN用户集合,{}",id);
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询某个用户发布的所有博文
     * @param current 当前页 默认从第一页开始
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询 从第一页开始，每页最多10条
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.success(records);
    }

    /**
     * 分页查询某个用户  所关注的用户所发布的博文
     * @param lastId
     * @param offset
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(Long lastId,@RequestParam(defaultValue = "0")Integer offset){
        log.info("分页查询某个用户所关注的用户所发布的博文,偏移量为{}",offset);
        return blogService.queryBlogOfFollow(lastId,offset);
    }



}
