package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.ThirdPartyAPITraceEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface ThirdPartyAPITraceRepository extends BaseMongoRepository<String, ThirdPartyAPITraceEntity> {

    @Override
    default Class<ThirdPartyAPITraceEntity> getEntityType() {
        return ThirdPartyAPITraceEntity.class;
    }
}
