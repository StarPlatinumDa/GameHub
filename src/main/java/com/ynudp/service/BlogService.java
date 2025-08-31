package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;


public interface BlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result getBlogById(Long id);

    Result likeBLog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long lastId, Integer offset);
}
