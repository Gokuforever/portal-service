package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.RecommendersEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface RecommendersRepository extends BaseMongoRepository<String, RecommendersEntity> {

    @Override
    default Class<RecommendersEntity> getEntityType() {
        return RecommendersEntity.class;
    }
}
