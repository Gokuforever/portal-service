package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.SmsPool;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsPool_Repository extends BaseMongoRepository<String, SmsPool> {

	@Override
	default Class<SmsPool> getEntityType() {
		return SmsPool.class;
	}

}
