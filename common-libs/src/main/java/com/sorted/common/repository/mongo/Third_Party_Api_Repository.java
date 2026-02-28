package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Third_Party_Api;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Third_Party_Api_Repository extends BaseMongoRepository<String, Third_Party_Api>{

	@Override
	default Class<Third_Party_Api> getEntityType() {
		return Third_Party_Api.class;
	}
}
