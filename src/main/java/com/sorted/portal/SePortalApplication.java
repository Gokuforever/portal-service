package com.sorted.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.sorted")
public class SePortalApplication {

	public static void main(String[] args) {
		SpringApplication.run(SePortalApplication.class, args);
	}

}
