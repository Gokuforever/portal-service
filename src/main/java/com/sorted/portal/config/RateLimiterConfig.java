package com.sorted.portal.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Value("${rate.limiter.requests.per.second:10}")
    private double requestsPerSecond;

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(requestsPerSecond);
    }
} 