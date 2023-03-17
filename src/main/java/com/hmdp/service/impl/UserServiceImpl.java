package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //是否是有效手机格式
        if( RegexUtils.isPhoneInvalid(phone) ) {
            return Result.fail("手机号码格式不正确");
        }
       //生成验证码
        String s = RandomUtil.randomString(6);
        //redis保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, s);
        stringRedisTemplate.expire(LOGIN_CODE_KEY + phone, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("生成验证码成功："+s);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //检测手机号格式是否正确
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号码格式不正确");
        }
        //检测code是否一致
        if(loginForm.getCode() == null || loginForm.getCode().equals(stringRedisTemplate.opsForValue()
                .get(loginForm.getPhone()))) {
            return Result.fail("验证码输入错误");
        }
        //数据库是否存在
        User user = query().eq("phone", loginForm.getPhone()).one();
        log.info("-----------{}",user);
        //存在登录 否则注册
        if(user == null){
            user = new User();
            user.setPhone(loginForm.getPhone())
                    .setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
            save(user);
        }
        //todo jwt
        //生成随机token 作为登陆令牌
        String token = LOGIN_USER_KEY + UUID.randomUUID().toString(true);
        //保存redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        log.info(userMap.toString());
        stringRedisTemplate.opsForHash().putAll(token, userMap);
        stringRedisTemplate.expire(token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }
}
