package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.File_Upload_Details;
import com.sorted.common.helper.BaseMongoRepository;

public interface File_Upload_Details_Repository extends BaseMongoRepository<String, File_Upload_Details> {

	@Override
	default Class<File_Upload_Details> getEntityType() {
		return File_Upload_Details.class;
	}
}
