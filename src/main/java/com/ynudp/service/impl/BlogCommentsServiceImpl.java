package com.ynudp.service.impl;

import com.ynudp.entity.BlogComments;
import com.ynudp.mapper.BlogCommentsMapper;
import com.ynudp.service.BlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

}
