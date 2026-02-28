package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Order_Item_Repository extends BaseMongoRepository<String, Order_Item> {

	@Override
	default Class<Order_Item> getEntityType() {
		return Order_Item.class;
	}
}
