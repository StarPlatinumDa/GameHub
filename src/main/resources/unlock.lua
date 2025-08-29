-- redis.call是lua调用redis的接口
-- 比较锁中的标识与当前线程存的 是否一致
if redis.call('get', KEYS[1]) == ARGV[1] then
    -- 释放锁
    return redis.call('del', KEYS[1])
end
return 0