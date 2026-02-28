package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Pincode_Master;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Pincode_Master_Repository extends BaseMongoRepository<String, Pincode_Master> {

	@Override
	default Class<Pincode_Master> getEntityType() {
		return Pincode_Master.class;
	}
}
