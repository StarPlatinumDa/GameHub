package com.ynudp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.ynudp.dto.UserDTO;
import com.ynudp.entity.User;
import com.ynudp.utils.RedisConstants;
import com.ynudp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;



/**
 拦截器通常是通过 WebMvcConfigurer 的 addInterceptors 方法手动实例化的
 （例如 new LoginInterceptor()），而这种方式绕过了 Spring 的依赖注入。

  不用new，而是直接传入@Autowired的实例，就可以交给spring容器管理了
 */
// 拦截器，专用于在controller之前或之后执行逻辑
// 拦截器要生效需要在配置文件中配置
@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {


    @Autowired
    private RedisTemplate redisTemplate;


    // ctrl+i可以看哪些方法可以重写
    // 在controller执行之前校验登录信息
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.(从cookie中)获取Session
//        HttpSession session = request.getSession();

        log.info("拦截器2");

        log.info("请求:{}，{}",request.getMethod(),request.getRequestURI());


        // 改成获取token
        String token = request.getHeader("authorization");

        // 2.获取Session中的用户
//        UserDTO user = (UserDTO) session.getAttribute("user");
        // 改成用token从redis中获取用户
        HashOperations hashOperations = redisTemplate.opsForHash();

        Map<Object, Object> userMap = hashOperations.entries(RedisConstants.LOGIN_USER_KEY + token);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            // 4.不存在，拦截，返回401 未授权
            response.setStatus(401);
            return false;
        }

        // 从map还原成userDTO
        UserDTO userDTO = new UserDTO();
        // 不忽略转换过程的错误，直接抛出
        BeanUtil.fillBeanWithMap(userMap,userDTO,false);


        // 5.存在，把用户保存到ThreadLocal放行
        log.info("当前用户id为：{}", userDTO.getId());


        UserHolder.saveUser(userDTO);



        return true;
    }

    // 在controller执行之后执行,销毁对应的  避免内存泄漏
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄漏
        // 因为线程池，线程复用，线程池会复用线程，保存数据，数据会保存在ThreadLocal中，不清理的话，数据被其他用户看到，导致泄漏
        UserHolder.removeUser();

    }
}
