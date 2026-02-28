package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Product_Master;
import com.sorted.common.helper.BaseMongoRepository;

public interface Product_Master_Repository extends BaseMongoRepository<String, Product_Master> {

	@Override
	default Class<Product_Master> getEntityType() {
		return Product_Master.class;
	}
}
