package com.ynudp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ynudp.dto.Result;
import com.ynudp.dto.ScrollResult;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.Blog;
import com.ynudp.entity.Follow;
import com.ynudp.entity.User;
import com.ynudp.mapper.BlogMapper;
import com.ynudp.mapper.FollowMapper;
import com.ynudp.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.service.FollowService;
import com.ynudp.service.UserService;
import com.ynudp.utils.SystemConstants;
import com.ynudp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
    @Resource
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private FollowService followService;

    @Autowired
    private BlogMapper baseMapper;
    @Autowired
    private BlogMapper blogMapper;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
            isBLogLiked(blog);
        });
        return Result.success(records);
    }

    /**
     * 抽取方法，查询博客的作者并赋值给blog
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result getBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog的作者
        queryBlogUser(blog);
        // 3.新增，查询blog是否被当前用户点赞过
        isBLogLiked(blog);

        return Result.success(blog);
    }

    private void isBLogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user==null){
            // 用户未登录时，无需查看是否被点赞
            return;
        }
        Long userId = user.getId();
        Long blogId = blog.getId();
        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        Double score = zSetOperations.score("blog:liked:" + blogId, userId.toString());
        if (score!=null){
            blog.setIsLike(true);
        }else blog.setIsLike(false);
    }

    @Override
    public Result likeBLog(Long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 判断当前用户是否已经点赞
        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        Double score = zSetOperations.score("blog:liked:" + id, userId.toString());
        // 如果未点赞，可以点赞  1.数据库点赞数量加一  2.redis set保存用户
        if(score==null){
            update().setSql("liked = liked + 1").eq("id", id).update();
            // zadd key score member
            zSetOperations.add("blog:liked:" + id, userId.toString(), System.currentTimeMillis());
        }else {
            // 如果已经点赞，取消点赞 1.数据库点赞数量减一  2.同时redis set移除当前用户
            boolean isSuccesss = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccesss){
                zSetOperations.remove("blog:liked:" + id, userId.toString());
            }
        }
        return Result.success();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询top5 zrange key 0 4 查询出的是最小的5个元素(从小到大排序)  即先点赞的在前
        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        Set<String> top5 = zSetOperations.range("blog:liked:" + id, 0, 4);
        if (top5==null||top5.isEmpty()){
            return Result.success(new ArrayList<>());
        }
        // 解析出用户id String转化成Long
        List<Long> ids = top5.stream().map(string -> Long.valueOf(string)).toList();
        // 通过userId获取用户
        String join = StrUtil.join(",", ids);
        // 查询使用in时会自动按id排序，为了返回原来的顺序，得用where id in (id1,id2,id3) order by field(id,id1,id2,id3)
        List<User> users = userService.query().in("id",ids)
                .last("order by field(id,"+join+")").list();
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        // 返回用户列表
        return Result.success(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean save = save(blog);
        if (!save){
            return Result.fail("发布笔记失败！");
        }
        // 查询该博文作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        // 推送笔记id给粉丝
        for (Follow follow : follows) {
            Long followId = follow.getUserId();
            String key= "feed:"+followId;
            ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
            zSetOperations.add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.success(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long lastId, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱
        String key="feed:"+userId;
        // ZREVRANGEBYSCORE key lastId minTime WITHSCORE(返回值带上score) LIMIT offset 3(count，查几条)
        ZSetOperations<String, String> zSetOperations = stringRedisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> tuples = zSetOperations
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, 3);
        if (tuples==null||tuples.isEmpty()){
            return Result.success();
        }
        // 解析数据：blogId,score(时间戳),offset(上次查询最小score的个数)
        ArrayList<Long> ids = new ArrayList<>();
        Long minTime=0L;
        int sum=1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            // 获取blogId
            String blogId = tuple.getValue();
            ids.add(Long.valueOf(blogId));
            // 获取分数
            long time = tuple.getScore().longValue();
            if(time==minTime){
                // 第一次重复才加(也就是2个)，所以初始化为1
                sum++;
            }else {
                minTime=time;
                sum=1;
            }
        }
        // 根据blogId查询blog,但mybatis批量查询是id排序的，要返回原顺序只能自己写
        String join = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id",ids).last("order by field(id,"+join+")").list();

        // 新增，每个blog还要额外显示作者信息和点赞数
        for (Blog blog : blogs) {
            // 2.查询blog的作者
            queryBlogUser(blog);
            // 3.新增，查询blog是否被当前用户点赞过
            isBLogLiked(blog);
        }

        // 封装返回笔记
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(sum);
        scrollResult.setMinTime(minTime);
        return Result.success(scrollResult);
    }
}
