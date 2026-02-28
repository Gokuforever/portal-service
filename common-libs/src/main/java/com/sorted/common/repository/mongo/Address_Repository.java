package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Address;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Address_Repository extends BaseMongoRepository<String, Address> {

	@Override
	default Class<Address> getEntityType() {
		return Address.class;
	}
}
