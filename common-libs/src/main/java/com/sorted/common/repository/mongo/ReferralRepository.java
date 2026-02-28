package com.sorted.common.repository.mongo;


import com.sorted.common.entity.mongo.ReferralEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface ReferralRepository extends BaseMongoRepository<String, ReferralEntity> {

    @Override
    default Class<ReferralEntity> getEntityType() {
        return ReferralEntity.class;
    }
}
