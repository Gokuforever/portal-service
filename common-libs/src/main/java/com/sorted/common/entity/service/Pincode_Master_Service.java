package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Pincode_Master;
import com.sorted.common.repository.mongo.Pincode_Master_Repository;
import org.springframework.stereotype.Service;

@Service
public class Pincode_Master_Service
		extends GenericEntityServiceImpl<String, Pincode_Master, Pincode_Master_Repository> {

	@Override
	protected Class<Pincode_Master_Repository> getRepoClass() {
		return Pincode_Master_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Pincode_Master inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeUpdate(String id, Pincode_Master inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
	}

}
