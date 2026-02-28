package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Product_Master;
import com.sorted.common.repository.mongo.Product_Master_Repository;
import org.springframework.stereotype.Service;

@Service
public class Product_Master_Service extends GenericEntityServiceImpl<String, Product_Master, Product_Master_Repository>{

	@Override
	protected Class<Product_Master_Repository> getRepoClass() {
		return Product_Master_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Product_Master inE) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void validateBeforeUpdate(String id, Product_Master inE) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

}
