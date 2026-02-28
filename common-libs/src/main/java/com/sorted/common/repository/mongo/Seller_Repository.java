package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Seller;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Seller_Repository extends BaseMongoRepository<String, Seller> {

	@Override
	default Class<Seller> getEntityType() {
		return Seller.class;
	}
}
