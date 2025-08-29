package com.ynudp;

import com.ynudp.service.ShopService;
import com.ynudp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class YnuDianPingApplicationTests {
    @Autowired
    private ShopService shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService= Executors.newFixedThreadPool(500);

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        // 因为线程是异步的，所以要获取开始结束时间就要异步计数器  总共就是生成100*300=30000个id
        CountDownLatch countDownLatch = new CountDownLatch(300);

        // 直接创建Runnable对象，而不是new Thread()  不立即启动线程，本质上是任务定义
        // 可以直接task.run()不创建新线程执行，也可以交给线程池执行
        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 每个线程执行完毕就计数一次
            countDownLatch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            // 任务提交300次
            executorService.submit(task);
        }

        // 等待所有线程执行完毕
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time："+(end-start));
    }


    @Test
    void testId() {
        System.out.println(redisIdWorker.nextId("order"));
    }

}
