package com.ynudp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j// 是为了自定义序列化方式和覆盖 Spring Boot 默认的 RedisTemplate 行为
// 如果不需要自定义序列化方式，则不需要写这个类
public class RedisConfiguration {
    @Bean // 使得类上的参数自动被注入   同时自身的作用是将方法的返回对象直接注册为Bean,由容器管理
    public RedisTemplate redisTemplate(RedisConnectionFactory connectionFactory){
        log.info("开始创建redis模板对象...");
        RedisTemplate redisTemplate = new RedisTemplate();
        // 设置redis连接工厂对象(不需要自己创建，是maven引入的starter创建好放入容器中的  只需要声明注入即可)
        redisTemplate.setConnectionFactory(connectionFactory);
        // 设置redis key的序列化器  不设置的话会用默认的jdk redis序列化器,但会导致图形化界面里的key不同于key本身(多一段序列化的码)
        redisTemplate.setKeySerializer(new StringRedisSerializer());

//        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
