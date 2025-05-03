package com.sorted.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.google.common.util.concurrent.RateLimiter;

@Configuration
public class RateLimiterConfig {

    @Value("${rate.limiter.requests.per.second:10}")
    private double requestsPerSecond;

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(requestsPerSecond);
    }
} 