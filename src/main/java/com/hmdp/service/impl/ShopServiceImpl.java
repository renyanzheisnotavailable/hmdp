package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

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

    @Override
    public Result queryById(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis查缓存
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        //存在
        if(StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        if(shopJson != null) {
            return Result.fail("商铺不存在");
        }
        //redis不存在 查找数据库
        Shop shop = getById(id);
        //数据库不存在 错误
        if(shop == null){
            //空值写入redis
            redisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在");
        }
        //存在 写入redis 返回
        redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
        //todo springcache
    }

    public Shop queryWithPassThrough(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //从redis查缓存
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        //存在
        if(StrUtil.isNotBlank(shopJson)) {
            return null;
        }
        if(shopJson != null) {
            return null;
        }
        //redis不存在 查找数据库
        Shop shop = getById(id);
        //数据库不存在 错误
        if(shop == null){
            //空值写入redis
            redisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在 写入redis 返回
        redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return null;
    }

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

    public boolean trylock(String key) {
        return BooleanUtil.isTrue(redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES));
    }

    public boolean unlock(String key) {
        return redisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
