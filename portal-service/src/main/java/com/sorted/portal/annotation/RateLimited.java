package com.sorted.portal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    // Note: The value here is not used as we configure rate limiting globally
    // The actual rate limit is configured in application.properties (rate.limiter.requests.per.minute)
    double value() default 5.0; // Default rate limit configured in application.properties
} 