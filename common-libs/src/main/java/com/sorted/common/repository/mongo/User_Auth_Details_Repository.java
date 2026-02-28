package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.User_Auth_Details;
import com.sorted.common.helper.BaseMongoRepository;

public interface User_Auth_Details_Repository extends BaseMongoRepository<String, User_Auth_Details> {

	@Override
	default Class<User_Auth_Details> getEntityType() {
		return User_Auth_Details.class;
	}
}
