package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.HomeConfig;
import com.sorted.common.helper.BaseMongoRepository;

public interface HomeConfigRepository extends BaseMongoRepository<String, HomeConfig> {
    @Override
    default Class<HomeConfig> getEntityType() {
        return HomeConfig.class;
    }
}
