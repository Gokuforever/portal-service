package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Category_Master;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Category_MasterRepository extends BaseMongoRepository<String, Category_Master> {

	@Override
	default Class<Category_Master> getEntityType() {
		return Category_Master.class;
	}

}
