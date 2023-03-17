----key[1] 锁的key; ARGV[1] 当前线程表示  获取锁的value，判断是否与当前线程标识一致
--if (redis.call('GET', KEYS[1]) ==  ARGV[1]) then
--    return redis.call('DEL', KEYS[1])
--end
--return 0

-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示一致
if (redis.call('GET', KEYS[1]) == ARGV[1]) then
    -- 一致，则删除锁
    return redis.call('DEL', KEYS[1])
end
-- 不一致，则直接返回
return 0