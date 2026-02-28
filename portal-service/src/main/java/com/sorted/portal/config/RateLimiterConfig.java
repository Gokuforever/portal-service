package com.sorted.portal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimiterConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterConfig.class);

    @Value("${rate.limiter.requests.per.minute:10}")
    private int requestsPerMinute;

    @Bean
    public SlidingWindowRateLimiter slidingWindowRateLimiter() {
        long windowSizeInMillis = TimeUnit.MINUTES.toMillis(1);
        logger.info("Initializing SlidingWindowRateLimiter with {} requests per minute", requestsPerMinute);
        return new SlidingWindowRateLimiter(requestsPerMinute, windowSizeInMillis);
    }
}
