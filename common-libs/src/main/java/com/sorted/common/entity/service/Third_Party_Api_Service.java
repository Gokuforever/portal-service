package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Third_Party_Api;
import com.sorted.common.repository.mongo.Third_Party_Api_Repository;
import org.springframework.stereotype.Service;

@Service
public class Third_Party_Api_Service
		extends GenericEntityServiceImpl<String, Third_Party_Api, Third_Party_Api_Repository> {

	@Override
	protected Class<Third_Party_Api_Repository> getRepoClass() {
		return Third_Party_Api_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Third_Party_Api inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeUpdate(String id, Third_Party_Api inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
	}

}
