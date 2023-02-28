package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author Chu
 * @create 2023-02-22-10:50
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        System.out.println(token);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> user = redisTemplate.opsForHash().entries(token);
        System.out.println(user.toString());
        if(user.isEmpty()){
            return true;
        }
        UserHolder.saveUser(BeanUtil.fillBeanWithMap(user, new UserDTO(), false));
        redisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
