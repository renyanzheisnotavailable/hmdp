package com.hmdp.utils;

import com.hmdp.dto.Result;

/**
 * @author Chu
 * @create 2023-02-21-21:59
 */
public class utils {
    public Result isPhoneNumber(String phone){
        if( RegexUtils.isPhoneInvalid(phone) ) {
            return Result.fail("手机号码格式不正确");
        }
        return null;
    }
}
