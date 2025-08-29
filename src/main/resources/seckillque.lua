-- 1.确定参数列表   优惠券id，用户id   和消息队列的 订单id

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

-- 2.数据key 库存key，订单key  lua使用..拼接
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务逻辑
-- 3.1 判断库存是否充足 取出来的是string，所以用tonumber转成数字
if(tonumber(redis.call('get',stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end
-- 3.2 判断用户是否已经下过单 sismember 判断用户id是否在集合中
if(redis.call('sismember',orderKey,userId)==1) then
    -- 用户已经下过单，返回2
    return 2
end

-- 3.3 扣库存
redis.call('incrby',stockKey,-1)
-- 3.4 把用户id记录到set集合中，完成下单
redis.call('sadd',orderKey,userId)
-- 3.5 发送消息到消息队列中 XADD stream.orders * k1 v1 k2 v2   *表示id不自己指定，而是redis生成
redis.call('xadd','stream.orders','*','id',orderId,'voucherId',voucherId,'userId',userId)
return 0

