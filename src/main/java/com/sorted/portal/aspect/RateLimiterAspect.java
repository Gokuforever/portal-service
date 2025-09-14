package com.sorted.portal.aspect;

import com.sorted.portal.annotation.RateLimited;
import com.sorted.portal.config.SlidingWindowRateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class RateLimiterAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterAspect.class);
    private final SlidingWindowRateLimiter rateLimiter;

    @Autowired
    public RateLimiterAspect(SlidingWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        if (rateLimiter.tryAcquire()) {
            logger.debug("Rate limit check passed for method: {}", methodName);
            return joinPoint.proceed();
        } else {
            logger.warn("Rate limit exceeded for method: {}", methodName);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests, please try again later."
            );
        }
    }
}
