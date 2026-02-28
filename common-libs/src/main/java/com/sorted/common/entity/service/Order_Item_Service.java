package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.repository.mongo.Order_Item_Repository;
import org.springframework.stereotype.Service;

@Service
public class Order_Item_Service extends GenericEntityServiceImpl<String, Order_Item, Order_Item_Repository> {

	@Override
	protected Class<Order_Item_Repository> getRepoClass() {
		return Order_Item_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Order_Item inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeUpdate(String id, Order_Item inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
	}

	@Override
	public Order_Item update(String id, Order_Item entity, String cudby) {
		return super.update(id, entity, cudby);
	}
}
