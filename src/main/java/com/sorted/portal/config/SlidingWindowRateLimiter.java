package com.sorted.portal.config;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

@Component
public class SlidingWindowRateLimiter {
    
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowSizeInMillis;
    
    public SlidingWindowRateLimiter() {
        // Default: 5 requests per minute
        this.maxRequests = 5;
        this.windowSizeInMillis = TimeUnit.MINUTES.toMillis(1);
    }
    
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeInMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeInMillis = windowSizeInMillis;
    }
    
    public boolean tryAcquire(String key) {
        long currentTime = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> timestamps = requestTimestamps.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        
        // Remove timestamps outside the window
        long windowStart = currentTime - windowSizeInMillis;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }
        
        // Check if we can accept the request
        if (timestamps.size() < maxRequests) {
            timestamps.addLast(currentTime);
            return true;
        }
        
        return false;
    }
    
    public boolean tryAcquire() {
        // Use a global key for application-wide rate limiting
        return tryAcquire("global");
    }
}
