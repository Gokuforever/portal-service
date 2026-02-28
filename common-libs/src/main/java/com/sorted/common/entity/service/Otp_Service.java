package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Otp;
import com.sorted.common.repository.mongo.Otp_Repository;
import org.springframework.stereotype.Service;

@Service
public class Otp_Service extends GenericEntityServiceImpl<String, Otp, Otp_Repository> {

	@Override
	protected Class<Otp_Repository> getRepoClass() {
		return Otp_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Otp inE) throws RuntimeException {

	}

	@Override
	protected void validateBeforeUpdate(String id, Otp inE) throws RuntimeException {

	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {

	}

}
