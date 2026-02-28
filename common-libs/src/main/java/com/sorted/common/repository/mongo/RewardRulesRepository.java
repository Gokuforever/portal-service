package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.RewardRulesEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface RewardRulesRepository extends BaseMongoRepository<String, RewardRulesEntity> {

    @Override
    default Class<RewardRulesEntity> getEntityType() {
        return RewardRulesEntity.class;
    }
}
