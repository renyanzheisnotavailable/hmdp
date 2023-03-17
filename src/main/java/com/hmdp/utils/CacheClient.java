package com.hmdp.utils;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate =redisTemplate;
    }

    /**
     * 将任意java对象序列化为json并储存在string类型的key中，并且可以设置ttl
     *
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 将任意java对象序列化为json并储存在string类型的key中，并且可以设置逻辑过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public <T> void setWithLogicExpire(String key, T value, Long time, TimeUnit timeUnit) {
        RedisData<T> tRedisData = new RedisData<>(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)), value);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(tRedisData));
    }


    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param prefix redis缓存中的id前缀
     * @param id redis缓存的id
     * @param type 查询出的类型
     * @param dbFallback 数据库查询逻辑
     * @param time 数据库新查找数据存储在redis的时间
     * @param timeUnit 时间单位
     * @param <R> 返回类型
     * @param <ID> id类型
     * @return
     */
    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R>dbFallback, Long time, TimeUnit timeUnit) {
        String key = prefix + id;
        //redis中查询
        String json = redisTemplate.opsForValue().get(key);
        //存在返回
        if (json != null) {
            return JSONUtil.toBean(json, type);
        }
        //不存在 查数据库
        R r = dbFallback.apply(id);
        //数据库不存在，缓存空值
        if (r == null) {
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        //数据库存在 添加缓存并返回
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * 互斥锁-上锁
     * @param key
     * @return
     */
    public boolean trylock(String key) {
        return BooleanUtil.isTrue(redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES));
    }

    /**
     * 互斥锁-解锁
     * @param key
     * @return
     */
    public boolean unlock(String key) {
        return redisTemplate.delete(key);
    }


    /**
     * 线程池
     */
    private ExecutorService CACHE_REBUILD_EXECUTOR =
            new ThreadPoolExecutor(10 , 200, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(1024), new ThreadFactoryBuilder().setNamePrefix("")
                    .build(), new ThreadPoolExecutor.AbortPolicy());

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题。
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithExpire(
            String prefix, ID id, Class<R> type, Function<ID, R>DbFallback, Long time, TimeUnit timeUnit){
        //1.在redis中查找
        String key = prefix + id;
        String json = redisTemplate.opsForValue().get(key);
        RedisData<R> r = JSONUtil.toBean(json, RedisData.class);

        //2.判断是否过期
        // 2.1未过期 返回
        if (r.getExpireTime().isAfter(LocalDateTime.now())) {
            return r.getData();
        }
        //2.2过期
        //3.1获取锁
        if (trylock(key)) {
            //3.1.1 成功 缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = DbFallback.apply(id);
                    this.setWithLogicExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    unlock(key);
                }
            });
        }
        //3.1.2 失败 已有线程进行缓存重建，直接返回
        return r.getData();
    }
}
