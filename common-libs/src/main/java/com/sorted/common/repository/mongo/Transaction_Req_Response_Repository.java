package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Transaction_Req_Response;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Transaction_Req_Response_Repository extends BaseMongoRepository<String, Transaction_Req_Response> {

	@Override
	default Class<Transaction_Req_Response> getEntityType() {
		return Transaction_Req_Response.class;
	}
}
