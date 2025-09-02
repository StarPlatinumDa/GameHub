package com.ynudp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ynudp.dto.Result;
import com.ynudp.entity.VoucherOrder;
import com.ynudp.mapper.SeckillVoucherMapper;
import com.ynudp.mapper.VoucherOrderMapper;
import com.ynudp.service.SeckillVoucherService;
import com.ynudp.service.VoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ynudp.utils.RedisIdWorker;
import com.ynudp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private SeckillVoucherService seckillVoucherService;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    // 线程池 只给一个线程执行任务即可
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();

    // 在主线程中提前获取代理对象以便子线程使用
    private VoucherOrderService proxy;


    // 创建内部类，作为线程任务  从redis消息队列中获取信息
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取redis消息队列中的订单信息 XREADFROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders > 最近一条未消费信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.如果获取失败，说明没有消息，继续下一次循环
                    if(list==null||list.isEmpty()){
                        continue;
                    }
                    // 3.如果获取成功,解析list获得数据，然后数据库下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    // getId获取的是消息的id，用于确认
                    RecordId id = record.getId();
                    // getValue是获取消息
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = new VoucherOrder();
                    BeanUtil.fillBeanWithMap(values,voucherOrder,true);

                    handleVoucherOrder(voucherOrder);

                    // 4.给redis消息队列确认信息ACK  SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",id);
                } catch (Exception e) {
                    log.error("异步处理redis消息队列订单异常",e);
                    handlePendingList();
                }

            }
        }
        // 处理Pending-list中的异常信息(即主消息队列中被读取了却未确认的信息)
        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取PendingList中的订单信息 XREADFROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders 0 读pendinglist中的第一条消息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),// pendinglist读不到不阻塞，说明没有异常
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );

                    // 2.如果获取失败，说明pendingList没有异常信息，结束循环
                    if(list==null||list.isEmpty()){
                        break;
                    }
                    // 3.如果获取成功,解析list获得数据，然后数据库下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    // getId获取的是消息的id，用于确认
                    RecordId id = record.getId();
                    // getValue是获取消息
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = new VoucherOrder();
                    BeanUtil.fillBeanWithMap(values,voucherOrder,true);
                    handleVoucherOrder(voucherOrder);
                    // 4.给redis消息队列确认信息ACK  SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",id);

                }catch (Exception e) {
                    log.error("异步处理pending-list订单异常",e);
                    // 这里不用递归调用自己，因为有while(true)会循环
                    try {
                        // 休眠一会 以免异常后再次循环太频繁
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }
    }


//    // 创建内部类，作为线程任务  从阻塞队列中获取信息
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true){
//                // 1.获取队列中的订单信息 因为用了阻塞队列，取不到数据会阻塞，所以while不会有巨大消耗
//                try {
//                    VoucherOrder order = orderTasks.take();
//                    // 2.创建订单
//                    handleVoucherOrder(order);
//
//
//                } catch (Exception e) {
//                    log.error("异步处理订单异常",e);
//                }
//
//            }
//        }
//    }
    // 异步创建订单调用的创建订单函数
    private void handleVoucherOrder(VoucherOrder order) {
        Long voucherId = order.getVoucherId();
        Long userId = order.getUserId();
        Long orderId = order.getId();
        // redisson解决可重入，锁重试和主从一致性问题
        // 创建锁对象
        // 理论上前面分离出的抢单逻辑已经移到redis中可以判断一人一单了，而锁就是为了一人一单，所以这里可以不需要的(冗余)
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock){
            // 获取锁失败，返回错误
            log.error("请勿重复下单!");
        }
        // 获取事务的代理对象，当前VoucherOrderServiceImpl类的代理对象
        // 同时异步获取代理对象获取不到(子线程ThreadLocal中获取不到父线程的)，所以放到外面获取
        try {
            // 异步创建订单和减库存
            proxy.createVoucherOrderAsyn(order);
        }finally {
            // 释放锁
            lock.unlock();
        }
    }

    @PostConstruct// 在类初始化完毕时执行
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    // 提前通过io流获取lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    // 静态常量在静态代码块中初始化
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 提前通过io流获取lua脚本 消息队列版
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT_QUE;
    // 静态常量在静态代码块中初始化
    static {
        SECKILL_SCRIPT_QUE = new DefaultRedisScript<>();
        SECKILL_SCRIPT_QUE.setLocation(new ClassPathResource("seckillque.lua"));
        SECKILL_SCRIPT_QUE.setResultType(Long.class);
    }

    /**
     * 优惠券秒杀下单 返回订单id  抢单判断和数据库下单分开,同时消息队列从jvm移到redis的Stream数据结构中
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本 在redis中得知是否有购买资格 同时把下单信息保存到阻塞队列  优惠券id，用户id，订单id
        // 没有key所以传入一个空集合  这里redis的库存的读写都是用StringRedisTemplate避免key或value的不一致(因为用lua读取的缘故，value必须为string)
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT_QUE, Collections.emptyList(), voucherId.toString(), userId.toString(), String.valueOf(orderId));
//        Long result = (Long) redisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        // 2.判断结果是否为0

        if(result!=0){
            // 2.1不为0，没有购买资格
            return Result.fail(result==1?"库存不足":"不能重复下单");
        }

        // 提前在主线程中获取代理对象，便于new子线程中能用
        proxy = (VoucherOrderService) AopContext.currentProxy();

        // 4.直接返回订单id
        return Result.success(orderId);
    }



    /**
     * 优惠券秒杀下单 返回订单id  优化版本，把抢单判断和下单分开
     * @param voucherId
     * @return
     */
