package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.User_Auth_Details;
import com.sorted.common.repository.mongo.User_Auth_Details_Repository;
import org.springframework.stereotype.Service;

@Service
public class User_Auth_Details_Service
		extends GenericEntityServiceImpl<String, User_Auth_Details, User_Auth_Details_Repository> {

	@Override
	protected Class<User_Auth_Details_Repository> getRepoClass() {
		return User_Auth_Details_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(User_Auth_Details inE) throws RuntimeException {

	}

	@Override
	protected void validateBeforeUpdate(String id, User_Auth_Details inE) throws RuntimeException {

	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {

	}

}
