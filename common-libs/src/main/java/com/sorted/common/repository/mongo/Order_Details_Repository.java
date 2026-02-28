package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Order_Details_Repository extends BaseMongoRepository<String, Order_Details>{

	@Override
	default Class<Order_Details> getEntityType() {
		return Order_Details.class;
	}
}
