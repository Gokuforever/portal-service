package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.Varient_Mapping;
import com.sorted.common.repository.mongo.Varient_Mapping_Repository;
import com.sorted.common.utils.CommonUtils;
import org.springframework.stereotype.Service;

@Service
public class Varient_Mapping_Service
		extends GenericEntityServiceImpl<String, Varient_Mapping, Varient_Mapping_Repository> {

	@Override
	protected Class<Varient_Mapping_Repository> getRepoClass() {
		return Varient_Mapping_Repository.class;
	}

	@Override
	protected void validateBeforeCreate(Varient_Mapping inE) throws RuntimeException {
		String code = CommonUtils.createCode("VAR");
		inE.setCode(code);
	}

	@Override
	protected void validateBeforeUpdate(String id, Varient_Mapping inE) throws RuntimeException {

	}

	@Override
	protected void validateBeforeDelete(String id) throws RuntimeException {

	}

}
