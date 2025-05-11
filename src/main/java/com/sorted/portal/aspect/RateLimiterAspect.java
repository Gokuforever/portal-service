package com.sorted.portal.aspect;

import com.google.common.util.concurrent.RateLimiter;
import com.sorted.portal.annotation.RateLimited;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
public class RateLimiterAspect {

    private final RateLimiter rateLimiter;

    @Autowired
    public RateLimiterAspect(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimited)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        if (rateLimiter.tryAcquire()) {
            return joinPoint.proceed();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again later."
            );
        }
    }
} 