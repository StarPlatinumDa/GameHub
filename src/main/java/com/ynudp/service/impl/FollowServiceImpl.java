package com.ynudp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ynudp.dto.Result;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.Follow;
import com.ynudp.entity.User;
import com.ynudp.mapper.FollowMapper;
import com.ynudp.mapper.UserMapper;
import com.ynudp.service.FollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private UserMapper userMapper;


    @Override
    public Result follow(Long id, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        // 获取当前用户id
        Long userId = user.getId();
        Follow follow = new Follow();
        // 当前用户是关注者
        follow.setUserId(userId);
        follow.setFollowUserId(id);
        // true说明是关注
        if(isFollow){
            followMapper.insert(follow);
        }else {
            remove(new QueryWrapper<Follow>()
                    .eq("user_id",userId).eq("follow_user_id",id));

        }
        return Result.success();
    }

    @Override
    public Result isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Follow follow = query().eq("user_id", userId).eq("follow_user_id", id).one();
        if (follow == null){
            return Result.success(false);
        }
        return Result.success(true);
    }

    @Override
    public Result commenFollows(Long id) {
        // SELECT follow_user_id FROM tb_follow WHERE user_id=1013 and follow_user_id in
        // (SELECT follow_user_id FROM tb_follow WHERE user_id=1012)
        Long userId = UserHolder.getUser().getId();
        List<Follow> list = query().select("follow_user_id").eq("user_id", userId)
                .inSql("follow_user_id", "SELECT follow_user_id FROM tb_follow WHERE user_id=" + id)
                .list();
//        System.out.println(list);
        if (list==null||list.isEmpty())return Result.success(new ArrayList<>());
        List<Long> ids = list.stream().map(follow -> follow.getFollowUserId()).toList();
        List<User> users = userMapper.selectBatchIds(ids);
        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();

        return Result.success(userDTOS);
    }
}
