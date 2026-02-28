package com.sorted.common.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Log4j2
@Configuration
public class CorsConfig {

	@Value("${cors.allowed.origins}")
	private String allowedOrigins;

	@Bean
	public CorsFilter corsFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		List<String> domains = Arrays.asList(allowedOrigins.split(","));
		config.setAllowedOrigins(domains);

		config.addAllowedMethod("*"); // Allow all HTTP methods (GET, POST, etc.)
		config.addAllowedHeader("*"); // Allow all headers
		config.setAllowCredentials(true); // Allow credentials if needed (for cookies, etc.)

		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}
}
