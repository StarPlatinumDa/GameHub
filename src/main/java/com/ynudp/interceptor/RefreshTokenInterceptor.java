package com.ynudp.interceptor;

import com.ynudp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 这个拦截器拦截所有路径，仅仅用于刷新token时间，确保用户一直在线
        log.info("拦截器1，用于刷新token时间");

        // 获取token
        String token = request.getHeader("authorization");
        if (token == null) {
            return true;// 如果没有token就直接放行
        }

        // 刷新token有效期  正常是30分钟，开发时用300避免频繁过期
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,300, TimeUnit.MINUTES);

        return true;
    }
}
