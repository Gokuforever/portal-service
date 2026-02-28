package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Plans;
import com.sorted.common.repository.mongo.Plan_Repository;
import org.springframework.stereotype.Service;

@Service
public class Plans_Service extends GenericEntityServiceImpl<String, Plans, Plan_Repository> {

	@Override
	protected Class<Plan_Repository> getRepoClass() {
		return Plan_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Plans inE) throws RuntimeException {
		// TODO Auto-generated method stub

	}

	@Override
	protected void validateBeforeUpdate(String id, Plans inE) throws RuntimeException {
		// TODO Auto-generated method stub

	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
		// TODO Auto-generated method stub

	}

}
