package com.sorted.portal.config.logging;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MDCFilterConfig {

    @Bean
    public MDCFilter mdcFilter() {
        return new MDCFilter();
    }

    @Bean
    public FilterRegistrationBean<MDCFilter> mdcFilterRegistration(MDCFilter mdcFilter) {
        FilterRegistrationBean<MDCFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(mdcFilter);
        registration.addUrlPatterns("/*");
        registration.setName("mdcFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
