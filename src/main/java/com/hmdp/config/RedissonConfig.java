package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Chu
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissionClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.221.128:6379").setPassword("102502");
        return Redisson.create(config);
    }
}
