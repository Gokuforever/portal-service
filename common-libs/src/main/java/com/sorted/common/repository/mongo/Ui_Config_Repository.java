package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Ui_Config;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Ui_Config_Repository extends BaseMongoRepository<String, Ui_Config> {

	@Override
	default Class<Ui_Config> getEntityType() {
		return Ui_Config.class;
	}
}
