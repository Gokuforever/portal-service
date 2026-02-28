package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.AssetsEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface AssetsRepository extends BaseMongoRepository<String, AssetsEntity> {

    @Override
    default Class<AssetsEntity> getEntityType() {
        return AssetsEntity.class;
    }
}
