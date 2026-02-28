package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.File_Upload_Details;
import com.sorted.common.repository.mongo.File_Upload_Details_Repository;
import org.springframework.stereotype.Service;

@Service
public class File_Upload_Details_Service
		extends GenericEntityServiceImpl<String, File_Upload_Details, File_Upload_Details_Repository> {

	@Override
	protected Class<File_Upload_Details_Repository> getRepoClass() {
		return File_Upload_Details_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(File_Upload_Details inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeUpdate(String id, File_Upload_Details inE) throws RuntimeException {
	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {
	}

}
