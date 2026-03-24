package com.sollite.global.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.sollite.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = Map.ofEntries(
                // 국내주식
                Map.entry("market:price",        base.entryTtl(Duration.ofSeconds(5))),
                Map.entry("market:orderbook",    base.entryTtl(Duration.ofSeconds(5))),
                Map.entry("market:daily",        base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("market:finance",      base.entryTtl(Duration.ofHours(1))),
                Map.entry("market:opinion",      base.entryTtl(Duration.ofHours(1))),
                Map.entry("market:investor",     base.entryTtl(Duration.ofHours(1))),
                Map.entry("market:indices",      base.entryTtl(Duration.ofSeconds(3))),
                // 국내주식 순위
                Map.entry("market:ranking",          base.entryTtl(Duration.ofSeconds(30))),
                // 해외주식
                Map.entry("foreign:price",          base.entryTtl(Duration.ofSeconds(5))),
                Map.entry("foreign:orderbook",      base.entryTtl(Duration.ofSeconds(5))),
                Map.entry("foreign:chart",          base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("foreign:tick-chart",     base.entryTtl(Duration.ofSeconds(30))),
                Map.entry("foreign:minute-chart",   base.entryTtl(Duration.ofSeconds(30))),
                Map.entry("foreign:advanced-chart", base.entryTtl(Duration.ofMinutes(5))),
                Map.entry("foreign:info",           base.entryTtl(Duration.ofHours(24)))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(1)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
