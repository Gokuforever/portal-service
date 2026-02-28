package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Plans;
import com.sorted.common.helper.BaseMongoRepository;

public interface Plan_Repository extends BaseMongoRepository<String, Plans> {

	@Override
	default Class<Plans> getEntityType() {
		return Plans.class;
	}
}
