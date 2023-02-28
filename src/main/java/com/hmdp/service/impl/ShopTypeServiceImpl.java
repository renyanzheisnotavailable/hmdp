package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        //查redis
//        String key = redisTemplate.opsForList(CACHE_SHOPTYPE_KEY)
        //存在返回
        //不存在查数据库
        //不存在报错
        //存在放入redis
        //不存在删除
        return null;
    }
}
