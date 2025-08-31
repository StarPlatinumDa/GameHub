package com.ynudp.service;

import com.ynudp.dto.Result;
import com.ynudp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface FollowService extends IService<Follow> {

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);

    Result commenFollows(Long id);
}
