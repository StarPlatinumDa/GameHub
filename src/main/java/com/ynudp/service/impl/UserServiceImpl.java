package com.ynudp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.entity.User;
import com.ynudp.mapper.UserMapper;
import com.ynudp.service.UserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
