package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Search_History;
import com.sorted.common.repository.mongo.Search_History_Repository;
import org.springframework.stereotype.Service;

@Service
public class Search_History_Service
		extends GenericEntityServiceImpl<String, Search_History, Search_History_Repository> {

	@Override
	protected Class<Search_History_Repository> getRepoClass() {
		return Search_History_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Search_History inE) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void validateBeforeUpdate(String id, Search_History inE) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
		// TODO Auto-generated method stub
		
	}

}
