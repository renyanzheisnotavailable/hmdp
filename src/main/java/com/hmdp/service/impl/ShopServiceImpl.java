package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@AllArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate redisTemplate;
    private final CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        Shop shop = queryWithPassThrough(id);
        //互斥锁解决穿透击穿
        Shop shop1 = queryWithPassThrough(id);
        
        return Result.ok(shop);
        //todo springcache
    }

    /**
     * null值解决缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
//        String shopKey = CACHE_SHOP_KEY + id;
//        //从redis查缓存
//        String shopJson = redisTemplate.opsForValue().get(shopKey);
//        //存在
//        if(StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if(shopJson != null) {
//            return null;
//        }
//        //redis不存在 查找数据库
//        Shop shop = getById(id);
//        //数据库不存在 错误
//        if(shop == null){
//            //空值写入redis
//            redisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //存在 写入redis 返回
//        redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return null;
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, (m) -> getById(m), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;

    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        String lock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        //从redis查缓存
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        //存在
        if(StrUtil.isNotBlank(shopJson)){
            return null;
        }
        //是否是之前查询的空数据
        if(shopJson != null) {
            return null;
        }
        //redis不存在 查找数据库
        //有锁
        try {
            if(!trylock(lock)) {
                Thread.sleep(10);
                queryWithMutex(id);
            }
            //没锁
            shop = getById(id);
            //数据库不存在 错误
            if(shop == null){
                //空值写入redis
                redisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在 写入redis 返回
            redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            unlock(lock);
        }
        return shop;
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
     * 逻辑过期 缓存重建
     * @param id
     * @param expireSeconds
     */
    private void saveShop2redis(Long id, long expireSeconds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(expireSeconds), shop);
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

//    private  static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 线程池
     */
    private ExecutorService CACHE_REBUILD_EXECUTOR =
            new ThreadPoolExecutor(10 , 200, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(1024), new ThreadFactoryBuilder().setNamePrefix("")
                    .build(), new ThreadPoolExecutor.AbortPolicy());

    public Shop queryWithLogicExpire(Long id){
        //1.在redis中查找
        String key = CACHE_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(key);
        RedisData<Shop> shop = JSONUtil.toBean(shopJson, RedisData.class);

        //2.判断是否过期
        // 2.1未过期 返回
        if (shop.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop.getData();
        }
        //2.2过期
        //3.1获取锁
        if (trylock(key)) {
            //3.1.1 成功 缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->saveShop2redis(id, 20));
        }
        //3.1.2 失败 已有线程进行缓存重建，直接返回
        return shop.getData();
    }




    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
