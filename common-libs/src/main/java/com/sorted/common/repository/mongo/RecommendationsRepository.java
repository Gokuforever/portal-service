package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.RecommendationsEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface RecommendationsRepository extends BaseMongoRepository<String, RecommendationsEntity> {

    @Override
    default Class<RecommendationsEntity> getEntityType() {
        return RecommendationsEntity.class;
    }
}
