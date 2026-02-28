package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Search_History;
import com.sorted.common.helper.BaseMongoRepository;

public interface Search_History_Repository extends BaseMongoRepository<String, Search_History> {

	@Override
	default Class<Search_History> getEntityType() {
		return Search_History.class;
	}
}
