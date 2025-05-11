package com.sorted.portal.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class PortalServiceInterceptorAppConfig implements WebMvcConfigurer {
    @Autowired
    PortalServiceInterceptor portalServiceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(portalServiceInterceptor).excludePathPatterns("/**");
    }
}
