package com.ynudp.config;

import com.ynudp.interceptor.LoginInterceptor;
import com.ynudp.interceptor.RefreshTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration // 配置类 将拦截器添加到这里
@Slf4j
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        // 默认order都为0，按照添加顺序执行  越小越先执行
        // 拦截所有请求刷新token(如果有)
        registry.addInterceptor(refreshTokenInterceptor).order(0);

        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",// 查询热点，与用户无关
                        "/shop/**",// shop无论是否登录都可以查询
                        "/shop-type/**",// 店铺类型无论是否登录都可以查询
                        "/upload/**",// 上传图片无论登录与否都可以查询
                        "/voucher/**", // 优惠券查询
                        "/error" // 不拦截所有错误信息，否则如果方法不存在，就会转到/error，然后被自定义拦截器拦截，导致真实的错误信息无法返回给前端
                ).order(1);

    }
}
