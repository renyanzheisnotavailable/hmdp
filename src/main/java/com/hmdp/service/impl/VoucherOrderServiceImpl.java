package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.Collections;

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
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService iSeckillVoucherService;

    private final RedisWorker redisWorker;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

//    /**
//     * 抢购秒杀券
//     * @param vocherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long vocherId) {
//        //找到该秒杀券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(vocherId);
//        //查看是否在抢购时间
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("不在规定时间");
//        }
//        //库存是否大于零
//        if (voucher.getStock() < 1) {
//            return Result.fail("无库存");
//        }
//        //一人一单
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order" + userId);
////        boolean b = simpleRedisLock.tryLock(1200);
//        boolean b = lock.tryLock();
//        if (!b) {
//            return Result.fail("获取失败");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(vocherId);
//        } finally {
////            simpleRedisLock.unlock();
//            lock.unlock();
//        }
//    }
    /**
     * 抢购秒杀券
     * @param vocherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long vocherId) {
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                vocherId.toString(), UserHolder.getUser().getId(), redisWorker.nextId("order")
        );
        int r = result.intValue();
        if(r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //todo 保存阻塞队列
        //返回订单id
        return Result.ok(redisWorker.nextId("order"));
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long vocherId) {
        log.info("===={}",query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", vocherId).count());
        if (query().eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", vocherId).count() > 0) {
            return Result.fail("用户已经下订单");
        }
        //修改库存
        boolean isUpdate = iSeckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", vocherId).gt("stock", 0).update();
        //生成订单
        if (!isUpdate) {
            return Result.fail("no stock");
        }
        //创建订单
        long id = redisWorker.nextId("order");
        Long user = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(id);
        voucherOrder.setUserId(user);
        voucherOrder.setVoucherId(vocherId);
        save(voucherOrder);
        return Result.ok(id);
    }
}
