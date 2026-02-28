package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Users;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Users_Repository extends BaseMongoRepository<String, Users> {

	@Override
	default Class<Users> getEntityType() {
		return Users.class;
	}
}