//    public Result seckillVoucher(Long voucherId) {
//        // 1.执行lua脚本 在redis中得知是否有购买资格
//        Long userId = UserHolder.getUser().getId();
//        // 没有key所以传入一个空集合  这里redis的库存的读写都是用StringRedisTemplate避免key或value的不一致(因为用lua读取的缘故，value必须为string)
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
////        Long result = (Long) redisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
//        // 2.判断结果是否为0
//
//        if(result!=0){
//            // 2.1不为0，没有购买资格
//            return Result.fail(result==1?"库存不足":"不能重复下单");
//        }
//
//        // 2.2为0，有购买资格，把下单信息保存到阻塞队列
//        // 把下单信息保存到阻塞队列  订单id，用户id，优惠券id
//        long orderId = redisIdWorker.nextId("order");
//        // 3.异步进行数据库下单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 需要填写1.订单id 2.用户id 3.代金券id  注意：订单id是我们手动生成的，而不是自增
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//
//        // 提前在主线程中获取代理对象，便于new子线程中能用
//        proxy = (VoucherOrderService) AopContext.currentProxy();
//        // 创建阻塞队列：当线程尝试从队列中获取元素时，如果队列为空，线程就会被阻塞，直到队列中有元素才会被唤醒
//        orderTasks.add(voucherOrder);
//
//        // 4.直接返回订单id
//        return Result.success(orderId);
//    }





//    /**
//     * 优惠券秒杀下单 返回订单id
//     * @param voucherId
//     * @return
//     */
//    // 把事务放在抽取的方法中
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券是否存在
//        // 不存在，直接返回错误
//        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
//        if (seckillVoucher == null){
//            return Result.fail("秒杀优惠券不存在!");
//        }
//
//        LocalDateTime now = LocalDateTime.now();
//        // 优惠券存在，2.判断是否在秒杀时间段内
//        if (now.isBefore(seckillVoucher.getBeginTime()) || now.isAfter(seckillVoucher.getEndTime())){
//            // 不在时间段内，返回不在时间段内
//            return Result.fail("秒杀优惠券不在使用时间段内!");
//        }
//
//        // 在时间段内，3.判断库存是否充足
//        if (seckillVoucher.getStock()<=0){
//            // 不充足，返回库存不足
//            return Result.fail("秒杀优惠券库存不足!");
//        }
//        // 新增：一人一单  锁在调用的外面，避免锁在内部(会导致锁释放了，方法的事务还没有提交，即数据库未修改，新的线程来了查到的还是旧值，并发安全问题)
//        // 同时把事务也加在抽取出的方法上，因为该方法涉及两个数据库的修改
//        Long userId = UserHolder.getUser().getId();
////        // 单服务写法
////        synchronized (userId.toString().intern()) {
////            // 获取事务的代理对象，当前VoucherOrderServiceImpl类的代理对象
////            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        // 集群或微服务写法  给当前用户id上锁保证一个用户只能下一单
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
//          // redisson解决可重入，锁重试和主从一致性问题
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
////        boolean isLock = lock.tryLock(5L);
//
//        boolean isLock = lock.tryLock();
//
//
//        if (!isLock){
//            // 获取锁失败，返回错误
//            return Result.fail("请勿重复下单!");
//        }
//        // 获取事务的代理对象，当前VoucherOrderServiceImpl类的代理对象
//        try {
//            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            // 释放锁
////            lock.unLock();
//            lock.unlock();
//        }
//    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 每次线程调用得到的userId对象是新建的，toString方法也是new的字符串
        // 所以最后用intern返回字符串对象规范表示，在字符串池中找(equals方法不同才会加入新的)
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count>0){
            // 存在，返回重复下单
            return Result.fail("用户已经购买过该优惠券!");
        }
        // 充足，4.扣减库存 必须用stock=stock-1的写法，数据库保证原子性，否则会出现并发问题(不这么写库存就是之前查出来的，跟现在的操作之间有空隙)
        // 乐观锁，只有库存与查询相同才更新(避免中途有其他线程操作库存)
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success){
            return Result.fail("秒杀优惠券库存不足!");
        }
        // 5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 需要填写1.订单id 2.用户id 3.代金券id  注意：订单id是我们手动生成的，而不是自增
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrderMapper.insert(voucherOrder);
        // 返回id
        return Result.success(orderId);
    }

    // 异步创建订单和减库存
    @Transactional
    public void createVoucherOrderAsyn(VoucherOrder voucherOrder) {
        // 每次线程调用得到的userId对象是新建的，toString方法也是new的字符串
        // 所以最后用intern返回字符串对象规范表示，在字符串池中找(equals方法不同才会加入新的)
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 查询订单是否已存在，但一人一单逻辑已经外移到redis，这里不可能出现重复，查询为冗余代码
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count>0){
            // 存在，返回重复下单
            log.error("用户已经购买过该优惠券!");
        }
        // 充足，4.扣减库存 必须用stock=stock-1的写法，数据库保证原子性，否则会出现并发问题(不这么写库存就是之前查出来的，跟现在的操作之间有空隙)
        // 乐观锁，只有库存与查询相同才更新(避免中途有其他线程操作库存)
        boolean success = seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success){
            // redis判断过，这里也不太可能出现不足的情况
            log.error("秒杀优惠券库存不足!");
        }
        // 创建订单
        voucherOrderMapper.insert(voucherOrder);
    }
}
