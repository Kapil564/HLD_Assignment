package com.kapil.typeahead.config;

import com.kapil.typeahead.cache.RedisNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory1() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory2() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6380));
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory3() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6381));
    }

    @Bean
    @Primary
    public StringRedisTemplate redisTemplate1(LettuceConnectionFactory redisConnectionFactory1) {
        return new StringRedisTemplate(redisConnectionFactory1);
    }

    @Bean
    public StringRedisTemplate redisTemplate2(LettuceConnectionFactory redisConnectionFactory2) {
        return new StringRedisTemplate(redisConnectionFactory2);
    }

    @Bean
    public StringRedisTemplate redisTemplate3(LettuceConnectionFactory redisConnectionFactory3) {
        return new StringRedisTemplate(redisConnectionFactory3);
    }

    @Bean
    public Map<RedisNode, StringRedisTemplate> redisTemplatesMap(
            StringRedisTemplate redisTemplate1,
            StringRedisTemplate redisTemplate2,
            StringRedisTemplate redisTemplate3
    ) {
        Map<RedisNode, StringRedisTemplate> map = new HashMap<>();
        map.put(new RedisNode("redis1", "localhost", 6379), redisTemplate1);
        map.put(new RedisNode("redis2", "localhost", 6380), redisTemplate2);
        map.put(new RedisNode("redis3", "localhost", 6381), redisTemplate3);
        return map;
    }
}