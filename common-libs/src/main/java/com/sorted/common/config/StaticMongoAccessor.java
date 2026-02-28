package com.sorted.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class StaticMongoAccessor {

	public static MongoTemplate MONGO_TEMPLATE;

	@Autowired
	private MongoTemplate mongoTemplate;

	@PostConstruct
	public void init() {
		StaticMongoAccessor.MONGO_TEMPLATE = this.mongoTemplate;
	}
}
