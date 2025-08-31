package com.ynudp.controller;


import com.ynudp.dto.Result;
import com.ynudp.mapper.FollowMapper;
import com.ynudp.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/follow")
@Slf4j
public class FollowController {
    @Autowired
    private FollowService followService;


    /**
     * 关注或取关
     * @param id
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow){
        log.info("关注或取关,{},{}",id,isFollow);

        return followService.follow(id, isFollow);
    }

    /**
     * 查询当前用户是否关注id的用户
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable Long id){
        log.info("查询是否关注,{}",id);
        return followService.isFollow(id);
    }

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commenFollows(@PathVariable Long id){
        return followService.commenFollows(id);
    }
}
