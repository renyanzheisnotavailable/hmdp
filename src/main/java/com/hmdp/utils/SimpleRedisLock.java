package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Chu
 */

@Slf4j
@AllArgsConstructor
public class SimpleRedisLock implements ILock{
    private String name;

    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    private  StringRedisTemplate stringRedisTemplate;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, KEY_PREFIX + ID_PREFIX + Thread.currentThread().getId() + "", timeoutSec, TimeUnit.SECONDS);
        log.info("---lock函数中，{},{}", success, KEY_PREFIX + ID_PREFIX + Thread.currentThread().getId() + "");
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        Long execute = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                KEY_PREFIX + ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        String threadId = KEY_PREFIX + ID_PREFIX + Thread.currentThread().getId();
//        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        log.info("{}",threadId);
//        log.info("---{}",s);
//        if (s.equals(threadId)) {
//            Boolean delete = stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
