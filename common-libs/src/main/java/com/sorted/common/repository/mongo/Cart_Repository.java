package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Cart;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Cart_Repository extends BaseMongoRepository<String, Cart> {

	@Override
	default Class<Cart> getEntityType() {
		return Cart.class;
	}
}
