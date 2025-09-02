package com.ynudp;

import com.ynudp.entity.Shop;
import com.ynudp.service.ShopService;
import com.ynudp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class GameHubApplicationTests {
    @Autowired
    private ShopService shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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


    @Test
    void loadShopData(){
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺按照typeId分组，分类相同的放到同一个集合   即形成一个Map<Long,List<Shop>>
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        // 3.分批写入Redis
        GeoOperations<String, String> geoOperations = stringRedisTemplate.opsForGeo();
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key= "shop:geo:"+typeId;
            List<Shop> shops = entry.getValue();
            // redis GEOADD key longitude latitude member
//            for (Shop shop : shops) {
//                geoOperations.add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
//            }

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            geoOperations.add(key,locations);
        }
    }


    @Test
    void testHyperLogLog(){
        HyperLogLogOperations<String, String> operations = stringRedisTemplate.opsForHyperLogLog();

        String[] values = new String[1000];
        int j;
        // 插入100万条数据
        for (int i = 0; i < 1000000; i++) {
            j=i%1000;
            values[j]="user_"+i;
            if (j==999){
                // 每次填满就发送给redis
                operations.add("hyperlog2",values);
            }
        }
        // 统计数量
        Long count = operations.size("hyperlog2");
        // count = 997593
        System.out.println("count = " + count);
    }


}
