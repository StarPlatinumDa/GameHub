package com.ynudp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest// redisson联锁MultiLock
public class RedissonMultiLockTest {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedissonClient redissonClient2;
    @Autowired
    private RedissonClient redissonClient3;

    private RLock lock;

    @BeforeEach// 标识为初始化方法，在@Test等操作之前执行
    void setUp() throws InterruptedException {
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");
        // 创建联锁 MultiLock  用哪个client.get都一样，内部是new RedissonMultiLock(locks)
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);

        // 后续使用就和以前一样
        lock.tryLock(1L, TimeUnit.SECONDS);

        lock.unlock();
    }
}
