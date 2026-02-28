package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Otp;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Otp_Repository extends BaseMongoRepository<String, Otp> {

	@Override
	default Class<Otp> getEntityType() {
		return Otp.class;
	}
}
